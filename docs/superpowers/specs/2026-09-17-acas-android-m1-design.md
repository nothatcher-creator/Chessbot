# A.C.A.S Android Companion — Milestone 1 Detection Design

## Goal
Build a separate Android companion application that can determine the current standard-chess position while the user is viewing Lichess in Chrome or the official Lichess Android app, and show diagnostics only: FEN, orientation, side to move, confidence, and which input source produced the position.

## Scope
Milestone 1 contains no chess-engine recommendations, no arrows, no automated input, and no move automation. It is deliberately detection-only so board acquisition can be validated independently.

Target packages:
- Chrome: `com.android.chrome`
- Current official Lichess Android app: `org.lichess.mobileV2`
- Legacy Lichess package is accepted opportunistically: `org.lichess.mobileapp`

The project is GPL-3.0 and identifies A.C.A.S. as the architectural upstream/reference. Future milestones may reuse more GPL-3.0 A.C.A.S. components.

## Architecture
The detector uses two inputs and one authoritative position tracker.

1. **Accessibility input** listens only while a target package is foreground. It extracts visible move-history text and accessibility descriptions. When SAN history is available, the tracker rebuilds the game from the standard starting position by matching each SAN token against legal moves. This path can recover a game even when the app starts midgame.
2. **MediaProjection input** captures screen frames after normal Android user consent. It locates an 8×8 board geometrically. It does not classify every piece image. Instead, once a standard starting layout is detected, it compares per-square edge features between frames, maps changed screen squares through both orientation hypotheses, and chooses the legal chess move that best explains those visual changes. This makes piece identity a consequence of chess rules rather than an image classifier.
3. **Position coordinator** accepts the higher-confidence source, keeps both sources observable, and emits a `DetectionState` with FEN, orientation, source, confidence, and status.

## Screen Detection
The board locator searches for large square regions whose 64 cell-center samples form two alternating color clusters. It scores candidates on checker alternation, cell-size consistency, and minimum contrast rather than requiring specific Lichess colors.

A standard-start bootstrap does not require piece classification. It looks for strong structural energy in screen rows 1, 2, 7, and 8 with substantially less energy in the four middle ranks. The first stable bootstrap creates the standard initial position with orientation unresolved.

For subsequent frames, each square is represented by a small mean-normalized edge feature. The tracker calculates a change score per square. Legal moves are generated from the current position. For each orientation hypothesis, a legal move predicts the squares whose visual content should change. Ordinary moves and captures touch source/destination, castling touches four squares, and en-passant touches three. The best uniquely separated legal explanation advances the position and resolves orientation.

## Accessibility Detection
The service walks the accessibility node tree with a strict node/time budget and collects text/content descriptions. `MoveHistoryExtractor` recognizes common SAN tokens, move numbers, castling, captures, checks, mates, and promotions while ignoring timers, usernames, ratings, coordinates, and unrelated UI text.

`SanReconstructor` replays the token sequence from the normal initial position. It generates legal moves and SAN for each move, then selects the exact normalized SAN match. If a token cannot be resolved, it stops at the last unambiguous position rather than guessing.

## Android UX
The main screen has four controls/status areas:
- Accessibility permission/status with a button opening Accessibility Settings.
- Screen-capture permission/status with Start/Stop capture controls.
- Target-app status showing the current foreground package.
- Detection diagnostics showing source, board found/not found, confidence, orientation, side to move, and full FEN.

No floating move overlay is needed in M1. A small optional non-interactive diagnostic overlay may be added only if it does not enter captured frames; the initial implementation keeps diagnostics in the companion activity to simplify testing.

## Privacy
All analysis is on-device. Screen frames are converted to lightweight board features and discarded immediately. No screenshot files are saved. Accessibility text is held only in memory long enough to reconstruct the position. No network permission is required by the milestone app.

## Failure Handling
- Accessibility unavailable: screen path remains usable.
- MediaProjection permission denied: accessibility path remains usable.
- Board not found: report `Waiting for board`; never invent a FEN.
- Starting in an arbitrary midgame with no accessible move history: report `Need start position or move history`; do not run the old piece classifier.
- Ambiguous visual transition: keep the last confirmed FEN and wait for another frame.
- Orientation unresolved: show `Unknown` until a legal visual transition disambiguates it.

## Testing
Pure Kotlin unit/smoke tests cover FEN, move generation, SAN replay, SAN extraction, orientation mapping, legal-move visual matching, castling/en-passant square-change expectations, start-layout bootstrap, and synthetic board-location scoring.

Android unit tests cover coordinator priority and target-package filtering. GitHub Actions builds the APK and verifies manifest declarations for Accessibility and MediaProjection foreground service.

## Success Criteria
1. On a normal Lichess game opened from move 1 in Chrome or Lichess mobile, the app reaches the correct initial FEN without classifying piece artwork.
2. After ordinary moves, captures, castling, and en-passant, legal visual tracking can advance the FEN from screen-square changes.
3. If the visible accessibility tree contains SAN history, the same position can be reconstructed independently from that history.
4. Diagnostics never silently replace an uncertain position with a guessed one.
5. The APK compiles and installs without Internet permission.

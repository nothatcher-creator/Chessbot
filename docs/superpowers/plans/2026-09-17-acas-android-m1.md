# A.C.A.S Android Companion M1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a detection-only Android companion that reconstructs Lichess positions from Accessibility SAN history or MediaProjection-based legal-move screen tracking.

**Architecture:** A pure Kotlin chess/tracking core is shared by two Android inputs. Accessibility tries SAN replay first; MediaProjection locates the board and tracks per-square visual changes from a standard-start bootstrap. A coordinator publishes diagnostics and never guesses when ambiguous.

**Tech Stack:** Kotlin/JVM, Android SDK 36, minSdk 29, Android AccessibilityService, MediaProjection/ImageReader, JUnit 4, Gradle/AGP.

**Spec:** `docs/superpowers/specs/2026-09-17-acas-android-m1-design.md`

## Global Constraints
- Detection-only milestone: no engine recommendation, move arrows, or automated input.
- Target `com.android.chrome`, `org.lichess.mobileV2`, and legacy `org.lichess.mobileapp`.
- No `INTERNET` permission.
- MediaProjection requires explicit Android consent.
- Accessibility traversal is bounded and target-package scoped.
- Never emit a new confirmed FEN from an ambiguous transition.
- GPL-3.0 project with A.C.A.S attribution.

---

### Task 1: Pure chess domain
**Files:** Create `acas-android/app/src/main/java/com/nothatcher/acas/chess/{Piece,Square,Move,Position,Fen,MoveGenerator,San}.kt`; tests under `acas-android/app/src/test/java/com/nothatcher/acas/chess/ChessCoreTest.kt`.
**Interfaces:** Produces `Position.initial()`, `Fen.encode(Position)`, `MoveGenerator.legalMoves(Position)`, `San.format(Position, Move)`, `Position.play(Move)`.
- [ ] Write tests for initial FEN, 20 opening moves, e2e4, castling, en-passant, promotion, and SAN.
- [ ] Run pure tests and verify failures before implementation.
- [ ] Implement immutable chess state and legal move generation including king-safety filtering.
- [ ] Run tests to green.
- [ ] Commit.

### Task 2: Accessibility move-history reconstruction
**Files:** Create `detection/MoveHistoryExtractor.kt`, `detection/SanReconstructor.kt`; tests `MoveHistoryExtractorTest.kt`, `SanReconstructorTest.kt`.
**Interfaces:** `MoveHistoryExtractor.extract(texts: List<String>): List<String>`; `SanReconstructor.reconstruct(tokens: List<String>): ReconstructionResult`.
- [ ] Add failing tests for numbered SAN text, castling, checks, promotion, UI noise, and a known opening sequence.
- [ ] Run tests red.
- [ ] Implement bounded SAN token extraction and legal-SAN replay.
- [ ] Run tests green.
- [ ] Commit.

### Task 3: Board geometry and visual features
**Files:** Create `vision/PixelFrame.kt`, `vision/BoardRect.kt`, `vision/BoardLocator.kt`, `vision/SquareFeatures.kt`; tests `BoardLocatorTest.kt`, `SquareFeaturesTest.kt`.
**Interfaces:** `BoardLocator.locate(PixelFrame): BoardDetection?`; `SquareFeatures.extract(PixelFrame, BoardRect): Array<SquareFeature>`.
- [ ] Generate synthetic two-tone/wood-like boards in tests, including highlights and UI margins.
- [ ] Verify locator tests fail.
- [ ] Implement checker-cluster candidate scoring and mean-normalized gradient features.
- [ ] Run tests green.
- [ ] Commit.

### Task 4: Standard-start bootstrap and legal transition matcher
**Files:** Create `tracking/BoardOrientation.kt`, `tracking/StartLayoutDetector.kt`, `tracking/VisualMoveMatcher.kt`, `tracking/VisualPositionTracker.kt`; tests under `tracking/`.
**Interfaces:** `StartLayoutDetector.score(features): Float`; `VisualMoveMatcher.match(position, changeScores, orientation): VisualMatch?`; `VisualPositionTracker.accept(features): VisualTrackingResult`.
- [ ] Add failing tests for standard-start structure, e2e4 orientation resolution, capture, castling, en-passant, and ambiguous noise.
- [ ] Run tests red.
- [ ] Implement expected-changed-square sets and unique-margin move scoring.
- [ ] Run tests green.
- [ ] Commit.

### Task 5: Android Accessibility source
**Files:** Create `service/AcasAccessibilityService.kt`, `detection/TargetPackages.kt`, `res/xml/accessibility_service_config.xml`; tests `TargetPackagesTest.kt`.
**Interfaces:** Service publishes target-package text observations to `DetectionBus`.
- [ ] Add target-package test and traversal budget constants.
- [ ] Implement service restricted to target packages, collecting text/content descriptions with node and depth limits.
- [ ] Register service in manifest.
- [ ] Run Android unit tests.
- [ ] Commit.

### Task 6: Android MediaProjection source
**Files:** Create `capture/CaptureService.kt`, `capture/BitmapFrameAdapter.kt`, `capture/RgbaBufferConverter.kt`.
**Interfaces:** Capture service publishes screen-derived visual tracking state at <=2 FPS and immediately closes Images.
- [ ] Add unit tests for RGBA row-padding conversion helper.
- [ ] Implement foreground MediaProjection service and ImageReader pipeline.
- [ ] Add service type/notification and no-persistence guarantee.
- [ ] Run Android unit tests.
- [ ] Commit.

### Task 7: Detection coordinator and diagnostics UI
**Files:** Create `DetectionBus.kt`, `DetectionCoordinator.kt`, `DetectionState.kt`, `MainActivity.kt`; tests `DetectionCoordinatorTest.kt`.
**Interfaces:** Coordinator gives fresh complete SAN reconstruction priority, otherwise visual tracking.
- [ ] Test source priority, ambiguity retention, and status strings.
- [ ] Implement coordinator.
- [ ] Implement programmatic Android UI for permissions, capture control, source/confidence/orientation/FEN diagnostics.
- [ ] Run Android unit tests.
- [ ] Commit.

### Task 8: Build/CI/license packaging
**Files:** Create `acas-android/{settings.gradle.kts,build.gradle.kts,gradle.properties}`, `acas-android/app/build.gradle.kts`, `acas-android/LICENSE`, `acas-android/NOTICE.md`, `.github/workflows/build-acas-android.yml`.
**Interfaces:** GitHub artifact `ACAS-Android-M1-debug` containing `app-debug.apk`.
- [ ] Configure SDK 36/minSdk 29/Java 17 and JUnit.
- [ ] Add GPL-3.0 license and A.C.A.S attribution/reference.
- [ ] CI runs `testDebugUnitTest`, `assembleDebug`, `unzip -t`, and APK presence checks.
- [ ] Build in CI and fix only evidence-backed failures.
- [ ] Download and independently hash/inspect final APK before completion claim.

package com.nothatcher.acasbridge;

public final class DetectorState {
    public record Snapshot(
            String target,
            String source,
            boolean boardDetected,
            String fen,
            String sideToMove,
            String orientation,
            String boardRect,
            int accessibilityNodes,
            int movesObserved,
            String message,
            long updatedAtMs) {}

    private static volatile Snapshot snapshot = new Snapshot(
            "Waiting for Chrome or Lichess",
            "None",
            false,
            null,
            "Unknown",
            "Unknown",
            null,
            0,
            0,
            "Enable Accessibility, then open Lichess. Screen capture is optional.",
            System.currentTimeMillis());

    private DetectorState() {}

    public static Snapshot get() { return snapshot; }

    public static synchronized void publish(Snapshot next) {
        snapshot = next;
    }

    public static synchronized void mergeScreen(
            boolean boardDetected,
            String orientation,
            String boardRect,
            boolean looksLikeStart,
            String message) {
        Snapshot old = snapshot;
        String fen = old.fen();
        String side = old.sideToMove();
        String source = old.source();
        if (boardDetected) {
            source = source.equals("Accessibility") ? "Accessibility + screen" : "Screen capture";
        }
        if (looksLikeStart && (fen == null || old.movesObserved() == 0)) {
            BoardStateResolver.Result start = BoardStateResolver.standardStart();
            fen = start.fen();
            side = start.sideToMove();
        }
        snapshot = new Snapshot(
                old.target(),
                source,
                boardDetected || old.boardDetected(),
                fen,
                side,
                orientation == null ? old.orientation() : orientation,
                boardRect == null ? old.boardRect() : boardRect,
                old.accessibilityNodes(),
                old.movesObserved(),
                message,
                System.currentTimeMillis());
    }
}

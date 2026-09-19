package com.nothatcher.acasbridge;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public final class AcasAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        if (!TargetAppClassifier.isTarget(packageName)) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            DetectorState.publish(new DetectorState.Snapshot(
                    TargetAppClassifier.classify(packageName),
                    "Accessibility",
                    false,
                    null,
                    "Unknown",
                    "Unknown",
                    null,
                    0,
                    0,
                    "Target app detected; waiting for accessible board or move history.",
                    System.currentTimeMillis()));
            return;
        }

        try {
            AccessibilitySnapshotReader.Snapshot snap = AccessibilitySnapshotReader.read(root);
            List<String> moves = SanTokenExtractor.extract(snap.texts());
            BoardStateResolver.Result result = BoardStateResolver.fromSan(moves);

            String fen = moves.isEmpty() ? null : result.fen();
            boolean confirmed = fen != null && result.ok();
            String message;
            if (moves.isEmpty()) {
                message = "Lichess detected. No SAN move history is exposed through Accessibility yet.";
            } else if (result.ok()) {
                message = "Reconstructed " + result.appliedMoves() + " visible moves from Accessibility.";
            } else {
                message = result.error();
            }

            DetectorState.publish(new DetectorState.Snapshot(
                    TargetAppClassifier.classify(packageName),
                    "Accessibility",
                    confirmed,
                    fen,
                    result.sideToMove(),
                    "Unknown",
                    null,
                    snap.nodeCount(),
                    result.appliedMoves(),
                    message,
                    System.currentTimeMillis()));
        } finally {
            root.recycle();
        }
    }

    @Override
    public void onInterrupt() {
        DetectorState.Snapshot old = DetectorState.get();
        DetectorState.publish(new DetectorState.Snapshot(
                old.target(), old.source(), old.boardDetected(), old.fen(), old.sideToMove(),
                old.orientation(), old.boardRect(), old.accessibilityNodes(), old.movesObserved(),
                "Accessibility service interrupted.", System.currentTimeMillis()));
    }
}

package com.nothatcher.acasbridge;

import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public final class AccessibilitySnapshotReader {
    public record Snapshot(List<String> texts, int nodeCount) {}

    private static final int MAX_NODES = 1200;
    private static final int MAX_DEPTH = 40;

    private AccessibilitySnapshotReader() {}

    public static Snapshot read(AccessibilityNodeInfo root) {
        List<String> texts = new ArrayList<>();
        int[] count = {0};
        walk(root, texts, count, 0);
        return new Snapshot(List.copyOf(texts), count[0]);
    }

    private static void walk(AccessibilityNodeInfo node, List<String> texts, int[] count, int depth) {
        if (node == null || count[0] >= MAX_NODES || depth > MAX_DEPTH) return;
        count[0]++;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        if (text != null && !text.toString().isBlank()) texts.add(text.toString());
        if (desc != null && !desc.toString().isBlank()) texts.add(desc.toString());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                try {
                    walk(child, texts, count, depth + 1);
                } finally {
                    child.recycle();
                }
            }
        }
    }
}

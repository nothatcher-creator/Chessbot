package com.nothatcher.acasbridge;

import java.util.Set;

public final class TargetAppClassifier {
    public static final String CHROME = "com.android.chrome";
    public static final String LICHESS_V2 = "org.lichess.mobileV2";
    public static final String LICHESS_LEGACY = "org.lichess.mobileapp";

    private static final Set<String> TARGETS = Set.of(CHROME, LICHESS_V2, LICHESS_LEGACY);

    private TargetAppClassifier() {}

    public static boolean isTarget(String packageName) {
        return packageName != null && TARGETS.contains(packageName);
    }

    public static boolean isSupported(String packageName) {
        return isTarget(packageName);
    }

    public static String classify(String packageName) {
        if (CHROME.equals(packageName)) return "Lichess in Chrome";
        if (LICHESS_V2.equals(packageName)) return "Lichess app";
        if (LICHESS_LEGACY.equals(packageName)) return "Lichess legacy app";
        return "Other app";
    }

    public static String label(String packageName) {
        return classify(packageName);
    }
}

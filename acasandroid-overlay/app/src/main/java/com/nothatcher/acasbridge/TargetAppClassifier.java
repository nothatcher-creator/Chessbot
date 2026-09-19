package com.nothatcher.acasbridge;

import java.util.Set;

public final class TargetAppClassifier {
    public enum Target {
        CHROME,
        LICHESS_APP,
        OTHER
    }

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

    public static Target classify(String packageName) {
        if (CHROME.equals(packageName)) return Target.CHROME;
        if (LICHESS_V2.equals(packageName) || LICHESS_LEGACY.equals(packageName)) {
            return Target.LICHESS_APP;
        }
        return Target.OTHER;
    }

    public static String label(String packageName) {
        return switch (classify(packageName)) {
            case CHROME -> "Lichess in Chrome";
            case LICHESS_APP -> "Lichess app";
            case OTHER -> "Other app";
        };
    }
}

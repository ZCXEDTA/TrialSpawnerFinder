package cn.trialfinder.config;

public enum TrialSearchMode {
    AUTO,
    EXACT;

    public static TrialSearchMode parse(String value) {
        return switch (value.trim().toLowerCase()) {
            case "auto" -> AUTO;
            case "exact" -> EXACT;
            default -> throw new IllegalArgumentException(
                    "trial-search-mode 只能是 auto 或 exact: " + value);
        };
    }
}

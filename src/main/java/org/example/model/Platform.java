package org.example.model;

/** Trading platforms supported by the app. Add new entries here when a new client is implemented. */
public enum Platform {
    DMARKET,
    CSFLOAT,
    WHITEMARKET;

    public String displayName() {
        return switch (this) {
            case DMARKET -> "DMarket";
            case CSFLOAT -> "CSFloat";
            case WHITEMARKET -> "WhiteMarket";
        };
    }

    public static Platform fromDbValue(String value) {
        if (value == null) return null;
        return Platform.valueOf(value.trim().toUpperCase());
    }
}

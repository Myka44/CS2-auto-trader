package org.example.util;

/**
 * Standard Steam wear conditions and their canonical float boundaries, plus
 * the DMarket floatPartValue bucket codes that approximate finer slices
 * within each wear tier (DMarket exposes MW-0..MW-N style buckets per wear).
 */
public enum WearCondition {
    FACTORY_NEW("Factory New", 0.00, 0.07),
    MINIMAL_WEAR("Minimal Wear", 0.07, 0.15),
    FIELD_TESTED("Field-Tested", 0.15, 0.38),
    WELL_WORN("Well-Worn", 0.38, 0.45),
    BATTLE_SCARRED("Battle-Scarred", 0.45, 1.00);

    private final String label;
    private final double floatMin;
    private final double floatMax;

    WearCondition(String label, double floatMin, double floatMax) {
        this.label = label;
        this.floatMin = floatMin;
        this.floatMax = floatMax;
    }

    public String getLabel() { return label; }
    public double getFloatMin() { return floatMin; }
    public double getFloatMax() { return floatMax; }

    public static WearCondition fromLabel(String label) {
        if (label == null) return null;
        for (WearCondition w : values()) {
            if (w.label.equalsIgnoreCase(label.trim())) {
                return w;
            }
        }
        return null;
    }

    /** Best-effort match of an arbitrary float value to its wear tier. */
    public static WearCondition fromFloat(double floatValue) {
        for (WearCondition w : values()) {
            if (floatValue >= w.floatMin && floatValue < w.floatMax) {
                return w;
            }
        }
        return BATTLE_SCARRED;
    }

    @Override
    public String toString() {
        return label;
    }
}

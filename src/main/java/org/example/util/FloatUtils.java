package org.example.util;

/**
 * Maps float ranges / wear conditions to DMarket's undocumented
 * {@code floatPartValue} bucket codes (observed values like "FN-0", "MW-1",
 * "BS-2", or "any").
 *
 * IMPORTANT: DMarket does not publish the exact float boundaries for these
 * buckets -- the codes were reverse-engineered from observed target/listing
 * data (see the original Tasks.java, which hardcoded values like "MW-1",
 * "MW-2"). This class assumes each wear tier is split into 3 equal-width
 * buckets (0, 1, 2), which matches the buckets seen in practice but is NOT
 * guaranteed to be DMarket's exact internal split. Verify against real
 * DMarket search results (using the treeFilters float query) before relying
 * on this for tight float-sniping; for most use cases "any" + the precise
 * floatRangeMin/Max filter on offer search is more reliable than the bucket
 * code alone.
 */
public final class FloatUtils {

    private static final String ANY = "any";

    private FloatUtils() {}

    private static String wearPrefix(WearCondition wear) {
        return switch (wear) {
            case FACTORY_NEW -> "FN";
            case MINIMAL_WEAR -> "MW";
            case FIELD_TESTED -> "FT";
            case WELL_WORN -> "WW";
            case BATTLE_SCARRED -> "BS";
        };
    }

    /**
     * Returns the best-guess floatPartValue bucket code for a given float
     * value, e.g. 0.11 (Minimal Wear) -> "MW-1".
     */
    public static String bucketFor(double floatValue) {
        WearCondition wear = WearCondition.fromFloat(floatValue);
        double tierWidth = wear.getFloatMax() - wear.getFloatMin();
        double posInTier = (floatValue - wear.getFloatMin()) / tierWidth;
        int bucket = (int) Math.floor(posInTier * 3);
        bucket = Math.max(0, Math.min(2, bucket));
        return wearPrefix(wear) + "-" + bucket;
    }

    /**
     * Returns the floatPartValue code(s) that fully cover a custom
     * [floatMin, floatMax) range. If the range exactly matches a whole wear
     * tier (or spans multiple), returns "any" since no single bucket code
     * applies cleanly -- callers should fall back to the precise
     * floatRangeMin/Max offer-search filter in that case.
     */
    public static String floatPartValueForRange(double floatMin, double floatMax) {
        WearCondition wMin = WearCondition.fromFloat(floatMin);
        WearCondition wMax = WearCondition.fromFloat(Math.max(floatMin, floatMax - 1e-9));
        if (wMin != wMax) {
            // Spans more than one wear tier -- no single bucket code applies.
            return ANY;
        }
        if (floatMin <= wMin.getFloatMin() && floatMax >= wMin.getFloatMax()) {
            // Covers the whole wear tier -- "any" within that wear.
            return ANY;
        }
        // Narrow range inside a single tier: pick the bucket containing the midpoint.
        double mid = (floatMin + floatMax) / 2.0;
        return bucketFor(mid);
    }

    public static Boolean floatsIntersect(double floatMin1, double floatMax1, double floatMin2, double floatMax2){
        return floatMin1 < floatMax2 && floatMin2 < floatMax1;
    }

    public static String anyValue() {
        return ANY;
    }
}

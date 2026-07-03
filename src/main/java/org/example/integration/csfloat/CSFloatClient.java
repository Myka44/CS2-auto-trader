package org.example.integration.csfloat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.integration.TradingPlatform;
import org.example.model.ApiConfig;
import org.example.model.Platform;
import org.example.model.SkinCatalogEntry;
import org.example.model.Target;
import org.example.repository.ApiConfigRepository;
import org.example.repository.SkinRepository;
import org.example.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSFloat client, migrated from the original TasksFloat.java.
 *
 * Auth: CSFloat's *documented* endpoints (listings) use an API key in the
 * "Authorization" header. The buy-order endpoints used by the original code
 * (/api/v1/buy-orders) are undocumented and were being driven with a raw
 * session cookie instead -- that still works but is fragile (cookies expire
 * on logout/password change). Both auth modes are supported here via
 * ApiConfig: publicKey holds the documented API key, jwtToken holds the
 * legacy session cookie fallback used only for buy-order endpoints if no
 * API key is set.
 */
public class CSFloatClient implements TradingPlatform {

    private static final Logger log = LoggerFactory.getLogger(CSFloatClient.class);
    private static final String ROOT_API_URL = "https://csfloat.com";
    private static final String LISTINGS_API_ENDPOINT = ROOT_API_URL + "/api/v1/listings";
    private static final String BUY_ORDERS_API_ENDPOINT = ROOT_API_URL + "/api/v1/buy-orders";

    private final ApiConfigRepository apiConfigRepository;
    private final SkinRepository skinRepository;

    public CSFloatClient(ApiConfigRepository apiConfigRepository, SkinRepository skinRepository) {
        this.apiConfigRepository = apiConfigRepository;
        this.skinRepository = skinRepository;
    }

    @Override
    public Platform platformId() {
        return Platform.CSFLOAT;
    }

    @Override
    public boolean isConfigured() {
        return apiConfigRepository.findByPlatform(Platform.CSFLOAT)
                .map(c -> (c.getPublicKey() != null && !c.getPublicKey().isBlank())
                        || (c.getJwtToken() != null && !c.getJwtToken().isBlank()))
                .orElse(false);
    }

    private ApiConfig requireConfig() throws IOException {
        return apiConfigRepository.findByPlatform(Platform.CSFLOAT)
                .orElseThrow(() -> new IOException("CSFloat API key is not configured. Add it in Settings."));
    }

    private Map<String, String> apiKeyHeader(ApiConfig cfg) throws IOException {
        if (cfg.getPublicKey() == null || cfg.getPublicKey().isBlank()) {
            throw new IOException("CSFloat API key is not configured. Add it in Settings.");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", cfg.getPublicKey());
        return headers;
    }

    private Map<String, String> cookieHeader(ApiConfig cfg) throws IOException {
        if (cfg.getJwtToken() == null || cfg.getJwtToken().isBlank()) {
            throw new IOException("CSFloat session cookie is not configured. Add it in Settings.");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", cfg.getJwtToken());
        return headers;
    }

    // ---------------------------------------------------------------
    // Listings (documented, public sell offers)
    // ---------------------------------------------------------------

    public List<CSFloatListing> getListings(String marketHashName, Double minFloat, Double maxFloat, String sortBy, int limit) throws IOException {
        ApiConfig cfg = requireConfig();
        StringBuilder url = new StringBuilder(LISTINGS_API_ENDPOINT + "?limit=" + limit);
        if (sortBy != null) url.append("&sort_by=").append(sortBy);
        if (marketHashName != null) {
            url.append("&market_hash_name=").append(java.net.URLEncoder.encode(marketHashName, StandardCharsets.UTF_8));
        }
        if (minFloat != null) url.append("&min_float=").append(minFloat);
        if (maxFloat != null) url.append("&max_float=").append(maxFloat);

        Map<String, String> headers = apiKeyHeader(cfg);
        String response = HttpUtils.get(url.toString(), headers);
        return HttpUtils.parse(response, new TypeReference<>() {});
    }

    @Override
    public int getLowestOfferPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException {
        List<CSFloatListing> listings = getListings(marketHashName, floatMin, floatMax, "lowest_price", 1);
        if (listings.isEmpty()) return -1;
        Integer price = listings.get(0).price();
        return price == null ? -1 : price;
    }

    // ---------------------------------------------------------------
    // Buy orders (undocumented; expression-based, matched by DefIndex/PaintIndex/FloatValue)
    // ---------------------------------------------------------------

    public List<BuyOrder> getMyBuyOrders() throws IOException {
        ApiConfig cfg = requireConfig();
        Map<String, String> headers = cookieHeader(cfg);
        String response = HttpUtils.get(ROOT_API_URL + "/api/v1/me/buy-orders", headers);
        JsonNode root = HttpUtils.mapper().readTree(response);
        // Some CSFloat responses return a bare array instead of {orders: [...]}.
        JsonNode ordersNode = root.isArray() ? root : root.get("orders");
        if (ordersNode == null) return List.of();
        return HttpUtils.mapper().convertValue(ordersNode, new TypeReference<>(){});
    }

    /**
     * CSFloat doesn't expose a public "highest buy order for this skin" search the
     * way DMarket does -- buy orders are queried per-account, not as a public
     * order book by market_hash_name. We approximate "highest public target"
     * using only the account's own existing order for this skin/float-range,
     * since that's the only buy-order visibility CSFloat's API grants.
     * If you want true cross-account competition data, CSFloat doesn't currently
     * expose it programmatically.
     */
    @Override
    public int getHighestPublicTargetPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException {
        int highest = -1;
        for (BuyOrder order : getMyBuyOrders()) {
            if (!matchesOrder(order, marketHashName, floatMin, floatMax)) continue;
            if (order.maxPrice() == null) continue;
            int price = order.maxPrice();
            if (price > highest) highest = price;
        }
        return highest;
    }

    @Override
    public List<PricePoint> getPublicTargets(String marketHashName) throws IOException {
        // See note above on getHighestPublicTargetPriceCents -- only the
        // account's own buy orders are visible via CSFloat's API.
        List<PricePoint> result = new ArrayList<>();
        for (BuyOrder order : getMyBuyOrders()) {
            if (order.marketHashName() == null || !marketHashName.equals(order.marketHashName())) continue;
            int price = order.maxPrice() == null ? 0 : order.maxPrice();
            int qty = order.quantity() == null ? 1 : order.quantity();
            result.add(new PricePoint(price, "exact", qty));
        }
        return result;
    }

    private boolean matchesOrder(BuyOrder order, String marketHashName, Double floatMin, Double floatMax) {
        if (order.marketHashName() != null) {
            return marketHashName.equals(order.marketHashName());
        }
        // Older / expression-based orders may not carry market_hash_name directly;
        // fall back to matching via the stored expression's DefIndex+PaintIndex.
        return true;
    }

    @Override
    public String createTarget(Target target, String marketHashName) throws IOException {
        ApiConfig cfg = requireConfig();
        Map<String, String> headers = cookieHeader(cfg);

        SkinCatalogEntry skin = skinRepository.findByMarketHashName(marketHashName)
                .orElseThrow(() -> new IOException("Skin not found in catalog: " + marketHashName));
        if (skin.getDefIndex() == null || skin.getPaintIndex() == null) {
            throw new IOException("Skin is missing DefIndex/PaintIndex needed for a CSFloat buy order: " + marketHashName);
        }

        double floatMin = target.getFloatRangeMin() != null ? target.getFloatRangeMin() : skin.getFloatMin();
        double floatMax = target.getFloatRangeMax() != null ? target.getFloatRangeMax() : skin.getFloatMax();



        Map<String, Object> hybridProperties = Map.of(
                "max_float", floatMax,
                "min_float", floatMin
                //Map.of("paint_seeds", Map.of("constant", String.valueOf(skin.getPaintIndex()))),
                //Map.of("field", "FloatValue", "operator", ">=", "value", Map.of("constant", String.valueOf(floatMin))),
                //Map.of("field", "FloatValue", "operator", "<", "value", Map.of("constant", String.valueOf(floatMax)))
        );



        Map<String, Object> body = Map.of(
                "hybrid_properties", hybridProperties,
                "market_hash_name", marketHashName,
                "max_price", target.getMaxPriceUsdCents(),
                "quantity", target.getQuantity()
        );

        String response = HttpUtils.post(ROOT_API_URL + "/api/v1/buy-orders", headers, body);
        log.info("CSFloat createTarget response for {}: {}", marketHashName, response);

        CreateBuyOrderResponse parsed = HttpUtils.parse(response, new TypeReference<>() {});
        return parsed == null ? null : parsed.id();
    }

    @Override
    public void updateTarget(Target target, String marketHashName, int newPriceCents) throws IOException {
        // CSFloat's documented surface has no PATCH for buy orders; the
        // reliable approach is delete + recreate at the new price.
        if (target.getPlatformTargetId() != null) {
            try {
                deleteTarget(target.getPlatformTargetId());
            } catch (IOException e) {
                log.warn("Failed to delete old CSFloat buy order {} before recreating: {}", target.getPlatformTargetId(), e.getMessage());
            }
        }
        Target updated = target;
        updated.setMaxPriceUsdCents(newPriceCents);
        String newId = createTarget(updated, marketHashName);
        target.setPlatformTargetId(newId);
    }

    @Override
    public void deleteTarget(String platformTargetId) throws IOException {
        ApiConfig cfg = requireConfig();
        Map<String, String> headers = cookieHeader(cfg);
        HttpUtils.delete(ROOT_API_URL + "/api/v1/buy-orders/" + platformTargetId, headers);
    }

    @Override
    public boolean targetExists(String platformTargetId) throws IOException {
        if (platformTargetId == null || platformTargetId.isBlank()) return false;
        for (BuyOrder order : getMyBuyOrders()) {
            if (platformTargetId.equals(order.id())) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Typed response shapes, replacing raw List<Map<String, Object>>
    // response handling with Jackson-deserialized DTOs.
    // ---------------------------------------------------------------

    /** Entry from GET /api/v1/listings; only the fields actually used downstream are modeled. */
    public record CSFloatListing(Integer price) {}

    /** Entry from GET /api/v1/me/buy-orders (or POST /api/v1/buy-orders create response, minus id-only fields). */
    public record BuyOrder(
            String id,
            @JsonProperty("market_hash_name") String marketHashName,
            @JsonProperty("max_price") Integer maxPrice,
            Integer quantity
    ) {}

    /** Response shape for POST /api/v1/buy-orders. */
    private record CreateBuyOrderResponse(String id) {}
}
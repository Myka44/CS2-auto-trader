package org.example.integration.dmarket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.integration.TradingPlatform;
import org.example.model.ApiConfig;
import org.example.model.Platform;
import org.example.model.Target;
import org.example.repository.ApiConfigRepository;
import org.example.util.FloatUtils;
import org.example.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DMarket Trading API client, migrated from the original Tasks.java.
 * Auth: every request is signed with Ed25519 using the account's secret key
 * (see DMarketSigner) and sent with X-Api-Key / X-Request-Sign / X-Sign-Date
 * headers, per DMarket's documented signing scheme.
 */
public class DMarketClient implements TradingPlatform {

    private static final Logger log = LoggerFactory.getLogger(DMarketClient.class);
    private static final String ROOT_API_URL = "https://api.dmarket.com";
    private static final String GAME_ID = "a8db";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApiConfigRepository apiConfigRepository;
    private final ApiConfig config;

    public DMarketClient(ApiConfigRepository apiConfigRepository) throws IOException {
        this.apiConfigRepository = apiConfigRepository;
        this.config = requireConfig();
    }

    @Override
    public Platform platformId() {
        return Platform.DMARKET;
    }

    @Override
    public boolean isConfigured() {
        return apiConfigRepository.findByPlatform(Platform.DMARKET)
                .map(c -> c.getPublicKey() != null && !c.getPublicKey().isBlank()
                        && c.getSecretKey() != null && !c.getSecretKey().isBlank())
                .orElse(false);
    }




    private ApiConfig requireConfig() throws IOException {
        return apiConfigRepository.findByPlatform(Platform.DMARKET)
                .filter(c -> c.getPublicKey() != null && c.getSecretKey() != null)
                .orElse(null);
    }

    private Map<String, String> signedGetHeaders(String apiUrlPath, String queryParams) {
        String nonce = String.valueOf(Instant.now().getEpochSecond());
        String stringToSign = "GET" + apiUrlPath + queryParams + nonce;
        String signature = DMarketSigner.signMessage(stringToSign, config.getSecretKey());
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Api-Key", config.getPublicKey());
        headers.put("X-Request-Sign", "dmar ed25519 " + signature);
        headers.put("X-Sign-Date", nonce);
        return headers;
    }

    private Map<String, String> signedPostHeaders(String apiUrlPath, Object body) throws IOException {
        String nonce = String.valueOf(Instant.now().getEpochSecond());
        String stringToSign = "POST" + apiUrlPath + MAPPER.writeValueAsString(body) + nonce;
        String signature = DMarketSigner.signMessage(stringToSign, config.getSecretKey());
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Api-Key", config.getPublicKey());
        headers.put("X-Request-Sign", "dmar ed25519 " + signature);
        headers.put("X-Sign-Date", nonce);
        headers.put("Authorization", config.getJwtToken());
        return headers;
    }

    // ---------------------------------------------------------------
    // Public market offers (sell listings)
    // ---------------------------------------------------------------

    /** Unauthenticated: returns typed market offers for a title, optionally filtered by float range. */
    public List<MarketOffer> getOffersFromMarket(String marketHashName, Double floatMin, Double floatMax) throws IOException {
        String encoded = java.net.URLEncoder.encode(marketHashName, StandardCharsets.UTF_8);
        String url;
        if (floatMin != null && floatMax != null) {
            url = ROOT_API_URL + "/exchange/v1/market/items?gameId=" + GAME_ID
                    + "&treeFilters=floatValueFrom%5B%5D=" + floatMin + ",floatValueTo%5B%5D=" + floatMax
                    + "&limit=100&currency=USD&title=" + encoded;
        } else {
            url = ROOT_API_URL + "/exchange/v1/market/items?gameId=" + GAME_ID
                    + "&limit=100&currency=USD&title=" + encoded;
        }
        String response = HttpUtils.get(url, null);
        MarketOffersResponse parsed = HttpUtils.parse(response, new TypeReference<>() {});
        return parsed == null || parsed.objects() == null ? List.of() : parsed.objects();
    }

    @Override
    public int getLowestOfferPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException {
        List<MarketOffer> offers = getOffersFromMarket(marketHashName, floatMin, floatMax);
        int lowest = -1;
        for (MarketOffer offer : offers) {
            if (offer.price() == null || offer.price().usd() == null) continue;
            int price = offer.price().usd();
            if (lowest == -1 || price < lowest) {
                lowest = price;
            }
        }
        return lowest;
    }

    // ---------------------------------------------------------------
    // Public targets (buy orders) - the order book
    // ---------------------------------------------------------------

    /** Typed public order-book entries for a title (anyone's targets, not just yours). */
    public List<PublicTargetOrder> getTargets(String marketHashName) throws IOException {
        String encoded = java.net.URLEncoder.encode(marketHashName, StandardCharsets.UTF_8).replace("+", "%20");
        String apiUrlPath = "/marketplace-api/v1/targets-by-title/" + GAME_ID + "/" + encoded;
        String response = HttpUtils.get(ROOT_API_URL + apiUrlPath, null);
        PublicTargetsResponse parsed = HttpUtils.parse(response, new TypeReference<>() {});
        return parsed == null || parsed.orders() == null ? List.of() : parsed.orders();
    }

    @Override
    public int getHighestPublicTargetPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException {
        String floatPartValue = (floatMin != null && floatMax != null)
                ? FloatUtils.floatPartValueForRange(floatMin, floatMax)
                : FloatUtils.anyValue();

        List<PublicTargetOrder> allTargets = getTargets(marketHashName);
        int highest = -1;
        for (PublicTargetOrder t : allTargets) {
            if (t.price() == null) continue;
            int price = t.price();
            String thisFloatPartValue = t.attributes() == null ? null : t.attributes().floatPartValue();
            boolean matches = "any".equals(thisFloatPartValue)
                    || floatPartValue.equals("any")
                    || floatPartValue.equals(thisFloatPartValue);
            if (matches && price > highest) {
                highest = price;
            }
        }
        return highest;
    }

    @Override
    public List<PricePoint> getPublicTargets(String marketHashName) throws IOException {
        List<PricePoint> result = new ArrayList<>();
        for (PublicTargetOrder t : getTargets(marketHashName)) {
            if (t.price() == null) continue;
            String fpv = t.attributes() == null ? "any" : t.attributes().floatPartValue();
            int amount = t.amount() == null ? 1 : t.amount();
            result.add(new PricePoint(t.price(), fpv, amount));
        }
        return result;
    }

    // ---------------------------------------------------------------
    // My targets (authenticated)
    // ---------------------------------------------------------------

    public List<UserTarget> getMyTargets(String marketHashName) throws IOException {
        String encoded = java.net.URLEncoder.encode(marketHashName, StandardCharsets.UTF_8);
        String queryParams = "?BasicFilters.Status=TargetStatusActive&BasicFilters.Title=" + encoded;
        String apiUrlPath = "/marketplace-api/v1/user-targets";

        Map<String, String> headers = signedGetHeaders(apiUrlPath, queryParams);
        String response = HttpUtils.get(ROOT_API_URL + apiUrlPath + queryParams, headers);
        UserTargetsResponse parsed = HttpUtils.parse(response, new TypeReference<>() {});
        return parsed == null || parsed.items() == null ? List.of() : parsed.items();
    }

    /** Finds my existing target ID for this skin matching the given floatPartValue ("any" matches any). */
    public String findMyTargetId(String marketHashName, String floatPartValue) throws IOException {
        for (UserTarget target : getMyTargets(marketHashName)) {
            if (target.attributes() == null) continue;
            for (TargetAttribute attr : target.attributes()) {
                if ("floatPartValue".equals(attr.name()) && floatPartValue.equals(attr.value())) {
                    return target.targetId();
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Create / update / delete targets
    // ---------------------------------------------------------------

    @Override
    public String createTarget(Target target, String marketHashName) throws IOException {
        String floatPartValue = resolveFloatPartValue(target);
        double priceUsd = target.getMaxPriceUsdCents() / 100.0;

        Map<String, Object> body = Map.of(
                "GameID", GAME_ID,
                "Targets", List.of(
                        Map.of(
                                "Amount", Integer.toString(target.getQuantity()),
                                "Title", marketHashName,
                                "Price", Map.of(
                                        "Currency", "USD",
                                        "Amount", priceUsd
                                ),
                                "Attrs", Map.of("floatPartValue", floatPartValue)
                        )
                )
        );

        String apiUrlPath = "/marketplace-api/v1/user-targets/create";
        Map<String, String> headers = signedPostHeaders(apiUrlPath, body);
        String response = HttpUtils.post(ROOT_API_URL + apiUrlPath, headers, body);
        log.info("DMarket createTarget response for {}: {}", marketHashName, response);

        // The create endpoint doesn't reliably echo back a usable target ID in a
        // single-call response shape across DMarket API versions, so we
        // immediately re-query our own targets to capture the assigned ID.
        String newId = findMyTargetId(marketHashName, floatPartValue);
        return newId;
    }

    @Override
    public void updateTarget(Target target, String marketHashName, int newPriceCents) throws IOException {
        String floatPartValue = resolveFloatPartValue(target);
        double priceUsd = newPriceCents / 100.0;

        Map<String, Object> body = new HashMap<>();
        body.put("force", true);
        Map<String, Object> singleTarget = new HashMap<>();
        singleTarget.put("id", target.getPlatformTargetId());

        Map<String, Object> targetBody = new HashMap<>();
        targetBody.put("amount", target.getQuantity());
        targetBody.put("gameId", GAME_ID);
        Map<String, Object> priceMap = new HashMap<>();
        priceMap.put("amount", String.valueOf(priceUsd));
        priceMap.put("currency", "USD");
        targetBody.put("price", priceMap);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("gameId", GAME_ID);
        attributes.put("title", marketHashName);
        attributes.put("floatPartValue", floatPartValue);
        targetBody.put("attributes", attributes);
        singleTarget.put("body", targetBody);
        body.put("targets", List.of(singleTarget));

        String apiUrlPath = "/exchange/v1/target/update";
        Map<String, String> headers = signedPostHeaders(apiUrlPath, body);
        String response = HttpUtils.post(ROOT_API_URL + apiUrlPath, headers, body);
        log.info("DMarket updateTarget response for {}: {}", marketHashName, response);
    }

    @Override
    public void deleteTarget(String platformTargetId) throws IOException {
        Map<String, Object> body = Map.of("Targets", List.of(Map.of("TargetID", platformTargetId)));
        String apiUrlPath = "/marketplace-api/v1/user-targets/delete";
        Map<String, String> headers = signedPostHeaders(apiUrlPath, body);
        HttpUtils.post(ROOT_API_URL + apiUrlPath, headers, body);
    }

    @Override
    public boolean targetExists(String platformTargetId) throws IOException {
        if (platformTargetId == null || platformTargetId.isBlank()) return false;
        String apiUrlPath = "/marketplace-api/v1/user-targets";
        String queryParams = "?BasicFilters.Status=TargetStatusActive";
        Map<String, String> headers = signedGetHeaders(apiUrlPath, queryParams);
        String response = HttpUtils.get(ROOT_API_URL + apiUrlPath + queryParams, headers);
        UserTargetsResponse parsed = HttpUtils.parse(response, new TypeReference<>() {});
        if (parsed == null || parsed.items() == null) return false;
        for (UserTarget target : parsed.items()) {
            if (platformTargetId.equals(target.targetId())) {
                return true;
            }
        }
        return false;
    }

    private String resolveFloatPartValue(Target target) {
        if (target.getFloatPartValue() != null && !target.getFloatPartValue().isBlank()) {
            return target.getFloatPartValue();
        }
        if (target.getFloatRangeMin() != null && target.getFloatRangeMax() != null) {
            return FloatUtils.floatPartValueForRange(target.getFloatRangeMin(), target.getFloatRangeMax());
        }
        return FloatUtils.anyValue();
    }

    // ---------------------------------------------------------------
    // Typed response shapes, replacing raw List<Map<String, Object>>
    // response handling with Jackson-deserialized DTOs. Numeric fields
    // are typed as Integer even though DMarket sometimes sends them as
    // JSON strings ("123") -- Jackson coerces numeric strings to numbers
    // by default, so no manual parseInt/casting is needed by callers.
    // ---------------------------------------------------------------

    /** Response shape for GET /exchange/v1/market/items. */
    private record MarketOffersResponse(List<MarketOffer> objects) {}

    public record MarketOffer(OfferPrice price) {}

    public record OfferPrice(@JsonProperty("USD") Integer usd) {}

    /** Response shape for GET /marketplace-api/v1/targets-by-title/{gameId}/{title}. */
    private record PublicTargetsResponse(List<PublicTargetOrder> orders) {}

    public record PublicTargetOrder(Integer price, TargetAttributes attributes, Integer amount) {}

    public record TargetAttributes(String floatPartValue) {}

    /** Response shape for GET /marketplace-api/v1/user-targets. */
    private record UserTargetsResponse(@JsonProperty("Items") List<UserTarget> items) {}

    public record UserTarget(
            @JsonProperty("TargetID") String targetId,
            @JsonProperty("Attributes") List<TargetAttribute> attributes
    ) {}

    public record TargetAttribute(@JsonProperty("Name") String name, @JsonProperty("Value") String value) {}
}
package org.example.integration.WhiteMarket;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.example.integration.TradingPlatform;
import org.example.model.Platform;
import org.example.model.SkinCatalogEntry;
import org.example.model.Target;
import org.example.repository.ApiConfigRepository;
import org.example.repository.SkinRepository;
import org.example.util.FloatUtils;
import org.example.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WhiteMarketClient implements TradingPlatform {

    private static final Logger log = LoggerFactory.getLogger(WhiteMarketClient.class);

    private static final String BASE_URL = "https://api.white.market/graphql/partner";
    private final ApiConfigRepository apiConfigRepository;
    private final SkinRepository skinRepository;
    private static final String APP_ID = "CSGO";
    private static final String CSGO_FLOAT_PARAM = "CSGO_FLOAT";

    // ---------------------------------------------------------------
    // Queries / mutations
    // ---------------------------------------------------------------

    private static final String MARKET_LIST_QUERY = """
            query MarketList($nameHash: String, $floatFrom: String, $floatTo: String, $first: Int) {
              market_list(
                search: {
                  appId: CSGO
                  nameHash: $nameHash
                  sort: {
                    field: PRICE
                    type: ASC
                    }
                  csgoFloatFrom: $floatFrom
                  csgoFloatTo: $floatTo
                }
                forwardPagination: { first: $first }
              ) {
                edges {
                  node {
                    id
                    price { value currency }
                    item {
                      nameHash
                      description { name }
                      order { params { param value } }
                    }
                    createdAt
                  }
                }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
        """;

    private static final String TARGET_LIST_QUERY = """
            query TargetList($nameHash: String, $first: Int, $after: String) {
              order_list(
                search: {
                  appId: CSGO
                  nameHash: $nameHash
                  sort: {
                    field: PRICE
                    type: DESC
                    }
                }
                forwardPagination: { first: $first, after: $after }
              ) {
                edges {
                  node {
                    id
                    price { value currency }
                    params { param value }
                  }
                }
                pageInfo { hasNextPage endCursor }
                totalCount
              }
            }
        """;

    /**
     * My own active orders across all skins (order_my_active), used by
     * targetExists/getPublicTargets-style "do I already have this" checks
     * without needing to scan a specific title's whole public order book.
     */
    private static final String MY_ACTIVE_ORDERS_QUERY = """
            query MyActiveOrders($first: Int, $after: String) {
              order_my_active(
                forwardPagination: { first: $first, after: $after }
              ) {
                edges {
                  node {
                    id
                    nameHash
                    price { value currency }
                    status
                    params { param value }
                  }
                  cursor
                }
                pageInfo { hasNextPage hasPreviousPage startCursor endCursor }
                totalCount
              }
            }
        """;

    /**
     * order_new returns the created MarketOrder directly (not wrapped in a
     * connection) -- per docs its params.value is a single scalar, not the
     * array shape used by the list-query params above.
     */
    private static final String CREATE_TARGET_QUERY = """
            mutation CreateTarget($nameHash: String, $floatFrom: String, $floatTo: String, $amount: Int, $price: String) {
              order_new(
                app: CSGO
                nameHash: $nameHash
                price: {
                  value: $price
                  currency: USD
                }
                quantity: $amount
                csgoFloat: {
                  from: $floatFrom
                  to: $floatTo
                }
              ) {
                id
                nameHash
                quantity
                status
                price { value currency }
                params { param value }
                createdAt
                expiredAt
              }
            }
        """;

    /** order_edit returns the updated MarketOrder directly, same shape as order_new. */
    private static final String UPDATE_TARGET_QUERY = """
            mutation UpdateTarget($id: String, $amount: Int, $price: String) {
              order_edit(
                id: $id
                price: {
                  value: $price
                  currency: USD
                }
                quantity: $amount
              ) {
                id
                nameHash
                quantity
                status
                price { value currency }
                params { param value }
                createdAt
                expiredAt
              }
            }
        """;

    /** order_delete returns the deleted MarketOrder directly, same shape as order_new. */
    private static final String DELETE_TARGET_QUERY = """
            mutation DeleteTarget($ids: [String!]) {
              order_delete(ids: $ids) {
                id
                nameHash
                status
                price { value currency }
              }
            }
        """;

    public WhiteMarketClient(ApiConfigRepository apiConfigRepository, SkinRepository skinRepository) {
        this.apiConfigRepository = apiConfigRepository;
        this.skinRepository = skinRepository;
    }

    @Override
    public Platform platformId() {
        return Platform.WHITEMARKET;
    }

    @Override
    public int getLowestOfferPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException {

        Map<String, String> headers = getHeaders();
        Map<String, Object> body = getBody(MARKET_LIST_QUERY, marketHashName, floatMin, floatMax);

        String response = HttpUtils.get(BASE_URL, headers, body);

        GraphQLResponse<MarketListData> parsed = HttpUtils.parse(response, new TypeReference<>() {});

        if (parsed == null || parsed.data() == null || parsed.data().marketList() == null) {
            throw new IOException("Unexpected White Market response shape: " + response);
        }

        List<Edge<MarketListNode>> edges = parsed.data().marketList().edges();
        if (edges == null || edges.isEmpty()) {
            return -1;
        }

        Price price = edges.get(0).node().price();
        return priceToCents(price);
    }

    @Override
    public int getHighestPublicTargetPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException {
        Map<String, String> headers = getHeaders();
        Map<String, Object> body = getBody(TARGET_LIST_QUERY, marketHashName, floatMin, floatMax);

        String response = HttpUtils.get(BASE_URL, headers, body);

        GraphQLResponse<TargetListData> parsed = HttpUtils.parse(response, new TypeReference<>() {});

        if (parsed == null || parsed.data() == null || parsed.data().targetList() == null) {
            throw new IOException("Unexpected White Market response shape: " + response);
        }

        List<Edge<TargetListNode>> edges = parsed.data().targetList().edges();
        if (edges == null || edges.isEmpty()) {
            return -1;
        }

        int highestPrice = -1;
        for (Edge<TargetListNode> edge : edges) {
            TargetListNode node = edge.node();
            if (!matchesFloatRange(node.params(), floatMin, floatMax)) {
                continue;
            }
            int priceCents = priceToCents(node.price());
            if (priceCents > highestPrice) {
                highestPrice = priceCents;
            }
        }

        return highestPrice;
    }

    @Override
    public List<PricePoint> getPublicTargets(String marketHashName) throws IOException {
        List<PricePoint> result = new ArrayList<>();
        boolean hasNextPage = true;
        String after = null;

        while (hasNextPage) {
            Map<String, Object> body = getBody(TARGET_LIST_QUERY, marketHashName, null, null, after);
            String response = HttpUtils.get(BASE_URL, getHeaders(), body);

            GraphQLResponse<TargetListData> parsed = HttpUtils.parse(response, new TypeReference<>() {});
            if (parsed == null || parsed.data() == null || parsed.data().targetList() == null) {
                throw new IOException("Unexpected White Market response shape: " + response);
            }

            Connection<TargetListNode> targetList = parsed.data().targetList();
            List<Edge<TargetListNode>> edges = targetList.edges();
            if (edges != null) {
                for (Edge<TargetListNode> edge : edges) {
                    TargetListNode node = edge.node();
                    double[] floatRange = extractFloatRange(node.params());
                    String floatPartValue = floatRange == null
                            ? FloatUtils.anyValue()
                            : FloatUtils.floatPartValueForRange(floatRange[0], floatRange[1]);
                    result.add(new PricePoint(priceToCents(node.price()), floatPartValue, 1));
                }
            }

            hasNextPage = targetList.pageInfo().hasNextPage();
            after = targetList.pageInfo().endCursor();
        }

        return result;
    }

    @Override
    public String createTarget(Target target, String marketHashName) throws IOException {
        double floatMin = target.getFloatRangeMin();
        double floatMax = target.getFloatRangeMax();

        if (target.getFloatRangeMin() == null || target.getFloatRangeMax() == null) {
            SkinCatalogEntry skin = skinRepository.findByMarketHashName(marketHashName)
                    .orElseThrow(() -> new IOException("Skin not found in catalog: " + marketHashName));
            floatMin = target.getFloatRangeMin() != null ? target.getFloatRangeMin() : skin.getFloatMin();
            floatMax = target.getFloatRangeMax() != null ? target.getFloatRangeMax() : skin.getFloatMax();
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("nameHash", marketHashName);
        variables.put("floatFrom", String.valueOf(floatMin));
        variables.put("floatTo", String.valueOf(floatMax));
        variables.put("amount", target.getQuantity());
        variables.put("price", usdCentsToValueString(target.getMaxPriceUsdCents()));

        Map<String, String> headers = getHeaders();
        Map<String, Object> body = getBody(CREATE_TARGET_QUERY, variables);

        String response = HttpUtils.post(BASE_URL, headers, body);
        log.info("White Market createTarget response for {}: {}", marketHashName, response);

        GraphQLResponse<CreateTargetData> parsed = HttpUtils.parse(response, new TypeReference<>() {});
        if (parsed == null || parsed.data() == null || parsed.data().orderNew() == null) {
            throw new IOException("Unexpected White Market createTarget response shape: " + response);
        }

        return parsed.data().orderNew().id();
    }

    @Override
    public void updateTarget(Target target, String marketHashName, int newPriceCents) throws IOException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("id", target.getPlatformTargetId());
        variables.put("amount", target.getQuantity());
        variables.put("price", usdCentsToValueString(newPriceCents));

        Map<String, String> headers = getHeaders();
        Map<String, Object> body = getBody(UPDATE_TARGET_QUERY, variables);

        String response = HttpUtils.post(BASE_URL, headers, body);
        log.info("White Market updateTarget response for {}: {}", marketHashName, response);

        GraphQLResponse<UpdateTargetData> parsed = HttpUtils.parse(response, new TypeReference<>() {});
        if (parsed == null || parsed.data() == null || parsed.data().orderEdit() == null) {
            throw new IOException("Unexpected White Market updateTarget response shape: " + response);
        }
    }

    @Override
    public void deleteTarget(String platformTargetId) throws IOException {
        Map<String, Object> variables = new HashMap<>();
        variables.put("ids", List.of(platformTargetId));

        Map<String, String> headers = getHeaders();
        Map<String, Object> body = getBody(DELETE_TARGET_QUERY, variables);

        String response = HttpUtils.post(BASE_URL, headers, body);
        log.info("White Market deleteTarget response for {}: {}", platformTargetId, response);

        // order_delete echoes back the deleted order -- not strictly needed by
        // callers today, but worth surfacing a clear error if the shape is off
        // rather than silently swallowing a delete that didn't actually happen.
        GraphQLResponse<DeleteTargetData> parsed = HttpUtils.parse(response, new TypeReference<>() {});
        if (parsed == null || parsed.data() == null || parsed.data().orderDelete() == null) {
            throw new IOException("Unexpected White Market deleteTarget response shape: " + response);
        }
    }

    @Override
    public boolean targetExists(String platformTargetId) throws IOException {
        if (platformTargetId == null || platformTargetId.isBlank()) {
            return false;
        }

        Map<String, String> headers = getHeaders();
        boolean hasNextPage = true;
        String after = null;

        while (hasNextPage) {
            Map<String, Object> variables = new HashMap<>();
            variables.put("first", 100);
            variables.put("after", after);

            Map<String, Object> body = getBody(MY_ACTIVE_ORDERS_QUERY, variables);
            String response = HttpUtils.get(BASE_URL, headers, body);

            GraphQLResponse<MyActiveOrdersData> parsed = HttpUtils.parse(response, new TypeReference<>() {});
            if (parsed == null || parsed.data() == null || parsed.data().orderMyActive() == null) {
                throw new IOException("Unexpected White Market response shape: " + response);
            }

            Connection<MyActiveOrderNode> activeOrders = parsed.data().orderMyActive();
            List<Edge<MyActiveOrderNode>> edges = activeOrders.edges();
            if (edges != null) {
                for (Edge<MyActiveOrderNode> edge : edges) {
                    if (edge.node().id().equals(platformTargetId)) {
                        return true;
                    }
                }
            }

            hasNextPage = activeOrders.pageInfo().hasNextPage();
            after = activeOrders.pageInfo().endCursor();
        }

        return false;
    }

    @Override
    public boolean isConfigured() {
        return apiConfigRepository.findByPlatform(Platform.WHITEMARKET)
                .map(c -> (c.getSecretKey() != null && !c.getSecretKey().isBlank())
                        || (c.getJwtToken() != null && !c.getJwtToken().isBlank()))
                .orElse(false);
    }

    /**
     * Builds the GraphQL request body for queries keyed by nameHash + float
     * range + page size (market list / target list).
     */
    private Map<String, Object> getBody(String query, String marketHashName, Double floatMin, Double floatMax) {
        return getBody(query, marketHashName, floatMin, floatMax, null);
    }

    private Map<String, Object> getBody(String query, String marketHashName, Double floatMin, Double floatMax, String after) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("nameHash", marketHashName);
        variables.put("floatFrom", floatMin != null ? floatMin.toString() : null);
        variables.put("floatTo", floatMax != null ? floatMax.toString() : null);
        variables.put("first", 50);
        variables.put("after", after);
        return getBody(query, variables);
    }

    /**
     * Builds the GraphQL request body. "variables" must be a real JSON object,
     * not a pre-serialized JSON string -- HttpUtils will serialize this whole
     * map to JSON once, so a String value here would get double-encoded.
     */
    private Map<String, Object> getBody(String query, Map<String, Object> variables) {
        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("variables", variables);
        return body;
    }

    private Map<String, String> getHeaders() {
        return Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer " + apiConfigRepository.findByPlatform(Platform.WHITEMARKET).get().getJwtToken()
        );
    }

    private static int priceToCents(Price price) {
        return new BigDecimal(price.value()).movePointRight(2).intValueExact();
    }

    private static String usdCentsToValueString(int cents) {
        return new BigDecimal(cents).movePointLeft(2).toPlainString();
    }

    /**
     * True if this node's CSGO_FLOAT param overlaps [floatMin, floatMax], or
     * if either side is unbounded (null) -- a null floatMin/floatMax means
     * "don't filter by float", matching the other platform clients'
     * convention (see DMarketClient/CSFloatClient).
     */
    private static boolean matchesFloatRange(List<OrderParam> params, Double floatMin, Double floatMax) {
        if (floatMin == null || floatMax == null) {
            return true;
        }
        double[] floatRange = extractFloatRange(params);
        if (floatRange == null) {
            // No CSGO_FLOAT param on this node -- treat as "any float" rather
            // than excluding it, consistent with FloatUtils.anyValue() semantics.
            return true;
        }
        return FloatUtils.floatsIntersect(floatMin, floatMax, floatRange[0], floatRange[1]);
    }

    /**
     * Pulls the [min, max] float range out of a node's params list, if a
     * CSGO_FLOAT param is present. This is for the list-query param shape,
     * where value is an array of two numeric strings (min, max) -- e.g.
     * ["0.00...", "0.07..."]. Other params (e.g. CSGO_PHASE) carry a single
     * null in that array instead, so this only parses when the param name
     * and array shape actually match.
     *
     * @return {min, max}, or null if no CSGO_FLOAT param is present
     */
    private static double[] extractFloatRange(List<OrderParam> params) {
        if (params == null) {
            return null;
        }
        for (OrderParam p : params) {
            if (CSGO_FLOAT_PARAM.equals(p.param()) && p.value() != null && p.value().size() == 2) {
                String lo = p.value().get(0);
                String hi = p.value().get(1);
                if (lo != null && hi != null) {
                    return new double[]{ Double.parseDouble(lo), Double.parseDouble(hi) };
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------
    // Generic GraphQL response shapes, shared by every query/mutation here.
    // Relay-style connections (edges/node/pageInfo) are structurally
    // identical regardless of what the node contains, so only the node
    // type varies per query.
    // ---------------------------------------------------------------

    private record GraphQLResponse<T>(@JsonProperty("data") T data) {}

    private record Connection<T>(List<Edge<T>> edges, PageInfo pageInfo, int totalCount) {}

    private record Edge<T>(T node) {}

    private record PageInfo(boolean hasNextPage, String endCursor) {}

    // --- Query-specific "data" wrappers ---

    private record MarketListData(@JsonProperty("market_list") Connection<MarketListNode> marketList) {}

    private record TargetListData(@JsonProperty("order_list") Connection<TargetListNode> targetList) {}

    private record MyActiveOrdersData(@JsonProperty("order_my_active") Connection<MyActiveOrderNode> orderMyActive) {}

    /**
     * order_new/order_edit/order_delete each return a MarketOrder object
     * directly under "data" -- not a connection, unlike the list queries.
     */
    private record CreateTargetData(@JsonProperty("order_new") MarketOrder orderNew) {}

    private record UpdateTargetData(@JsonProperty("order_edit") MarketOrder orderEdit) {}

    private record DeleteTargetData(@JsonProperty("order_delete") MarketOrder orderDelete) {}

    // --- Node shapes ---

    private record MarketListNode(String id, Price price, Item item, String createdAt) {}

    /** Node shape from order_list / order_my_active -- params.value here is an array (see OrderParam). */
    private record TargetListNode(String id, Price price, List<OrderParam> params) {}

    private record MyActiveOrderNode(String id, String nameHash, Price price, String status, List<OrderParam> params) {}

    /**
     * The full MarketOrder type returned directly by order_new / order_edit /
     * order_delete. Per the docs this carries more fields than we currently
     * use (app, quantitySuccess/Progress/Failed, icon, iconLarge, link) --
     * those are omitted here since Jackson's FAIL_ON_UNKNOWN_PROPERTIES is
     * disabled project-wide, so unrecognized fields are safely ignored
     * rather than throwing. Add fields here if a caller ever needs them.
     *
     * IMPORTANT: params.value on this type is a single scalar (e.g.
     * "0.123456"), NOT the two-element array used by the list-query node
     * shapes above -- hence the separate MutationOrderParam record using
     * JsonNode to tolerate that scalar shape safely.
     */
    private record MarketOrder(
            String id,
            String nameHash,
            Integer quantity,
            String status,
            Price price,
            List<MutationOrderParam> params,
            String createdAt,
            String expiredAt
    ) {}

    private record Price(String value, String currency) {}

    private record Item(String nameHash, Description description, Order order) {}

    private record Description(String name) {}

    private record Order(List<OrderParam> params) {}

    /** List-query param shape: value is an array, e.g. ["0.00...", "0.07..."] or [null]. */
    private record OrderParam(String param, List<String> value) {}

    /**
     * Mutation-response param shape: value is documented as a single scalar
     * (e.g. 0.123456), which is a different shape than OrderParam above.
     * Typed as JsonNode rather than String/Double so deserialization never
     * fails regardless of whether White Market sends it as a number, a
     * string, or (inconsistently) an array -- callers should check
     * isArray()/isTextual()/isNumber() before reading.
     */
    private record MutationOrderParam(String param, JsonNode value) {}
}
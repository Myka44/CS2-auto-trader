package org.example.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Generic HTTP helpers shared by all platform clients. Each platform client
 * owns its own auth-header construction; this class just executes requests
 * and parses JSON responses, plus raises a typed exception with the HTTP
 * status + body on failure so callers can distinguish "no offers" from
 * "auth failed" from "rate limited".
 */
public final class HttpUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpUtils.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private HttpUtils() {}

    public static class HttpException extends IOException {
        private final int statusCode;
        private final String body;

        public HttpException(int statusCode, String body) {
            super("HTTP " + statusCode + ": " + truncate(body));
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }

        private static String truncate(String s) {
            if (s == null) return "";
            return s.length() > 500 ? s.substring(0, 500) + "..." : s;
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String get(String url, Map<String, String> headers) throws IOException {
        Request req = Request.get(url);
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        return execute(req);
    }

    public static String get(String url, Map<String, String> headers, Object jsonBody) throws IOException {
        Request req = Request.get(url);
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        if (jsonBody != null) {
            req.bodyString(MAPPER.writeValueAsString(jsonBody), ContentType.APPLICATION_JSON);
        }
        return execute(req);
    }

    public static String post(String url, Map<String, String> headers, Object jsonBody) throws IOException {
        Request req = Request.post(url);
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        if (jsonBody != null) {
            req.bodyString(MAPPER.writeValueAsString(jsonBody), ContentType.APPLICATION_JSON);
        }
        return execute(req);
    }

    public static String patch(String url, Map<String, String> headers, Object jsonBody) throws IOException {
        Request req = Request.patch(url);
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        if (jsonBody != null) {
            req.bodyString(MAPPER.writeValueAsString(jsonBody), ContentType.APPLICATION_JSON);
        }
        return execute(req);
    }

    public static String delete(String url, Map<String, String> headers) throws IOException {
        Request req = Request.delete(url);
        if (headers != null) {
            headers.forEach(req::addHeader);
        }
        return execute(req);
    }

    private static String execute(Request req) throws IOException {
        Response response = req.execute();
        ClassicHttpResponse classic = (ClassicHttpResponse) response.returnResponse();
        int status = classic.getCode();
        HttpEntity entity = classic.getEntity();
        String body = entity != null
                ? new String(entity.getContent().readAllBytes())
                : "";
        if (status < 200 || status >= 300) {
            log.warn("HTTP request failed with status {}: {}", status, truncateForLog(body));
            throw new HttpException(status, body);
        }
        return body;
    }

    private static String truncateForLog(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) throws IOException {
        return MAPPER.readValue(json, Map.class);
    }

    public static <T> T parse(String json, TypeReference<T> typeRef) throws IOException {
        return MAPPER.readValue(json, typeRef);
    }
}
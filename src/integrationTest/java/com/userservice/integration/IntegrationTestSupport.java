package com.userservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

abstract class IntegrationTestSupport {

    protected static final String BASE_URL =
            System.getenv().getOrDefault("INTEGRATION_BASE_URL", "http://localhost:8080");
    private static final String JDBC_URL =
            System.getenv().getOrDefault("INTEGRATION_JDBC_URL", "jdbc:h2:tcp://localhost:9092/./userservice");
    private static final String JDBC_USER =
            System.getenv().getOrDefault("INTEGRATION_JDBC_USER", "sa");
    private static final String JDBC_PASSWORD =
            System.getenv().getOrDefault("INTEGRATION_JDBC_PASSWORD", "");

    protected static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    protected ApiResponse get(String path) throws Exception {
        return request("GET", path, null);
    }

    protected ApiResponse post(String path, String body) throws Exception {
        return request("POST", path, body);
    }

    protected ApiResponse put(String path, String body) throws Exception {
        return request("PUT", path, body);
    }

    protected ApiResponse delete(String path) throws Exception {
        return request("DELETE", path, null);
    }

    protected ApiResponse request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json, application/problem+json");
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode json = response.body().isBlank() ? null : JSON.readTree(response.body());
        return new ApiResponse(response.statusCode(), response.headers().map(), response.body(), json);
    }

    protected long scalarLong(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new AssertionError("Query returned no rows: " + sql);
            }
            return result.getLong(1);
        }
    }

    protected String scalarString(String sql) throws SQLException {
        try (Connection connection = connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new AssertionError("Query returned no rows: " + sql);
            }
            return result.getString(1);
        }
    }

    protected Connection connection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    protected record ApiResponse(
            int status,
            Map<String, List<String>> headers,
            String body,
            JsonNode json
    ) {
        String header(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream())
                    .findFirst()
                    .orElse(null);
        }
    }
}

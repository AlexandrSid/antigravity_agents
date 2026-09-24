package com.userservice.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiE2ETest extends IntegrationTestSupport {

    @Test
    void createLookupReplaceFamilyAndSoftDelete_happyPath() throws Exception {
        ApiResponse created = post("/api/v1/users", userBody(
                "Happy", "Path", "happy.path@example.com", "+1 (202) 555-0101",
                address("United   States", "New York", "Main Street", "10", null, "10001"),
                "1", "51"
        ));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.header("location")).matches("/api/v1/users/\\d+");
        long id = created.json().path("id").asLong();
        assertThat(id).isGreaterThan(1_000);
        assertThat(created.json().path("email").asText()).isEqualTo("happy.path@example.com");
        assertThat(created.json().path("phoneNumber").asText()).isEqualTo("+12025550101");
        assertThat(created.json().path("childrenIds").isArray()).isTrue();

        ApiResponse byId = get("/api/v1/users/" + id);
        assertThat(byId.status()).isEqualTo(200);
        assertThat(byId.json().path("id").asLong()).isEqualTo(id);

        ApiResponse byEmail = get("/api/v1/users/by-email?email="
                + URLEncoder.encode("HAPPY.PATH@EXAMPLE.COM", StandardCharsets.UTF_8));
        assertThat(byEmail.status()).isEqualTo(200);
        assertThat(byEmail.json().path("id").asLong()).isEqualTo(id);

        ApiResponse updated = put("/api/v1/users/" + id, userBody(
                "Updated", "User", "updated.path@example.com", null, null, null, null
        ));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.json().path("firstName").asText()).isEqualTo("Updated");
        assertThat(updated.json().has("phoneNumber")).isFalse();
        assertThat(updated.json().has("address")).isFalse();
        assertThat(updated.json().has("fatherId")).isFalse();
        assertThat(updated.json().has("motherId")).isFalse();

        ApiResponse family = get("/api/v1/users/" + id + "/family");
        assertThat(family.status()).isEqualTo(200);
        assertThat(family.json().isArray()).isTrue();
        assertThat(family.json()).hasSize(1);
        assertThat(family.json().get(0).path("id").asLong()).isEqualTo(id);

        ApiResponse deleted = delete("/api/v1/users/" + id);
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertProblem(get("/api/v1/users/" + id), 404, "urn:user-service:error:user-not-found");
    }

    @Test
    void activeIdentityConflictsAndDeletedIdentityReuse() throws Exception {
        String email = "identity.reuse@example.com";
        String phone = "+12025550102";
        ApiResponse first = post("/api/v1/users", userBody("First", "Owner", email, phone, null, null, null));
        assertThat(first.status()).isEqualTo(201);

        assertProblem(
                post("/api/v1/users", userBody("Email", "Conflict", email.toUpperCase(), "+12025550103", null, null, null)),
                409,
                "urn:user-service:error:duplicate-email"
        );
        assertProblem(
                post("/api/v1/users", userBody("Phone", "Conflict", "other.identity@example.com", "+1 (202) 555-0102", null, null, null)),
                409,
                "urn:user-service:error:duplicate-phone"
        );

        assertThat(delete("/api/v1/users/" + first.json().path("id").asLong()).status()).isEqualTo(204);
        ApiResponse reused = post("/api/v1/users", userBody("Reused", "Owner", email, phone, null, null, null));
        assertThat(reused.status()).isEqualTo(201);
    }

    @Test
    void addressNormalizationReusesMatchAndPostalDifferenceCreatesNewAddress() throws Exception {
        ApiResponse first = post("/api/v1/users", userBody(
                "Address", "One", "address.one@example.com", null,
                address("United   States", "New   York", "Main Street", "10", "2 A", "10001"),
                null, null
        ));
        ApiResponse equivalent = post("/api/v1/users", userBody(
                "Address", "Two", "address.two@example.com", null,
                address(" united states ", "new york", "MAIN   STREET", "10", "2 a", "10001"),
                null, null
        ));
        ApiResponse differentPostal = post("/api/v1/users", userBody(
                "Address", "Three", "address.three@example.com", null,
                address("United States", "New York", "Main Street", "10", "2 A", "10002"),
                null, null
        ));
        assertThat(first.status()).isEqualTo(201);
        assertThat(equivalent.status()).isEqualTo(201);
        assertThat(differentPostal.status()).isEqualTo(201);
        long firstAddress = first.json().path("address").path("id").asLong();
        assertThat(equivalent.json().path("address").path("id").asLong()).isEqualTo(firstAddress);
        assertThat(differentPostal.json().path("address").path("id").asLong()).isNotEqualTo(firstAddress);
    }

    @Test
    void concurrentEquivalentAddressesReuseOneAddressRow() throws Exception {
        int requestCount = 8;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        List<Future<ApiResponse>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return post("/api/v1/users", userBody(
                            "Concurrent",
                            "Address" + requestIndex,
                            "concurrent.address." + requestIndex + "@example.com",
                            null,
                            address("Concurrency Land", "Race City", "Parallel Street", "8", null, "C-8"),
                            null,
                            null
                    ));
                }));
            }
            ready.await();
            start.countDown();
            List<ApiResponse> responses = new ArrayList<>();
            for (Future<ApiResponse> future : futures) {
                responses.add(future.get());
            }
            assertThat(responses).allSatisfy(response -> assertThat(response.status()).isEqualTo(201));
            assertThat(responses.stream()
                    .map(response -> response.json().path("address").path("id").asLong())
                    .distinct()
                    .toList()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void validationMalformedJsonAndInvalidPhoneReturnProblemDetails() throws Exception {
        assertProblem(
                post("/api/v1/users", """
                        {"firstName":"","lastName":"User","birthDate":"2999-01-01","email":"invalid"}
                        """),
                400,
                "urn:user-service:error:validation-failed"
        );
        assertProblem(post("/api/v1/users", "{"), 400, "urn:user-service:error:malformed-json");
        assertProblem(
                post("/api/v1/users", userBody("Invalid", "Phone", "invalid.phone@example.com", "call-me", null, null, null)),
                400,
                "urn:user-service:error:validation-failed"
        );
    }

    @Test
    void missingSameSelfAndCyclicParentsAreRejected() throws Exception {
        assertProblem(
                post("/api/v1/users", userBody("Missing", "Parent", "missing.parent@example.com", null, null, "999999", null)),
                404,
                "urn:user-service:error:parent-not-found"
        );
        assertProblem(
                post("/api/v1/users", userBody("Same", "Parent", "same.parent@example.com", null, null, "1", "1")),
                400,
                "urn:user-service:error:invalid-parent-relation"
        );

        ApiResponse parent = post("/api/v1/users", userBody("Cycle", "Parent", "cycle.parent@example.com", null, null, null, null));
        long parentId = parent.json().path("id").asLong();
        ApiResponse child = post("/api/v1/users", userBody("Cycle", "Child", "cycle.child@example.com", null, null, Long.toString(parentId), null));
        long childId = child.json().path("id").asLong();

        assertProblem(
                put("/api/v1/users/" + parentId, userBody(
                        "Cycle", "Parent", "cycle.parent@example.com", null, null, Long.toString(parentId), null
                )),
                400,
                "urn:user-service:error:invalid-parent-relation"
        );
        assertProblem(
                put("/api/v1/users/" + parentId, userBody(
                        "Cycle", "Parent", "cycle.parent@example.com", null, null, Long.toString(childId), null
                )),
                400,
                "urn:user-service:error:invalid-parent-relation"
        );
    }

    @Test
    void familyExcludesDeletedRelationsAndHandlesDeletedAnchor() throws Exception {
        ApiResponse father = post("/api/v1/users", userBody("Family", "Father", "family.father@example.com", null, null, null, null));
        ApiResponse mother = post("/api/v1/users", userBody("Family", "Mother", "family.mother@example.com", null, null, null, null));
        long fatherId = father.json().path("id").asLong();
        long motherId = mother.json().path("id").asLong();
        ApiResponse child = post("/api/v1/users", userBody(
                "Family", "Child", "family.child@example.com", null, null,
                Long.toString(fatherId), Long.toString(motherId)
        ));
        long childId = child.json().path("id").asLong();

        assertThat(delete("/api/v1/users/" + motherId).status()).isEqualTo(204);
        ApiResponse childAfterDelete = get("/api/v1/users/" + childId);
        assertThat(childAfterDelete.status()).isEqualTo(200);
        assertThat(childAfterDelete.json().path("fatherId").asLong()).isEqualTo(fatherId);
        assertThat(childAfterDelete.json().has("motherId")).isFalse();

        ApiResponse activeFamily = get("/api/v1/users/" + fatherId + "/family");
        assertThat(ids(activeFamily.json())).contains(fatherId, childId).doesNotContain(motherId);

        assertThat(delete("/api/v1/users/" + fatherId).status()).isEqualTo(204);
        ApiResponse deletedAnchorFamily = get("/api/v1/users/" + fatherId + "/family");
        assertThat(deletedAnchorFamily.status()).isEqualTo(200);
        assertThat(ids(deletedAnchorFamily.json())).containsExactly(childId);

        assertThat(delete("/api/v1/users/" + childId).status()).isEqualTo(204);
        ApiResponse emptyFamily = get("/api/v1/users/" + fatherId + "/family");
        assertThat(emptyFamily.status()).isEqualTo(200);
        assertThat(emptyFamily.json()).isEmpty();
    }

    private static long[] ids(JsonNode array) {
        long[] ids = new long[array.size()];
        for (int index = 0; index < array.size(); index++) {
            ids[index] = array.get(index).path("id").asLong();
        }
        return ids;
    }

    private void assertProblem(ApiResponse response, int status, String type) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.header("content-type")).startsWith("application/problem+json");
        assertThat(response.json().path("type").asText()).isEqualTo(type);
        assertThat(response.json().path("status").asInt()).isEqualTo(status);
        assertThat(response.json().path("title").asText()).isNotBlank();
        assertThat(response.json().path("detail").asText()).isNotBlank();
        assertThat(response.json().path("instance").asText()).startsWith("/api/v1/users");
    }

    private static String userBody(
            String firstName,
            String lastName,
            String email,
            String phone,
            String address,
            String fatherId,
            String motherId
    ) {
        StringBuilder json = new StringBuilder()
                .append("{")
                .append("\"firstName\":\"").append(firstName).append("\",")
                .append("\"lastName\":\"").append(lastName).append("\",")
                .append("\"birthDate\":\"1990-01-01\",")
                .append("\"email\":\"").append(email).append("\"");
        if (phone != null) {
            json.append(",\"phoneNumber\":\"").append(phone).append("\"");
        }
        if (address != null) {
            json.append(",\"address\":").append(address);
        }
        if (fatherId != null) {
            json.append(",\"fatherId\":").append(fatherId);
        }
        if (motherId != null) {
            json.append(",\"motherId\":").append(motherId);
        }
        return json.append("}").toString();
    }

    private static String address(
            String country,
            String city,
            String street,
            String building,
            String apartment,
            String postalCode
    ) {
        StringBuilder json = new StringBuilder()
                .append("{")
                .append("\"country\":\"").append(country).append("\",")
                .append("\"city\":\"").append(city).append("\",")
                .append("\"street\":\"").append(street).append("\",")
                .append("\"building\":\"").append(building).append("\"");
        if (apartment != null) {
            json.append(",\"apartment\":\"").append(apartment).append("\"");
        }
        if (postalCode != null) {
            json.append(",\"postalCode\":\"").append(postalCode).append("\"");
        }
        return json.append("}").toString();
    }
}

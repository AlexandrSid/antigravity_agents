package com.linkshortener.resource;

import com.linkshortener.dto.CreateLinkRequest;
import com.linkshortener.dto.LinkResponse;
import com.linkshortener.service.LinkService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class LinkResourceTest {

    @InjectMock
    LinkService linkService;

    @Test
    @DisplayName("Task 1.6: testPostLink_validUrl_returns201WithShortUrl")
    void testPostLink_validUrl_returns201WithShortUrl() {
        LinkResponse mockResponse = new LinkResponse(
            "https://shortner.url/aB3dEfGh",
            "aB3dEfGh",
            "https://example.com/target",
            Instant.parse("2026-10-20T13:00:00Z"),
            Instant.parse("2026-10-21T13:00:00Z")
        );
        when(linkService.createShortLink(any(CreateLinkRequest.class))).thenReturn(mockResponse);

        given()
            .contentType(ContentType.JSON)
            .body(new CreateLinkRequest("https://example.com/target"))
        .when()
            .post("/api/v1/links")
        .then()
            .statusCode(201)
            .body("shortUrl", equalTo("https://shortner.url/aB3dEfGh"))
            .body("shortKey", equalTo("aB3dEfGh"))
            .body("originalUrl", equalTo("https://example.com/target"))
            .body("createdAt", notNullValue())
            .body("expiresAt", notNullValue());
    }

    @Test
    @DisplayName("Task 1.6: testPostLink_blankUrl_returns400ProblemDetails")
    void testPostLink_blankUrl_returns400ProblemDetails() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"url\":\"   \"}")
        .when()
            .post("/api/v1/links")
        .then()
            .statusCode(400);
    }

    @Test
    @DisplayName("Task 1.6: testPostLink_urlExceeding2048Chars_returns400ProblemDetails")
    void testPostLink_urlExceeding2048Chars_returns400ProblemDetails() {
        String longUrl = "https://example.com/" + "a".repeat(2050);
        given()
            .contentType(ContentType.JSON)
            .body(new CreateLinkRequest(longUrl))
        .when()
            .post("/api/v1/links")
        .then()
            .statusCode(400);
    }
}

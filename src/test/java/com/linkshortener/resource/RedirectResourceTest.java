package com.linkshortener.resource;

import com.linkshortener.exception.LinkNotFoundException;
import com.linkshortener.service.LinkService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.when;

@QuarkusTest
class RedirectResourceTest {

    @InjectMock
    LinkService linkService;

    @Test
    @DisplayName("Task 1.6: testGetShortKey_existingActive_returns302WithLocationHeader")
    void testGetShortKey_existingActive_returns302WithLocationHeader() {
        when(linkService.resolveOriginalUrl("aB3dEfGh")).thenReturn("https://example.com/original-target");

        given()
            .redirects().follow(false)
        .when()
            .get("/aB3dEfGh")
        .then()
            .statusCode(302)
            .header("Location", equalTo("https://example.com/original-target"));
    }

    @Test
    @DisplayName("Task 1.6: testGetShortKey_nonExistingOrExpired_returns404ProblemDetails")
    void testGetShortKey_nonExistingOrExpired_returns404ProblemDetails() {
        when(linkService.resolveOriginalUrl("nonexist")).thenThrow(new LinkNotFoundException("Link not found: nonexist"));

        given()
            .redirects().follow(false)
        .when()
            .get("/nonexist")
        .then()
            .statusCode(404);
    }
}

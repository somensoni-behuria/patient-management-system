package com.pm.integration;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end black-box tests driven entirely through the API gateway.
 *
 * <p>Requires the full stack to be running (docker compose up). Configure the gateway base URL
 * with the GATEWAY_URL env var (defaults to http://localhost:4004). Run with:
 * {@code mvn verify -pl integration-tests -Pintegration}.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PatientManagementIT {

    private static String baseUrl;
    private static String token;

    @BeforeAll
    static void setup() {
        baseUrl = System.getenv().getOrDefault("GATEWAY_URL", "http://localhost:4004");
    }

    @Test
    @Order(1)
    void login_returnsJwt() {
        String email = System.getenv().getOrDefault("AUTH_SEED_EMAIL", "testuser@test.com");
        String password = System.getenv().getOrDefault("AUTH_SEED_PASSWORD", "password123");

        Response response = given()
                .baseUri(baseUrl)
                .contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract().response();

        token = response.jsonPath().getString("token");
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(2)
    void createPatient_thenFetchIt() {
        String uniqueEmail = "it-" + UUID.randomUUID() + "@example.com";
        String body = "{"
                + "\"name\":\"Integration Test\","
                + "\"email\":\"" + uniqueEmail + "\","
                + "\"address\":\"1 Test Way\","
                + "\"dateOfBirth\":\"1991-01-01\","
                + "\"registeredDate\":\"2025-01-01\""
                + "}";

        String id = given()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/patients")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("email", org.hamcrest.Matchers.equalTo(uniqueEmail))
                .extract().jsonPath().getString("id");

        // The created patient should be retrievable.
        given()
                .baseUri(baseUrl)
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/patients/" + id)
                .then()
                .statusCode(200)
                .body("id", org.hamcrest.Matchers.equalTo(id));
    }

    @Test
    @Order(3)
    void protectedRoute_rejectsMissingToken() {
        given()
                .baseUri(baseUrl)
                .when()
                .get("/api/patients")
                .then()
                .statusCode(401);
    }
}

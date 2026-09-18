/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.testsetup;

import org.junit.jupiter.api.BeforeEach;

import cloud.katta.client.ApiClient;

import static io.restassured.RestAssured.given;

public class AbstractAdminCLIIT {
    protected String accessToken;
    protected ApiClient apiClient;

    @BeforeEach
    protected void setup() throws Exception {
        // service account of client cryptomatorhub-system with realm role admin, as the realm of katta-compose does not enable direct access grants
        accessToken = given()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .formParam("client_id", "cryptomatorhub-system")
                .formParam("client_secret", "top-secret")
                .formParam("grant_type", "client_credentials")
                .when()
                .post("http://localhost:8380/realms/cryptomator/protocol/openid-connect/token")
                .then()
                .statusCode(200)
                .extract().path("access_token");
        apiClient = new ApiClient();
        apiClient.addDefaultHeader("Authorization", "Bearer " + accessToken);
        apiClient.setBasePath("http://localhost:8280");
    }
}

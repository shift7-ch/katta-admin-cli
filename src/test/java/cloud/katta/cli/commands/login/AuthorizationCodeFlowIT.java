/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli.commands.login;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import cloud.katta.cli.Katta;
import cloud.katta.client.api.StorageProfileResourceApi;
import cloud.katta.testsetup.AbstractAdminCLIIT;
import cloud.katta.testsetup.CLIIntegrationTest;

import static org.junit.jupiter.api.Assertions.*;

@CLIIntegrationTest
class AuthorizationCodeFlowIT extends AbstractAdminCLIIT {

    @Test
    public void testClientCredentialsGrant() throws Exception {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final PrintStream stdout = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        final int rc;
        try {
            rc = Katta.commandLine().execute(
                    "accesstoken",
                    "--tokenUrl", "http://localhost:8380/realms/cryptomator/protocol/openid-connect/token",
                    "--clientId", "cryptomatorhub-system",
                    "--clientSecret", "top-secret"
            );
        }
        finally {
            System.setOut(stdout);
        }
        assertEquals(0, rc);
        final String token = out.toString(StandardCharsets.UTF_8).trim();
        assertFalse(token.isEmpty());
        assertFalse(token.contains("\n"));
        // token is accepted by hub with admin role
        apiClient.addDefaultHeader("Authorization", "Bearer " + token);
        new StorageProfileResourceApi(apiClient).apiStorageprofileGet(null);
    }

    @Test
    public void testClientCredentialsGrantInvalidSecret() {
        final int rc = Katta.commandLine().execute(
                "accesstoken",
                "--tokenUrl", "http://localhost:8380/realms/cryptomator/protocol/openid-connect/token",
                "--clientId", "cryptomatorhub-system",
                "--clientSecret", "wrong-secret"
        );
        assertNotEquals(0, rc);
    }

    @Test
    public void testMissingAuthUrlAndClientSecret() {
        final int rc = Katta.commandLine().execute(
                "accesstoken",
                "--tokenUrl", "http://localhost:8380/realms/cryptomator/protocol/openid-connect/token",
                "--clientId", "cryptomator"
        );
        assertEquals(2, rc);
    }
}

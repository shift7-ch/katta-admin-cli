/*
 * Copyright (c) 2025 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli.commands.login;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coffeelibs.tinyoauth2client.TinyOAuth2;
import io.github.coffeelibs.tinyoauth2client.TinyOAuth2Client;
import picocli.CommandLine;


/**
 * Prints an access token obtained with the authorization code flow or, if a client secret is given, with the client credentials grant.
 * <p>
 * Based on <a href="https://github.com/cryptomator/hub-cli/commit/bffcf2805530976c4a758990958ff75f9df68c0e#diff-c349f933a7698e31cfe25bd0a638ae487a02ac6fcb429bcce3e315aa8832be8b">hub-cli</a>.
 */
@CommandLine.Command(name = "accesstoken", description = "Get access token using authorization code flow or, with --clientSecret, client credentials grant.", mixinStandardHelpOptions = true)
public class AuthorizationCodeFlow implements Callable<Integer> {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--tokenUrl"}, description = "Keycloak realm URL with scheme. Example: \"https://keycloak.default.katta.cloud/realms/cryptomator/protocol/openid-connect/token\"", required = true)
    String tokenUrl;

    @CommandLine.Option(names = {"--authUrl"}, description = "Keycloak realm URL with scheme. Required unless --clientSecret is provided. Example: \"https://keycloak.default.katta.cloud/realms/cryptomator/protocol/openid-connect/auth\"", required = false)
    String authUrl;

    @CommandLine.Option(names = {"--clientId"}, description = "Keycloak realm URL with scheme. Example: \"cryptomator\"", required = true)
    String clientId;

    @CommandLine.Option(names = {"--clientSecret"}, description = "Client secret to use client credentials grant instead of authorization code flow. Example: service account of client \"cryptomatorhub-system\" with realm role admin.", required = false)
    String clientSecret;

    @Override
    public Integer call() throws Exception {
        final TinyOAuth2Client client = TinyOAuth2.client(clientId).withTokenEndpoint(URI.create(tokenUrl));
        final HttpResponse<String> authResponse;
        if(null == clientSecret) {
            if(null == authUrl) {
                throw new CommandLine.ParameterException(spec.commandLine(), "Missing required option: --authUrl or --clientSecret");
            }
            authResponse = client
                    .authorizationCodeGrant(URI.create(authUrl))
                    .authorize(HttpClient.newHttpClient(), uri -> System.err.println("Please login on " + uri));
        }
        else {
            authResponse = client
                    .clientCredentialsGrant(StandardCharsets.UTF_8, clientSecret)
                    .authorize(HttpClient.newHttpClient());
        }
        return printAccessToken(authResponse);
    }

    private int printAccessToken(HttpResponse<String> response) throws JsonProcessingException {
        var statusCode = response.statusCode();
        if(statusCode != 200) {
            System.err.println("""
                    Request was responded with code %d and body:
                    %s
                    """.formatted(statusCode, response.body()));
            return statusCode;
        }
        var token = new ObjectMapper().reader().readTree(response.body()).get("access_token").asText();
        System.out.println(token);
        return 0;
    }
}

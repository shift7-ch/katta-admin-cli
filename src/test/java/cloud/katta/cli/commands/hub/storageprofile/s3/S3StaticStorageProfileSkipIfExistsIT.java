/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli.commands.hub.storageprofile.s3;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import cloud.katta.cli.Katta;
import cloud.katta.client.api.StorageProfileResourceApi;
import cloud.katta.client.model.StorageProfileDto;
import cloud.katta.model.StorageProfileDtoWrapper;
import cloud.katta.testsetup.AbstractAdminCLIIT;
import cloud.katta.testsetup.CLIIntegrationTest;

import static org.junit.jupiter.api.Assertions.*;

@CLIIntegrationTest
class S3StaticStorageProfileSkipIfExistsIT extends AbstractAdminCLIIT {

    @Test
    public void testSkipIfExists() throws Exception {
        final String profileName = "S3 Static Skip - " + UUID.randomUUID();
        assertEquals(0, this.upload(profileName, false));
        final UUID id = this.find(profileName);

        // second upload with --skipIfExists keeps the storage profile of the first upload
        assertEquals(0, this.upload(profileName, true));
        assertEquals(id, this.find(profileName));

        // without --skipIfExists the storage profile is uploaded again
        assertEquals(0, this.upload(profileName, false));
        assertEquals(2, this.count(profileName));
    }

    private int upload(final String profileName, final boolean skipIfExists) {
        return Katta.commandLine().execute(skipIfExists
                ? new String[]{"storageprofile", "s3", "static",
                "--hubUrl", "http://localhost:8280", "--accessToken", accessToken,
                "--name", profileName, "--endpointUrl", "https://s3.example.com", "--region", "us-east-1", "--skipIfExists"}
                : new String[]{"storageprofile", "s3", "static",
                "--hubUrl", "http://localhost:8280", "--accessToken", accessToken,
                "--name", profileName, "--endpointUrl", "https://s3.example.com", "--region", "us-east-1"});
    }

    private List<StorageProfileDto> profiles(final String profileName) throws Exception {
        return new StorageProfileResourceApi(apiClient).apiStorageprofileGet(null).stream()
                .filter(p -> profileName.equals(StorageProfileDtoWrapper.coerce(p).getName()))
                .toList();
    }

    private UUID find(final String profileName) throws Exception {
        final List<StorageProfileDto> profiles = this.profiles(profileName);
        assertEquals(1, profiles.size());
        return StorageProfileDtoWrapper.coerce(profiles.getFirst()).getId();
    }

    private long count(final String profileName) throws Exception {
        return this.profiles(profileName).size();
    }
}

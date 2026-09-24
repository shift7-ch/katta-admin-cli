/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli.commands.hub.storageprofile;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import cloud.katta.cli.commands.AbstractAuthorizationCode;
import cloud.katta.client.ApiClient;
import cloud.katta.client.ApiException;
import cloud.katta.client.JSON;
import cloud.katta.client.api.StorageProfileResourceApi;
import cloud.katta.client.model.StorageProfileDto;
import cloud.katta.model.StorageProfileDtoWrapper;
import picocli.CommandLine;

public abstract class AbstractStorageProfile extends AbstractAuthorizationCode implements Callable<Void> {

    @CommandLine.Option(names = {"--name"}, description = "The name.", required = false)
    protected String name;

    @CommandLine.Option(names = {"--region"}, description = "Default Bucket region, e.g. \"eu-west-1\".", required = true)
    protected String region;

    @CommandLine.Option(names = {"--regions"}, description = "Bucket regions, e.g. \"--regions eu-west-1  --regions eu-west-2 --regions eu-west-3\".", required = false)
    protected List<String> regions;

    @CommandLine.Option(names = {"--skipIfExists"}, description = "Do not upload when a storage profile with the same name already exists, archived or not. "
            + "Prints the existing storage profile instead. Note that no attempt is made to update it.", defaultValue = "false")
    protected boolean skipIfExists;

    @CommandLine.Option(names = {"--debug"}, description = "Print HTTP request and response headers.", defaultValue = "false")
    protected boolean debug;

    public AbstractStorageProfile() {
    }

    public AbstractStorageProfile(final String hubUrl, final String name, final String region, final List<String> regions) {
        this.hubUrl = hubUrl;
        this.name = name;
        this.region = region;
        this.regions = regions;
    }

    @Override
    public Void call() throws Exception {
        final ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(hubUrl);
        apiClient.addDefaultHeader("Authorization", "Bearer %s".formatted(this.login()));
        apiClient.setDebugging(debug);
        final StorageProfileResourceApi storageProfileResourceApi = new StorageProfileResourceApi(apiClient);
        final StorageProfileDto response;
        final Optional<StorageProfileDto> existing = skipIfExists ? this.find(storageProfileResourceApi) : Optional.empty();
        if(existing.isPresent()) {
            System.err.printf("Storage profile %s already exists.%n", this.name());
            response = existing.get();
        }
        else {
            response = this.call(storageProfileResourceApi);
        }
        System.out.println(new JSON().getContext(null).writeValueAsString(response));
        return null;
    }

    /**
     * @return The first storage profile with the same name, archived or not. The server assigns the id on creation, therefore
     * the name is the only stable handle to recognize a previously uploaded storage profile.
     */
    private Optional<StorageProfileDto> find(final StorageProfileResourceApi storageProfileResourceApi) throws ApiException {
        return storageProfileResourceApi.apiStorageprofileGet(null).stream()
                .filter(profile -> this.name().equals(StorageProfileDtoWrapper.coerce(profile).getName()))
                .findFirst();
    }

    /**
     * @return The name from <code>--name</code> or the description of the storage profile if not set.
     */
    protected String name() {
        return null == name ? this.toString() : name;
    }

    protected abstract StorageProfileDto call(final StorageProfileResourceApi storageProfileResourceApi) throws ApiException;
}

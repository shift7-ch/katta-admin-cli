/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.cli.commands.storage.aws;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.policybuilder.iam.IamCondition;
import software.amazon.awssdk.policybuilder.iam.IamEffect;
import software.amazon.awssdk.policybuilder.iam.IamPolicy;
import software.amazon.awssdk.policybuilder.iam.IamPolicyWriter;
import software.amazon.awssdk.policybuilder.iam.IamPrincipalType;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.CreateOpenIdConnectProviderRequest;
import software.amazon.awssdk.services.iam.model.CreateOpenIdConnectProviderResponse;
import software.amazon.awssdk.services.iam.model.CreateRoleRequest;
import software.amazon.awssdk.services.iam.model.DeleteOpenIdConnectProviderRequest;
import software.amazon.awssdk.services.iam.model.GetOpenIdConnectProviderRequest;
import software.amazon.awssdk.services.iam.model.GetOpenIdConnectProviderResponse;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.ListOpenIdConnectProvidersResponse;
import software.amazon.awssdk.services.iam.model.NoSuchEntityException;
import software.amazon.awssdk.services.iam.model.OpenIDConnectProviderListEntry;
import software.amazon.awssdk.services.iam.model.PutRolePolicyRequest;
import software.amazon.awssdk.services.iam.model.UpdateAssumeRolePolicyRequest;

import static cloud.katta.cli.commands.common.Defaults.*;

/**
 * Sets up AWS for Katta in STS mode:
 * <ul>
 *  <li>creates/updates OIDC provider for cryptomator, cryptomatorhub and cryptomatorvaults clients.</li>
 *  <li>creates roles and role policy for
 *      <ul>
 *          <li>creating vaults: access restricted to creating buckets with given prefix</li>
 *          <li>accessing vaults using <a href="role chaining">https://docs.aws.amazon.com/IAM/latest/UserGuide/id_session-tags.html</a>: access restricted to reading/writing to single bucket.</li>
 *      </ul>
 *  </li>
 * </ul>
 * <p>
 * See also: <a href="https://github.com/shift7-ch/katta-docs/blob/main/SETUP_KATTA_SERVER.md#setup-aws">Katta Docs</a>.
 */
@CommandLine.Command(name = "aws",
        description = "Setup/update OIDC provider and roles for STS in AWS.",
        showDefaultValues = true,
        mixinStandardHelpOptions = true)
public class AWSSTSStorage implements Callable<Void> {

    // TODO get from /api/config instead/optionally?
    @CommandLine.Option(names = {"--realmUrl"}, description = "Keycloak realm URL with scheme. Example: \"https://keycloak.testing.katta.cloud/realms/cryptomator\".", required = true)
    String realmUrl;

    @CommandLine.Option(names = {"--roleNamePrefix"}, description = "IAM ARN role name prefix (not a full ARN; do not include 'arn:aws:iam::...:role", required = false, defaultValue = "katta-")
    String roleNamePrefix;

    @CommandLine.Option(names = {"--profileName"}, description = "AWS profile to load AWS credentials from. See ~/.aws/credentials.", required = false, defaultValue = "default")
    String profileName;

    @CommandLine.Option(names = {"--bucketPrefix"}, description = "Bucket Prefix for STS vaults. E.g. \"katta-\".", required = false, defaultValue = "katta-")
    String bucketPrefix;

    @CommandLine.Option(names = {"--maxSessionDuration"}, description = "Session duration for STS tokens in seconds.", required = false)
    Integer maxSessionDuration;

    // TODO can from /api/config instead/optionally?
    @CommandLine.Option(names = {"--clientId"}, description = "ClientIds for the OIDC provider.", required = false)
    List<String> clientId;

    int sleep = 10000;

    @Override
    public Void call() throws Exception {
        if(null == clientId) {
            clientId = List.of(CLIENT_IDS);
        }
        // remove trailing slash
        realmUrl = realmUrl.replaceAll("/$", "");
        final URI uri = new URI(realmUrl);
        if(!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new CommandLine.ParameterException(new CommandLine(this), String.format("--realmUrl must use https: %s", realmUrl));
        }
        final String arnPostfix = realmUrl.substring(uri.getScheme().length() + "://".length());

        try (final IamClient iam = IamClient.builder()
                .region(Region.AWS_GLOBAL)
                .credentialsProvider(DefaultCredentialsProvider.builder()
                        .profileName(profileName).build())
                .build()) {
            call(iam, arnPostfix);
        }
        return null;
    }

    protected void call(final IamClient iam, final String arnPostfix) throws InterruptedException {
        final ListOpenIdConnectProvidersResponse existingOpenIdConnectProviders = iam.listOpenIDConnectProviders();
        System.out.println(existingOpenIdConnectProviders);

        final Optional<OpenIDConnectProviderListEntry> existingOIDCProvider = existingOpenIdConnectProviders.openIDConnectProviderList().stream().filter(idp -> idp.arn().endsWith(arnPostfix)).findFirst();
        final String oidcProviderArn;
        if(existingOIDCProvider.isPresent()) {
            final GetOpenIdConnectProviderResponse response = iam.getOpenIDConnectProvider(GetOpenIdConnectProviderRequest.builder()
                    .openIDConnectProviderArn(existingOIDCProvider.get().arn()).build());
            if(response.hasClientIDList() && !response.clientIDList().containsAll(clientId)) {
                iam.deleteOpenIDConnectProvider(DeleteOpenIdConnectProviderRequest.builder()
                        .openIDConnectProviderArn(existingOIDCProvider.get().arn()).build());
                oidcProviderArn = iam.createOpenIDConnectProvider(CreateOpenIdConnectProviderRequest.builder()
                        .url(realmUrl)
                        .clientIDList(clientId)
                        .build()).openIDConnectProviderArn();
            }
            else {
                oidcProviderArn = existingOIDCProvider.get().arn();
            }
        }
        else {
            final CreateOpenIdConnectProviderResponse response = iam.createOpenIDConnectProvider(CreateOpenIdConnectProviderRequest.builder()
                    .url(realmUrl)
                    .clientIDList(clientId)
                    .build());
            oidcProviderArn = response.openIDConnectProviderArn();
        }
        System.out.println(oidcProviderArn);
        final String arnPrefix = oidcProviderArn.replace(":oidc-provider" + "/" + arnPostfix, "");

        //
        //		aws iam create-role --role-name katta-create-bucket --assume-role-policy-document aws_stscreatebuckettrustpolicy.json
        //		aws iam put-role-policy --role-name katta-create-bucket --policy-name katta-create-bucket --policy-document aws_stscreatebucketpermissionpolicy.json
        //
        final String awsSTSCreateBucketRoleName = String.format("%s%s", roleNamePrefix, CREATE_BUCKET_ROLE_NAME_INFIX);
        final IamPolicy awsSTSCreateBucketTrustPolicy = IamPolicy.builder()
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("sts:AssumeRoleWithWebIdentity")
                        .addPrincipal(IamPrincipalType.FEDERATED, oidcProviderArn)
                )
                .build();
        final IamPolicy awsSTSCreateBucketPermissionPolicy = IamPolicy.builder()
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("s3:CreateBucket")
                        .addAction("s3:GetBucketPolicy")
                        .addAction("s3:PutBucketVersioning")
                        .addAction("s3:GetBucketVersioning")
                        .addAction("s3:GetAccelerateConfiguration")
                        .addAction("s3:PutAccelerateConfiguration")
                        .addAction("s3:GetEncryptionConfiguration")
                        .addAction("s3:PutEncryptionConfiguration")
                        .addResource(String.format("arn:aws:s3:::%s*", bucketPrefix)))
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("s3:PutObject")
                        .addResource(String.format("arn:aws:s3:::%s*/*/", bucketPrefix))
                        .addResource(String.format("arn:aws:s3:::%s*/*.uvf", bucketPrefix)))
                .build();
        uploadAssumeRolePolicyAndPermissionPolicy(iam,
                awsSTSCreateBucketRoleName,
                awsSTSCreateBucketTrustPolicy.toJson(IamPolicyWriter.builder().prettyPrint(true).build()),
                awsSTSCreateBucketPermissionPolicy.toJson(IamPolicyWriter.builder().prettyPrint(true).build()),
                maxSessionDuration
        );

        //
        //		aws iam create-role --role-name katta-access-bucket-web-identity-role --assume-role-policy-document aws_stskatta-access-bucket-web-identity-role_trustpolicy.json
        //		aws iam put-role-policy --role-name katta-access-bucket-web-identity-role --policy-name katta-access-bucket-web-identity-role --policy-document aws_stskatta-access-bucket-web-identity-role_permissionpolicy.json
        //
        final String assumeRoleWithWebIdentityRoleName = String.format("%s%s%s", roleNamePrefix, ACCESS_BUCKET_ROLE_NAME_INFIX, ASSUME_ROLE_WITH_WEB_IDENTITY_ROLE_SUFFIX);
        final String assumeRoleTaggedSessionRoleName = String.format("%s%s%s", roleNamePrefix, ACCESS_BUCKET_ROLE_NAME_INFIX, ASSUME_ROLE_TAGGED_SESSION_ROLE_SUFFIX);

        final IamPolicy accessBucketAssumeRoleWithWebIdentityTrustPolicy = IamPolicy.builder()
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("sts:AssumeRoleWithWebIdentity")
                        .addAction("sts:TagSession")
                        .addPrincipal(IamPrincipalType.FEDERATED, oidcProviderArn)
                )
                .build();
        final IamPolicy accessBucketAssumeRoleWithWebIdentityPermissionPolicy = IamPolicy.builder()
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("sts:AssumeRole")
                        .addAction("sts:TagSession")
                        .addResource(String.format("%s:role/%s", arnPrefix, assumeRoleTaggedSessionRoleName)))
                .build();
        uploadAssumeRolePolicyAndPermissionPolicy(iam,
                assumeRoleWithWebIdentityRoleName,
                accessBucketAssumeRoleWithWebIdentityTrustPolicy.toJson(IamPolicyWriter.builder().prettyPrint(true).build()),
                accessBucketAssumeRoleWithWebIdentityPermissionPolicy.toJson(IamPolicyWriter.builder().prettyPrint(true).build()),
                maxSessionDuration
        );

        //
        //		sleep 10;
        //
        System.out.printf("Wait %ss%n", sleep / 1000);
        Thread.sleep(sleep);

        //
        //		aws iam create-role --role-name katta-access-bucket-tagged-session-role --assume-role-policy-document aws_stskatta-access-bucket-tagged-session-role_trustpolicy.json
        //		aws iam put-role-policy --role-name katta-access-bucket-tagged-session-role --policy-name katta-access-bucket-tagged-session-role --policy-document aws_stskatta-access-bucket-tagged-session-role_permissionpolicy.json
        //
        final GetRoleResponse role = iam.getRole(GetRoleRequest.builder().roleName(assumeRoleWithWebIdentityRoleName).build());
        final IamPolicy accessBucketTaggedSessionRoleTrustPolicy = IamPolicy.builder()
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("sts:AssumeRole")
                        .addAction("sts:TagSession")
                        .addPrincipal(IamPrincipalType.AWS, role.role().arn())
                        .addCondition(IamCondition.builder()
                                .operator("ForAnyValue:StringEquals")
                                .key("sts:TransitiveTagKeys")
                                .value("${aws:RequestTag/" + REQUEST_TAG + "}")
                                .build())
                )
                .build();
        final IamPolicy accessBucketTaggedSessionRolePermissionPolicy = IamPolicy.builder()
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("s3:GetBucketLocation")
                        .addAction("s3:ListBucket")
                        .addAction("s3:ListBucketMultipartUploads")
                        .addAction("s3:GetBucketVersioning")
                        .addAction("s3:ListBucketVersions")
                        .addResource(String.format("arn:aws:s3:::%s${aws:PrincipalTag/Vault}", bucketPrefix)))
                .addStatement(b -> b
                        .effect(IamEffect.ALLOW)
                        .addAction("s3:GetObject")
                        .addAction("s3:PutObject")
                        .addAction("s3:DeleteObject")
                        .addAction("s3:ListMultipartUploadParts")
                        .addAction("s3:AbortMultipartUpload")
                        .addResource(String.format("arn:aws:s3:::%s${aws:PrincipalTag/Vault}/*", bucketPrefix)))
                .build();
        uploadAssumeRolePolicyAndPermissionPolicy(iam,
                assumeRoleTaggedSessionRoleName,
                accessBucketTaggedSessionRoleTrustPolicy.toJson(IamPolicyWriter.builder().prettyPrint(true).build()),
                accessBucketTaggedSessionRolePermissionPolicy.toJson(IamPolicyWriter.builder().prettyPrint(true).build()),
                maxSessionDuration
        );
    }

    private void uploadAssumeRolePolicyAndPermissionPolicy(final IamClient iam, final String roleName, final String trustPolicy, final String permissionPolicy, final Integer maxSessionDuration) {
        try {
            iam.getRole(GetRoleRequest.builder().roleName(roleName).build());
            System.out.println(String.format("aws iam update-role --role-name %s --assume-role-policy-document file://...", roleName));
            System.out.println(trustPolicy);
            iam.updateAssumeRolePolicy(UpdateAssumeRolePolicyRequest.builder()
                    .roleName(roleName)
                    .policyDocument(trustPolicy)
                    .build());
        }
        catch(NoSuchEntityException e) {
            System.out.println(String.format("aws iam create-role --role-name %s --assume-role-policy-document file://...", roleName));
            System.out.println(trustPolicy);
            iam.createRole(CreateRoleRequest.builder()
                    .roleName(roleName)
                    .assumeRolePolicyDocument(trustPolicy)
                    .maxSessionDuration(maxSessionDuration)
                    .build()
            );

        }
        System.out.println(String.format("aws iam put-role-policy --role-name %s --policy-name %s --policy-document file://...", roleName, roleName));
        System.out.println(permissionPolicy);
        iam.putRolePolicy(PutRolePolicyRequest.builder()
                .roleName(roleName)
                .policyName(roleName)
                .policyDocument(permissionPolicy)
                .build());
    }
}

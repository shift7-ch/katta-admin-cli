/*
 * Copyright (c) 2026 shift7 GmbH. All rights reserved.
 */

package cloud.katta.examples;

import cloud.katta.cli.Katta;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Example to use with
 * <code>docker compose -f src/test/resources/compose.yaml up --wait</code>
 * (deploy/compose/local of katta-server).
 */
public class Local {
    public static void main(String[] args) {
        final String bucketPrefix = "katta-test-";
        final String realmUrl = String.format("http://localhost:8380/realms/cryptomator");
        final String hubUrl = String.format("http://localhost:8280");
        final String region = "eu-central-1";
        if(true) {
            final String[] options = {
                    "storageprofile", "minio", "sts",
                    "--hubUrl", hubUrl,
                    "--name", "MinIO S3 STS",
                    "--bucketPrefix", bucketPrefix,
                    "--endpointUrl", "http://localhost:9100",
                    "--stsRoleAccessBucket", "arn:minio:iam:::role/Hdms6XDZ6oOpuWYI3gu4gmgHN94", // cryptomatorvaults
                    "--stsRoleCreateBucketClient", "arn:minio:iam:::role/IqZpDC5ahW_DCAvZPZA4ACjEnDE", // cryptomator (Desktop)
                    "--stsRoleCreateBucketHub", "arn:minio:iam:::role/HGKdlY4eFFsXVvJmwlMYMhmbnDE", // cryptomatorhub (Web)
                    "--region", region,
                    "--regions", region
            };
            System.out.println(String.format("katta \"%s\"", String.join("\" \"", options)));
            int rc = new CommandLine(new Katta()).execute(options);
            assertEquals(0, rc);
        }
        if(false) {
            final String[] options = {
                    "storageprofile", "s3", "static",
                    "--hubUrl", hubUrl,
                    "--name", "MinIO S3 Static",
                    "--endpointUrl", "http://localhost:9100",
                    "--region", "eu-west-1",
                    "--regions", "eu-west-1",
                    "--regions", "eu-west-2",
                    "--regions", "eu-west-3"};
            System.out.println(String.format("katta \"%s\"", String.join("\" \"", options)));
            int rc = new CommandLine(new Katta()).execute(options);
            assertEquals(0, rc);
        }
    }
}

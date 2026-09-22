# Katta Admin CLI

[![Maven Build](https://github.com/shift7-ch/katta-admin-cli/actions/workflows/build.yml/badge.svg)](https://github.com/shift7-ch/katta-admin-cli/actions/workflows/build.yml)

> [Katta](https://katta.cloud/): transform your S3 storage into a secure, team-friendly workspace with client-side encryption.

This CLI program is used to configure a [Katta Server](https://github.com/shift7-ch/katta-server) including its S3 storage backend. Supported storage backend configurations are:

- AWS S3 accessed using static access keys
- AWS S3 accessed using AWS Security Token Service (STS) issuing temporary access keys from OIDC access token obtained by user from Keycloak identity provider.
- Generic S3-compatible provider accessed using static access credentials.
- MinIO accessed using Security Token Service (STS) with OIDC.

## Build the native image

The `katta` Admin CLI is distributed as a self-contained native executable built with GraalVM `native-image` through the `native` Maven profile.

**Prerequisites:**

- GraalVM for JDK 25 (or newer) with the `native-image` tool on the `PATH`, e.g. via [`graalvm/setup-graalvm`](https://github.com/graalvm/setup-graalvm)
  or [SDKMAN!](https://sdkman.io/).
- On Linux x86_64 the executable is linked statically against musl (`--static --libc=musl`), which requires `musl-dev` / `musl-tools` and a musl-linked
  static `libz.a` — see [`.github/workflows/release.yml`](.github/workflows/release.yml) for the exact setup. GraalVM does not support musl on Linux
  aarch64, where the executable is linked statically except for glibc (`--static-nolibc`). macOS builds are dynamically linked and need no extra tooling.

```bash
mvn verify -Pnative
```

The native image build runs the integration tests with the native-image agent to collect reachability metadata and therefore requires Docker.
Add `-Prelease` to build with `-O3` instead of the default `-Ob` (faster runtime, slower build). The resulting executable is written to
`target/katta`:

```bash
target/katta --help
```

## Tests

The Katta Server API client is used from [`katta-clientlib-hub`](https://github.com/shift7-ch/katta-clientlib), resolved from the shift7 Maven
repository in the version set with the `katta-clientlib.version` property.

Run unit tests only:

```bash
mvn verify -DskipITs
```

Integration tests are tagged with `cli` and start Katta Server, Keycloak and MinIO with the Docker Compose environment of
[katta-compose](https://github.com/shift7-ch/katta-compose) included with its Git URL in
[`compose.yaml`](src/test/resources/compose.yaml), using its default variables, Keycloak realm and setup files. Docker Compose fetches the
referenced commit on first use. To run integration tests with a local checkout of katta-compose instead, replace the Git URL with the absolute
path to `compose.yaml` in the checkout.

```bash
mvn verify
```

## Installation

Every tagged release publishes native executables and packages as
[GitHub Release assets](https://github.com/shift7-ch/katta-admin-cli/releases).

### macOS (Homebrew)

```bash
brew tap shift7-ch/katta
brew trust shift7-ch/katta
brew install katta
```

Upgrade with `brew upgrade katta`. Requires Apple Silicon (arm64).

### Linux (Debian/Ubuntu)

```bash
curl -fsSLO https://github.com/shift7-ch/katta-admin-cli/releases/latest/download/katta_$(dpkg --print-architecture).deb
sudo apt install ./katta_$(dpkg --print-architecture).deb
```

### Linux (Fedora/RHEL/openSUSE)

```bash
sudo rpm -i https://github.com/shift7-ch/katta-admin-cli/releases/latest/download/katta.$(uname -m).rpm
```

The `.deb` and `.rpm` packages install `katta` to `/usr/bin/katta` and a bash
completion script to `/usr/share/bash-completion/completions/katta`. They are
built for x86_64/amd64 and aarch64/arm64. The executables are also published as
`katta-linux-amd64` (statically linked) and `katta-linux-arm64` (requires glibc 2.34 or newer).

### Setup AWS using OIDC Provider and Security Token Service (STS) with `setup` command

Set up AWS as a storage backend for Katta Server. Configures identity provider and roles in IAM to restrict access to S3 buckets to users authenticated by
Keycloak.

```bash
katta setup aws \
  --realmUrl <realm-url>
```

**Required Options:**

- `--realmUrl`: Keycloak realm URL with scheme. Example: `https://keycloak.default.domain/realms/cryptomator`

**Additional Options:**

- `--profileName`: AWS profile to load AWS credentials from (see `~/.aws/credentials`)
- `--clientId`: Client Ids for the OIDC provider
- `--roleNamePrefix`: Prefix used for IAM role names. Defaults to `katta-`.
- `--bucketPrefix`: Prefix used when creating buckets for this storage profile. Defaults to `katta-`.

### Configure storage profile in AWS Setup using `storageprofile` command

Uploads a storage profile to Katta Server for use with AWS S3.
Requires [Setup AWS using OIDC Provider and Security Token Service (STS)](#setup-aws-using-oidc-provider-and-security-token-service-sts-with-setup-command).

```bash
katta storageprofile aws sts \
  --hubUrl <hub-url> \
  --awsAccountId <aws-account-id> \
  --region <aws-region>
```

**Required Options:**

- `--hubUrl`: Hub URL. Example: `https://hub.default.katta.cloud/`. Keycloak auth and token endpoints are fetched automatically from `<hub-url>/api/config`.
- `--awsAccountId`: AWS Account ID. A 12-digit number, such as 012345678901, that uniquely identifies an AWS account.
- `--region`: Bucket region. Example: `eu-west-1`

**Additional Options:**

- `--roleNamePrefix`: Prefix used for IAM role names. Defaults to `katta-`.
- `--bucketPrefix`: Prefix used when creating buckets for this storage profile. Defaults to `katta-`.
- `--authUrl`: Keycloak auth endpoint URL. Overrides the value fetched from `--hubUrl`.
- `--tokenUrl`: Keycloak token endpoint URL. Overrides the value fetched from `--hubUrl`.

### Configure storage profile for a generic S3-compatible provider using `storageprofile` command

Uploads a storage profile to Katta Server for use with any S3-compatible storage provider using static access credentials.
Unlike STS-based profiles, no temporary credentials are issued; the server uses static access key credentials directly.

```bash
katta storageprofile s3 static \
  --hubUrl <hub-url> \
  --endpointUrl <s3-endpoint-url> \
  --region <region>
```

**Required Options:**

- `--hubUrl`: Hub URL. Example: `https://hub.default.katta.cloud/`
- `--endpointUrl`: S3 endpoint URL. Example: `https://s3.example.com` or `https://s3.example.com:9000`
- `--region`: Default bucket region. Example: `us-east-1`

**Additional Options:**

- `--bucketPrefix`: Prefix used when creating buckets for this storage profile. Defaults to `katta-`.
- `--regions`: Additional bucket regions. Example: `--regions us-east-1 --regions us-west-2`
- `--name`: Display name for the storage profile.

### Setup MinIO using OIDC Provider and Security Token Service (STS) with `setup` command

Set up MinIO as a storage backend for Katta Server. Reads the Keycloak URL, realm and client IDs from `<hub-url>/api/config`
and creates (or updates) two policies via the MinIO Admin API:

- a **bucket creation** policy (`--createBucketPolicyName`, default `katta-createbucketpolicy`) allowing `s3:CreateBucket` and
  versioning/policy reads on `arn:aws:s3:::katta-*`, plus `s3:PutObject` for the vault template, restricted to the bucket prefix;
- a **bucket access** policy (`--accessBucketPolicyName`, default `katta-accessbucketpolicy`) granting read/write on
  `arn:aws:s3:::katta-${jwt:client_id}`. MinIO scopes bucket access per vault through the `${jwt:client_id}` policy variable and
  does not support role chaining or tagged sessions.

It is idempotent — re-run it to pick up policy changes.

```bash
katta setup minio \
  --hubUrl <hub-url> \
  --endpointUrl <minio-endpoint-url> \
  --accessKey <minio-access-key> \
  --secretKey <minio-secret-key>
```

**Required Options:**

- `--hubUrl`: Hub URL. Example: `https://hub.default.katta.cloud/`
- `--endpointUrl`: MinIO endpoint URL (S3 API). Example: `http://localhost:9000` or `https://minio.example.com:9000`
- `--accessKey`: Access key of a MinIO admin account.
- `--secretKey`: Secret key of a MinIO admin account.

**Additional Options:**

- `--minioAlias`: MinIO client alias used in the printed `mc` commands. Defaults to `myminio`.
- `--roleNamePrefix`: Prefix for the generated OIDC provider names (`<roleNamePrefix><clientId>`). Defaults to `katta-`.
- `--bucketPrefix`: Prefix used when creating buckets for this storage profile. Defaults to `katta-`.
- `--createBucketPolicyName`: Name of the bucket creation policy. Defaults to `<roleNamePrefix>createbucketpolicy`.
- `--accessBucketPolicyName`: Name of the bucket access policy. Defaults to `<roleNamePrefix>accessbucketpolicy`.

The MinIO Admin API used through `minio-java` cannot configure the OIDC identity provider itself (the `set-config-kv` endpoint
expects an encrypted payload that `minio-java` does not implement, and `minio/minio` is archived read-only since April 2026).
`katta setup minio` therefore does **not** register the providers; instead it prints the `mc alias set`, one
`mc admin config set … identity_openid:<roleNamePrefix><clientId>` per client, and `mc admin service restart` commands for you
to run against the MinIO server. (`mc idp openid add` is only available against MinIO AIStor deployments.)

MinIO prints the generated `RoleARN` for each provider to its server log on restart — pass those to
`katta storageprofile minio sts` below.

### Configure storage profile for MinIO using `storageprofile` command

Uploads a storage profile to Katta Server for use with MinIO STS. Requires MinIO STS setup with an OIDC provider.

Unlike AWS, MinIO does not support role chaining or tagged-session `AssumeRole`, so `stsRoleAccessBucketAssumeRoleTaggedSession`
and `stsSessionTag` are not used for MinIO storage profiles. MinIO uses the `${jwt:client_id}` policy variable to scope bucket
access per vault.

Requires [Setup MinIO using OIDC Provider and Security Token Service (STS)](#setup-minio-using-oidc-provider-and-security-token-service-sts-with-setup-command).

```bash
katta storageprofile minio sts \
  --hubUrl <hub-url> \
  --endpointUrl <minio-endpoint-url> \
  --region <region> \
  --stsRoleCreateBucketClient <role-arn> \
  --stsRoleCreateBucketHub <role-arn> \
  --stsRoleAccessBucket <role-arn>
```

**Required Options:**

- `--hubUrl`: Hub URL. Example: `https://hub.default.katta.cloud/`
- `--endpointUrl`: MinIO endpoint URL (S3 API). Example: `https://minio.example.com` or `https://minio.example.com:9000`
- `--region`: Default bucket region. Example: `us-east-1`
- `--stsRoleCreateBucketClient`: MinIO role ARN for bucket creation by the Cryptomator client (from `mc idp openid ls` or the `RoleARN` MinIO logs on restart
  for the `cryptomator` client).
- `--stsRoleCreateBucketHub`: MinIO role ARN for bucket creation by Cryptomator Hub (from `mc idp openid ls` or the `RoleARN` MinIO logs on restart for the
  `cryptomatorhub` client).
- `--stsRoleAccessBucket`: MinIO role ARN for bucket access (from `mc idp openid ls` or the `RoleARN` MinIO logs on restart for the `cryptomatorvaults` client).

**Additional Options:**

- `--bucketPrefix`: Prefix used when creating buckets for this storage profile. Defaults to `katta-`.
- `--regions`: Additional bucket regions. Example: `--regions us-east-1 --regions us-west-2`
- `--name`: Display name for the storage profile.

### Generate shell completion script with `completion` command

Generate a bash completion script for the `katta` CLI and install it for the current shell session.

```bash
source <(katta completion)
```

To persist completion across sessions, write the script to a file and source it from your shell profile:

```bash
katta completion > ~/.bash_completion.d/katta
echo 'source ~/.bash_completion.d/katta' >> ~/.bashrc
```

**Options:**

- `--shell`: Shell to generate completion for. Only `bash` is supported. Defaults to `bash`.

#### TLS certificates of the Keycloak endpoint

`katta setup aws` does not set certificate thumbprints on the IAM OIDC provider. AWS verifies the TLS certificate of the Keycloak endpoint against
its [library of trusted root CAs](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_create_oidc_verify-thumbprint.html), so no
update is needed when certificates are renewed. If Keycloak uses a certificate not signed by one of these CAs, add the thumbprint to the identity
provider in IAM manually.


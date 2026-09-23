# Container image with the Katta Admin CLI, e.g. to seed storage profiles from a Kubernetes Job.
# Built by .github/workflows/release.yml from the native executables of the release, with the
# build context containing katta-linux-amd64 and katta-linux-arm64.
# Distroless provides the glibc required by the linux-arm64 executable (the amd64 one is static)
# and the CA certificates for HTTPS, and runs as non-root.
FROM gcr.io/distroless/base-debian12:nonroot
ARG TARGETARCH
COPY --chmod=0755 katta-linux-${TARGETARCH} /usr/bin/katta
ENTRYPOINT ["/usr/bin/katta"]

# Build container versions pinned on 04-18-26
FROM messense/cargo-xwin@sha256:f3e680c963eff35f77908c78d97ac095d4bef1f69e4fe200447576b34cd251cb

ARG RUST_VERSION
RUN rustup default ${RUST_VERSION}
RUN rustup target add x86_64-pc-windows-msvc
RUN rustup target add aarch64-pc-windows-msvc
# PORT-26.3: the image digest does not pin what "cargo xwin cache xwin" downloads: it reads Microsoft's current Visual
# Studio manifest at build time. Pin the Windows SDK and the MSVC CRT it unpacks (the manifest itself cannot be pinned
# with this cargo-xwin, 0.21.5; it maps these versions to packages whose hashes xwin checks). On 2026-11-24 the
# manifest's newest SDK, 10.0.26100, failed to unpack ("unable to find x86_64 headers"); these are the versions the
# port's Windows libraries were built with (CRT headers: _VC_CRT 14.44.35220).
ENV XWIN_SDK_VERSION=10.0.22621
ENV XWIN_CRT_VERSION=14.44.17.14
RUN cargo xwin cache xwin

WORKDIR /

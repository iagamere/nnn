FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential cmake ninja-build git curl wget ca-certificates unzip zip xz-utils bzip2 \
    jq pkg-config autoconf automake libtool python3 file sudo patch gawk \
 && rm -rf /var/lib/apt/lists/*

# pacbrew-pacman: latest .deb from PacBrew/pacbrew-pacman releases (fails loudly if none found)
RUN --mount=type=secret,id=gh_token set -eux; \
    AUTH=""; if [ -s /run/secrets/gh_token ]; then AUTH="Authorization: Bearer $(cat /run/secrets/gh_token)"; fi; \
    url=$(curl -fsSL -H "$AUTH" https://api.github.com/repos/PacBrew/pacbrew-pacman/releases/latest \
          | jq -r '.assets[].browser_download_url | select(endswith(".deb"))' | head -n1); \
    test -n "$url" || { echo "ERROR: no .deb asset found in PacBrew/pacbrew-pacman latest release"; exit 1; }; \
    curl -fsSL -o /tmp/pacbrew.deb "$url"; \
    dpkg -i /tmp/pacbrew.deb; rm -f /tmp/pacbrew.deb

RUN set -eux; pacbrew-pacman -Sy; pacbrew-pacman -S --noconfirm ps4-openorbis ps4-openorbis-portlibs

COPY build-deps.sh /tmp/build-deps.sh
RUN bash /tmp/build-deps.sh && rm -rf /tmp/deps /tmp/build-deps.sh

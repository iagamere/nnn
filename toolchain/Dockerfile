FROM ubuntu:22.04
ENV DEBIAN_FRONTEND=noninteractive
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential git curl wget ca-certificates xz-utils unzip zip cmake ninja-build \
    autoconf automake libtool pkg-config perl python3 python3-pip make patch bison flex \
    libxml2-utils gettext texinfo && rm -rf /var/lib/apt/lists/*
# pacbrew-pacman release bootstrap. If upstream changes its release asset layout, fail loudly.
RUN set -eux; \
    mkdir -p /tmp/pacbrew; cd /tmp/pacbrew; \
    curl -fL https://api.github.com/repos/PacBrew/pacbrew-pacman/releases/latest -o release.json; \
    python3 -c 'import json; d=json.load(open("release.json")); print("\n".join(a["browser_download_url"] for a in d["assets"] if a["name"].endswith((".tar.gz",".tgz",".zip"))))' > assets.txt; \
    test -s assets.txt || { echo 'ERROR: no archive asset found for pacbrew-pacman latest release'; exit 1; }; \
    URL=$(head -n1 assets.txt); curl -fL "$URL" -o pacbrew.tar.gz; \
    tar -xf pacbrew.tar.gz; \
    INSTALLER=$(find . -type f -name '*install*' -perm -u+x -print -quit); \
    if [[ -n "$INSTALLER" ]]; then "$INSTALLER"; else echo 'ERROR: could not identify pacbrew installer in release archive'; find . -maxdepth 3 -type f; exit 1; fi
RUN set -eux; pacbrew-pacman -Sy; pacbrew-pacman -S --noconfirm ps4-openorbis ps4-openorbis-portlibs; chmod guo+x /opt/pacbrew/ps4/openorbis/ps4vars.sh
COPY toolchain/build-deps.sh /usr/local/bin/build-deps.sh
RUN chmod +x /usr/local/bin/build-deps.sh && /usr/local/bin/build-deps.sh
ENV PATH="/opt/pacbrew/ps4/openorbis/bin:${PATH}"
WORKDIR /work

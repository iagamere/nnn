#!/usr/bin/env bash
# Builds and installs project dependencies into $OPENORBIS/usr (same prefix as the cy33hc READMEs).
set -euxo pipefail
chmod guo+x /opt/pacbrew/ps4/openorbis/ps4vars.sh
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
W=/tmp/deps; mkdir -p "$W"; cd "$W"

cmake_build() {   # cmake_build <srcdir> [extra cmake args...]
  local d="$1"; shift
  mkdir -p "$d/build"; cd "$d/build"
  openorbis-cmake -DCMAKE_INSTALL_PREFIX="$OPENORBIS/usr" "$@" ..
  make -j"$(nproc)"
  make install
  cd "$W"
}

# 1) openssl (cy33hc/ps4-openssl, branch OpenSSL_1_1_1-ps4)
git clone --depth 1 -b OpenSSL_1_1_1-ps4 https://github.com/cy33hc/ps4-openssl.git openssl
cmake_build openssl

# 2) libcurl 7.80 (-DSOL_IP=0, -lSceNet, comment out #include <osreldate.h>)
curl -fsSL https://curl.se/download/curl-7.80.0.tar.xz | tar xJ
grep -rl 'osreldate.h' curl-7.80.0 | xargs -r sed -i 's|^\(\s*#\s*include\s*<osreldate.h>\)|// \1|'
cmake_build curl-7.80.0 -DCMAKE_C_FLAGS="-DSOL_IP=0" -DCMAKE_EXE_LINKER_FLAGS="-lSceNet" \
  -DBUILD_SHARED_LIBS=OFF -DBUILD_CURL_EXE=OFF -DBUILD_TESTING=OFF \
  -DCMAKE_USE_OPENSSL=ON -DOPENSSL_ROOT_DIR="$OPENORBIS/usr"

# 3) libsmb2 (cy33hc/libsmb2, branch ps4)
git clone --depth 1 -b ps4 https://github.com/cy33hc/libsmb2.git libsmb2
cmake_build libsmb2

# 4) libssh2 (upstream 1.10.0)
git clone --depth 1 -b libssh2-1.10.0 https://github.com/libssh2/libssh2.git libssh2
cmake_build libssh2 -DCRYPTO_BACKEND=OpenSSL -DOPENSSL_ROOT_DIR="$OPENORBIS/usr" \
  -DBUILD_SHARED_LIBS=OFF -DBUILD_EXAMPLES=OFF -DBUILD_TESTING=OFF

# 5) libnfs (cy33hc/libnfs, branch ps4)
git clone --depth 1 -b ps4 https://github.com/cy33hc/libnfs.git libnfs
cmake_build libnfs

# 6) json-c (upstream)
git clone --depth 1 -b json-c-0.16-20220414 https://github.com/json-c/json-c.git json-c
cmake_build json-c -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTING=OFF -DDISABLE_WERROR=ON

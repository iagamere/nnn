#!/usr/bin/env bash
set -euxo pipefail
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
PREFIX="${OPENORBIS}/usr"
WORK=/tmp/ps4-deps
mkdir -p "$WORK" "$PREFIX"
cd "$WORK"

# OpenSSL: upstream PS4-specific recipe.
git clone --depth 1 --branch OpenSSL_1_1_1-ps4 https://github.com/cy33hc/ps4-openssl.git openssl
cd openssl
if [[ -f README_PS4.md ]]; then cat README_PS4.md; fi
# Respect upstream's documented recipe rather than silently guessing a configure target.
./Configure --prefix="$PREFIX" ps4-cross
make -j"$(nproc)"
make install
cd "$WORK"

# libcurl 7.80.0, using the exact PS4-specific flags described by cy33hc's README.
curl -fL https://curl.se/download/curl-7.80.0.tar.xz -o curl-7.80.0.tar.xz
tar -xf curl-7.80.0.tar.xz
cd curl-7.80.0
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
autoreconf -fi
CFLAGS="${CFLAGS:-} -DSOL_IP=0" LIBS="${LIBS:-} -lSceNet" ./configure --prefix="${OPENORBIS}/usr" --host=x86_64 --disable-shared --enable-static --with-openssl --disable-manual
sed -i 's|#include <osreldate.h>|//#include <osreldate.h>|g' include/curl/curl.h
make -C lib -j"$(nproc)" install
cd "$WORK"

# Remaining libraries: clone the requested PS4 branches. Each build uses upstream build files;
# any upstream layout change stops the image build and is visible in Actions logs.
git clone --depth 1 --branch ps4 https://github.com/cy33hc/libsmb2.git libsmb2
cd libsmb2
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
if [[ -f Makefile.platform ]]; then make -f Makefile.platform ps4_install; else echo 'ERROR: libsmb2 Makefile.platform missing'; exit 2; fi
cd "$WORK"
git clone --depth 1 https://github.com/libssh2/libssh2.git libssh2
cd libssh2
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
openorbis-cmake -S . -B build -DCMAKE_INSTALL_PREFIX="$PREFIX" -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTING=OFF
cmake --build build -j"$(nproc)" --target install
cd "$WORK"
git clone --depth 1 --branch ps4 https://github.com/cy33hc/libnfs.git libnfs
cd libnfs
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
if [[ -f README.md ]]; then grep -n -i -A8 -B3 'build\|install\|openorbis' README.md || true; fi
if [[ -f CMakeLists.txt ]]; then
  openorbis-cmake -S . -B build -DCMAKE_INSTALL_PREFIX="$PREFIX" -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTING=OFF
  cmake --build build -j"$(nproc)" --target install
else
  echo 'ERROR: libnfs PS4 branch has no CMakeLists.txt; inspect upstream branch instructions instead of guessing.' >&2
  exit 2
fi
cd "$WORK"
git clone --depth 1 https://github.com/json-c/json-c.git json-c
cd json-c
source /opt/pacbrew/ps4/openorbis/ps4vars.sh
openorbis-cmake -S . -B build -DCMAKE_INSTALL_PREFIX="$PREFIX" -DBUILD_SHARED_LIBS=OFF -DBUILD_TESTING=OFF
cmake --build build -j"$(nproc)" --target install

# Explicit sanity checks: do not proceed with a partial image.
for f in "$PREFIX/lib/libcrypto.a" "$PREFIX/lib/libssl.a" "$PREFIX/lib/libcurl.a" "$PREFIX/lib/libjson-c.a"; do
  if [[ ! -f "$f" ]]; then echo "ERROR: expected dependency archive not found: $f" >&2; find "$PREFIX" -maxdepth 3 -type f -name '*.a' -print; exit 3; fi
done
echo 'Toolchain dependency build finished.'

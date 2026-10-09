#!/usr/bin/env bash
# usage: build.sh <zip> <outdir>   (runs inside the toolchain image)
set -uo pipefail
ZIP="$1"; OUT="$2"
mkdir -p "$OUT"; LOG="$OUT/build.log"; : > "$LOG"
ELFPATH_FILE=/tmp/elfpath; rm -f "$ELFPATH_FILE"

(
  set -eo pipefail
  echo "== unzip $ZIP =="
  rm -rf /tmp/src; mkdir /tmp/src
  unzip -q "$ZIP" -d /tmp/src
  CM=$(find /tmp/src -name CMakeLists.txt -printf '%d %p\n' | sort -n | head -n1 | cut -d' ' -f2-)
  [ -n "$CM" ] || { echo "error: CMakeLists.txt not found inside the zip"; exit 1; }
  SRC=$(dirname "$CM"); echo "== project dir: $SRC =="
  [ -f /opt/pacbrew/ps4/openorbis/ps4vars.sh ] || { echo "error: ps4vars.sh missing in image"; exit 1; }
  source /opt/pacbrew/ps4/openorbis/ps4vars.sh
  cd "$SRC"; rm -rf build; mkdir build; cd build
  echo "== openorbis-cmake =="; openorbis-cmake ..
  echo "== make =="; make -j"$(nproc)"
  ELF=$(find "$SRC/build" -name 'ezremote-server-*.elf' | head -n1)
  [ -n "$ELF" ] || { echo "error: no ezremote-server-*.elf was produced"; exit 1; }
  echo "$ELF" > "$ELFPATH_FILE"
) >> "$LOG" 2>&1
rc=$?

ok=false; elf=""; version=""
if [ $rc -eq 0 ] && [ -s "$ELFPATH_FILE" ]; then
  cp "$(cat "$ELFPATH_FILE")" "$OUT/"
  elf=$(basename "$(cat "$ELFPATH_FILE")")
  version=${elf#ezremote-server-}; version=${version%.elf}
  ok=true
fi

grep -n -i -B3 -A3 'error' "$LOG" > "$OUT/errors.txt" || true
if [ "$ok" != true ] && [ ! -s "$OUT/errors.txt" ]; then
  echo "no line containing 'error' was found; see build.log (exit code $rc)" > "$OUT/errors.txt"
fi

jq -n --argjson ok "$ok" --arg version "$version" --arg elf "$elf" --arg time "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{ok:$ok,version:$version,elf:$elf,time:$time}' > "$OUT/status.json"
echo "build.sh finished: ok=$ok rc=$rc"
exit 0

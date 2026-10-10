#!/usr/bin/env bash
# Builds the Ionity VPN tunnel engine for Android and writes it into the app:
#   android/app/src/main/jniLibs/<abi>/libionityvpn.so
#   android/app/src/main/java/today/ionity/opensourcevpn/ovpn3/*.java   (SWIG bindings)
# Needs: Linux (or WSL), git, perl, make, cmake, ninja, swig 4, and an Android NDK (ANDROID_NDK_ROOT, r27+).
#   engine/build-engine.sh [work-dir]
set -euo pipefail
REPO="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${1:-$HOME/ionity-engine-work}"
: "${ANDROID_NDK_ROOT:?set ANDROID_NDK_ROOT to an Android NDK}"
API=23
ABIS="arm64-v8a armeabi-v7a x86_64"
# Pinned upstream sources
O3_TAG=release/3.11; OSSL_TAG=openssl-3.5.9; ASIO_TAG=asio-1-38-2; LZ4_TAG=v1.10.0

mkdir -p "$WORK/src" && cd "$WORK/src"
fetch() { [ -d "$3" ] || git -c advice.detachedHead=false clone -q --depth 1 --branch "$1" "$2" "$3"; }
fetch "$O3_TAG"   https://github.com/OpenVPN/openvpn3.git openvpn3
fetch "$OSSL_TAG" https://github.com/openssl/openssl.git  openssl
fetch "$ASIO_TAG" https://github.com/chriskohlhoff/asio.git asio
fetch "$LZ4_TAG"  https://github.com/lz4/lz4.git          lz4

export PATH="$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH"
declare -A OSSL_TARGET=([arm64-v8a]=android-arm64 [armeabi-v7a]=android-arm [x86_64]=android-x86_64)

# 1. OpenSSL, static, per ABI
for abi in $ABIS; do
  out="$WORK/out/openssl-$abi"
  [ -f "$out/lib/libssl.a" ] && continue
  b="$WORK/build/openssl-$abi"; rm -rf "$b"; mkdir -p "$b"; cd "$b"
  "$WORK/src/openssl/Configure" "${OSSL_TARGET[$abi]}" -D__ANDROID_API__=$API no-shared no-tests no-apps no-docs no-module no-legacy \
     --prefix="$out" --libdir=lib >config.log 2>&1
  make -j"$(nproc)" build_libs >make.log 2>&1 && make install_dev >install.log 2>&1
  echo "openssl $abi ok"
done

# 2. SWIG bindings (Java package of this app)
GEN="$WORK/gen"; JAVA_OUT="$REPO/android/app/src/main/java/today/ionity/opensourcevpn/ovpn3"
rm -rf "$GEN" "$JAVA_OUT"; mkdir -p "$GEN" "$JAVA_OUT"
swig -c++ -java -package today.ionity.opensourcevpn.ovpn3 -DOPENVPN_PLATFORM_ANDROID \
  -I"$WORK/src/openvpn3/client" -I"$WORK/src/openvpn3" \
  -outdir "$JAVA_OUT" -o "$GEN/ovpncli_wrap.cxx" -oh "$GEN/ovpncli_wrap.h" "$WORK/src/openvpn3/client/ovpncli.i"

# 3. libionityvpn.so per ABI
for abi in $ABIS; do
  b="$WORK/build/engine-$abi"; rm -rf "$b"
  cmake -S "$REPO/engine" -B "$b" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=$abi -DANDROID_PLATFORM=android-$API -DANDROID_STL=c++_static \
    -DDEPS="$WORK/src" -DOPENSSL_PREFIX="$WORK/out/openssl-$abi" -DGEN="$GEN" -DO3_VERSION="$O3_TAG" >/dev/null
  cmake --build "$b" -j"$(nproc)"
  mkdir -p "$REPO/android/app/src/main/jniLibs/$abi"
  cp "$b/libionityvpn.so" "$REPO/android/app/src/main/jniLibs/$abi/"
  echo "engine $abi ok ($(du -h "$b/libionityvpn.so" | cut -f1))"
done
echo ENGINE_OK

# Ionity VPN tunnel engine: OpenVPN 3 core (used under MPL-2.0) + OpenSSL 3 (Apache-2.0) + asio (BSL-1.0)
# + LZ4 (BSD-2-Clause), with the SWIG JNI glue, built into one shared library: libionityvpn.so.
# Ionity's own files: GPL-3.0-or-later. Built by engine/build-engine.sh (Linux / WSL / CI).
cmake_minimum_required(VERSION 3.22)
project(ionityvpn C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(DEPS "" CACHE PATH "Folder with openvpn3/, asio/, lz4/ sources")
set(OPENSSL_PREFIX "" CACHE PATH "Static OpenSSL install for this ABI")
set(GEN "" CACHE PATH "Folder with the SWIG-generated ovpncli_wrap.cxx")
set(O3_VERSION "unknown" CACHE STRING "OpenVPN 3 core version string")

add_library(ionity_lz4 STATIC ${DEPS}/lz4/lib/lz4.c)
target_include_directories(ionity_lz4 PUBLIC ${DEPS}/lz4/lib)
set_target_properties(ionity_lz4 PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(ionityvpn SHARED
  ${DEPS}/openvpn3/client/ovpncli.cpp
  ${DEPS}/openvpn3/openvpn/openssl/xkey/xkey_provider.c
  ${DEPS}/openvpn3/openvpn/openssl/xkey/xkey_helper.c
  ${DEPS}/openvpn3/openvpn/crypto/data_epoch.cpp
  ${GEN}/ovpncli_wrap.cxx)

target_include_directories(ionityvpn PRIVATE
  ${DEPS}/openvpn3 ${DEPS}/openvpn3/client ${DEPS}/asio/asio/include ${OPENSSL_PREFIX}/include ${GEN})

target_compile_definitions(ionityvpn PRIVATE
  USE_OPENSSL ASIO_STANDALONE USE_ASIO HAVE_LZ4
  OPENVPN_ALLOW_INSECURE_CERTPROFILE
  OPENSSL_API_COMPAT=0x10200000L
  OPENVPN_CORE_GIT_VERSION="${O3_VERSION}"
  "SWIGEXPORT=__attribute__((visibility(\"default\")))")

target_compile_options(ionityvpn PRIVATE -fvisibility=hidden -Os -Wno-deprecated-declarations -Wno-unused-function -Wno-sometimes-uninitialized)
target_link_libraries(ionityvpn PRIVATE ${OPENSSL_PREFIX}/lib/libssl.a ${OPENSSL_PREFIX}/lib/libcrypto.a ionity_lz4 log)
target_link_options(ionityvpn PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL -Wl,-z,max-page-size=16384 -s)

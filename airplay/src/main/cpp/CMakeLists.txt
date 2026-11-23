cmake_minimum_required(VERSION 3.22.1)
project(castbay_protocol LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(THIRD_PARTY_ROOT ${CMAKE_SOURCE_DIR}/../../../../third_party)
set(UXPLAY_ROOT ${THIRD_PARTY_ROOT}/UxPlay)
set(LIBPLIST_ROOT ${THIRD_PARTY_ROOT}/libplist)

# OpenSSL 3's libcrypto, built from the third_party/openssl submodule by tools/build-openssl.sh
# (Gradle runs it first). OpenSSL 3 is Apache 2.0, compatible with UxPlay's GPL-3.0; the 1.1.1
# prefab package used before had a licence the GPL doesn't allow linking with.
set(OPENSSL_ANDROID ${THIRD_PARTY_ROOT}/openssl-android/${ANDROID_ABI})
if(NOT EXISTS ${OPENSSL_ANDROID}/lib/libcrypto.a)
    message(FATAL_ERROR "No OpenSSL for ${ANDROID_ABI}: run tools/build-openssl.sh ${ANDROID_ABI}")
endif()
add_library(openssl_crypto STATIC IMPORTED)
set_target_properties(openssl_crypto PROPERTIES
    IMPORTED_LOCATION ${OPENSSL_ANDROID}/lib/libcrypto.a
    INTERFACE_INCLUDE_DIRECTORIES ${OPENSSL_ANDROID}/include
)

set(LIBPLIST_SRC
    ${LIBPLIST_ROOT}/src/plist.c
    ${LIBPLIST_ROOT}/src/bplist.c
    ${LIBPLIST_ROOT}/src/xplist.c
    ${LIBPLIST_ROOT}/src/jplist.c
    ${LIBPLIST_ROOT}/src/oplist.c
    ${LIBPLIST_ROOT}/src/base64.c
    ${LIBPLIST_ROOT}/src/bytearray.c
    ${LIBPLIST_ROOT}/src/hashtable.c
    ${LIBPLIST_ROOT}/src/ptrarray.c
    ${LIBPLIST_ROOT}/src/common.c
    ${LIBPLIST_ROOT}/src/out-default.c
    ${LIBPLIST_ROOT}/src/out-limd.c
    ${LIBPLIST_ROOT}/src/out-plutil.c
    ${LIBPLIST_ROOT}/src/time64.c
    ${LIBPLIST_ROOT}/src/jsmn.c
    ${LIBPLIST_ROOT}/libcnary/node.c
    ${LIBPLIST_ROOT}/libcnary/node_list.c
)

add_library(plist STATIC ${LIBPLIST_SRC})
target_include_directories(plist PUBLIC
    ${LIBPLIST_ROOT}/include
    ${LIBPLIST_ROOT}/src
    ${LIBPLIST_ROOT}/libcnary/include
)
target_compile_definitions(plist PRIVATE
    PACKAGE_VERSION="2.6.0"
    _GNU_SOURCE
    HAVE_STRNDUP=1
)
target_compile_options(plist PRIVATE
    -Wno-implicit-function-declaration
    -Wno-int-conversion
)

# UxPlay's protocol library (lib/ only: its GStreamer renderers are replaced by MediaCodec,
# and its dns_sd/ and mdnsd/ publishers by Kotlin NsdManager via dnssd_android.c).
set(UXPLAY_LIB ${UXPLAY_ROOT}/lib)
file(GLOB UXPLAY_SRC ${UXPLAY_LIB}/*.c)
# Apply the Android stop wakeup to a build-local copy of UxPlay's listener.
set(HTTPD_WORK_DIR "${CMAKE_CURRENT_BINARY_DIR}/uxplay-httpd")
set(HTTPD_STOP_PATCH "${CMAKE_CURRENT_SOURCE_DIR}/httpd-stop-wakeup.patch")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${HTTPD_STOP_PATCH}")
configure_file("${UXPLAY_LIB}/httpd.c" "${HTTPD_WORK_DIR}/httpd.c" COPYONLY)
find_package(Git REQUIRED)
execute_process(
    COMMAND "${GIT_EXECUTABLE}" apply --no-index "${HTTPD_STOP_PATCH}"
    WORKING_DIRECTORY "${HTTPD_WORK_DIR}"
    COMMAND_ERROR_IS_FATAL ANY
)
list(REMOVE_ITEM UXPLAY_SRC ${UXPLAY_LIB}/httpd.c)
list(APPEND UXPLAY_SRC "${HTTPD_WORK_DIR}/httpd.c")
# raop_ext.c includes raop.c and adds helpers that need its private state.
list(REMOVE_ITEM UXPLAY_SRC ${UXPLAY_LIB}/raop.c)
file(GLOB PLAYFAIR_SRC ${UXPLAY_LIB}/playfair/*.c)
file(GLOB LLHTTP_SRC ${UXPLAY_LIB}/llhttp/*.c)

add_library(uxplay STATIC
    ${UXPLAY_SRC}
    ${PLAYFAIR_SRC}
    ${LLHTTP_SRC}
    ${CMAKE_CURRENT_SOURCE_DIR}/dnssd_android.c
    ${CMAKE_CURRENT_SOURCE_DIR}/raop_ext.c
)
target_include_directories(uxplay PUBLIC
    ${UXPLAY_LIB}
    ${UXPLAY_LIB}/playfair
    ${UXPLAY_LIB}/llhttp
)
target_compile_definitions(uxplay PRIVATE
    _GNU_SOURCE
    # The bundled libplist is 2.6.0 (see PACKAGE_VERSION above); UxPlay's own build
    # detects these via pkg-config.
    PLIST_210
    PLIST_230
)
target_compile_options(uxplay PRIVATE
    -Wno-deprecated-declarations
    -Wno-pointer-sign
)
target_link_libraries(uxplay PUBLIC plist openssl_crypto log)

# Apple's ALAC reference decoder (Apache-2.0): Android has no ALAC codec, and AirPlay
# audio streaming (music apps) sends ALAC. Only the decoder half is built.
set(ALAC_CODEC ${THIRD_PARTY_ROOT}/alac/codec)
add_library(alac STATIC
    ${ALAC_CODEC}/ALACDecoder.cpp
    ${ALAC_CODEC}/ALACBitUtilities.c
    ${ALAC_CODEC}/EndianPortable.c
    ${ALAC_CODEC}/ag_dec.c
    ${ALAC_CODEC}/dp_dec.c
    ${ALAC_CODEC}/matrix_dec.c
)
target_include_directories(alac PUBLIC ${ALAC_CODEC})
# EndianPortable.c only recognises x86 and Windows as little-endian; ARM is too.
target_compile_definitions(alac PRIVATE TARGET_RT_LITTLE_ENDIAN=1)

add_library(castbay_protocol SHARED
    jni_bridge.cpp
    video_sink.cpp
    audio_sink.cpp
    alac_decoder.cpp
    file_log.cpp
)
target_include_directories(castbay_protocol PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(castbay_protocol PRIVATE uxplay alac log android)
# Drops the parts of the static libraries (libcrypto above all) nothing calls.
target_link_options(castbay_protocol PRIVATE -Wl,--gc-sections)

cmake_minimum_required(VERSION 3.22.1)
project(featherup LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Hỗ trợ 16KB memory page size alignment bắt buộc cho Android 15, 16, 17+
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# ---------------------------------------------------------------------------
# NCNN prebuilt
# Đặt bộ ncnn prebuilt (android arm64-v8a / armeabi-v7a / x86_64 / x86) tại:
#   app/src/main/cpp/ncnn/<abi>/lib/libncnn.a (hoặc .so)
#   app/src/main/cpp/ncnn/<abi>/include/...
# Download: https://github.com/Tencent/ncnn/releases
# ---------------------------------------------------------------------------
set(NCNN_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/ncnn/${ANDROID_ABI})
if(EXISTS ${NCNN_ROOT})
    set(ncnn_DIR ${NCNN_ROOT}/lib/cmake/ncnn)
    find_package(ncnn REQUIRED)
    message(STATUS "Found ncnn prebuilt at ${NCNN_ROOT}")
else()
    message(WARNING "NCNN prebuilt not found at ${NCNN_ROOT}. "
        "Build sẽ fallback sang stub JNI (không có upscale thật). "
        "Hãy đặt ncnn prebuilt theo hướng dẫn trong CMakeLists.txt.")
endif()

find_library(log-lib log)
find_library(vulkan-lib vulkan)

add_library(featherup SHARED upscale_jni.cpp)

target_include_directories(featherup PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    $<$<BOOL:${EXISTS}>:${NCNN_ROOT}/include>
)

if(TARGET ncnn)
    target_link_libraries(featherup PRIVATE ncnn)
    target_compile_definitions(featherup PRIVATE FEATHER_HAS_NCNN=1)
else()
    # Stub build khi chưa có ncnn — JNI vẫn compile để UI/dev chạy được
endif()

target_link_libraries(featherup PRIVATE
    ${log-lib}
    ${vulkan-lib}      # Vulkan backend cho NCNN GPU pipeline
    -fopenmp           # OpenMP multi-thread CPU inference
    android
)

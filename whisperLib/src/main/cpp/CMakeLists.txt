cmake_minimum_required(VERSION 3.31.6)

project(whisper_android)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(
    WHISPER_ROOT
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp
)

set(
    SOURCE_FILES
    ${WHISPER_ROOT}/src/whisper.cpp
    ${CMAKE_CURRENT_LIST_DIR}/jni.c
)

find_library(LOG_LIB log)

include(FetchContent)

FetchContent_Declare(
    ggml
    SOURCE_DIR ${WHISPER_ROOT}/ggml
)

FetchContent_MakeAvailable(ggml)

function(build_whisper_library target)

    add_library(
        ${target}
        SHARED
        ${SOURCE_FILES}
    )

    target_link_libraries(
        ${target}
        ${LOG_LIB}
        android
        ggml
    )

    target_compile_definitions(
        ${target}
        PUBLIC
        GGML_USE_CPU
    )

    target_compile_options(
        ${target}
        PRIVATE
        -O3
    )

    target_include_directories(
        ${target}
        PRIVATE
        ${WHISPER_ROOT}
        ${WHISPER_ROOT}/src
        ${WHISPER_ROOT}/include
        ${WHISPER_ROOT}/ggml/include
        ${WHISPER_ROOT}/ggml/src
        ${WHISPER_ROOT}/ggml/src/ggml-cpu
    )
endfunction()

build_whisper_library(whisper)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    build_whisper_library(whisper_v8fp16_va)

    target_compile_options(
        whisper_v8fp16_va
        PRIVATE
        -march=armv8.2-a+fp16
    )
endif()

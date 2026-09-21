cmake_minimum_required(VERSION 3.31.6)

project("ai-chat" LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(LLAMA_SRC
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp
)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_CPU_KLEIDIAI ON)
    set(GGML_OPENMP ON)
else()
    set(GGML_SYSTEM_ARCH "x86")
    set(GGML_CPU_KLEIDIAI OFF)
    set(GGML_OPENMP OFF)
endif()

add_subdirectory(
    ${LLAMA_SRC}
    build-llama
)

add_library(
    ai-chat
    SHARED
    ai_chat.cpp
)

target_compile_definitions(
    ai-chat
    PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
    GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(
    ai-chat
    PRIVATE
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/ggml/src
)

target_link_libraries(
    ai-chat
    llama
    llama-common
    android
    log
)

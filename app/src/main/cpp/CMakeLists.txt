cmake_minimum_required(VERSION 3.22.1)
project(clipforge_whisper LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
# APKs remain debuggable; inference kernels must not run at -O0 on phones.
set(CMAKE_C_FLAGS_DEBUG "-O3 -g" CACHE STRING "Optimized debug native C" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -g" CACHE STRING "Optimized debug native C++" FORCE)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
# Directory options propagate into ggml subdirectories, even when the Android
# toolchain shadows cache variables with its own debug flag variables.
add_compile_options($<$<CONFIG:Debug>:-O3>)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
add_subdirectory(whisper.cpp)
add_library(clipforge_whisper SHARED whisper_bridge.cpp)
target_link_libraries(clipforge_whisper PRIVATE whisper)
if(ANDROID)
    target_link_libraries(clipforge_whisper PRIVATE log)
else()
    # Desktop JNI smoke test exercises the same bridge using public sample audio.
    find_package(JNI REQUIRED)
    target_include_directories(clipforge_whisper PRIVATE ${JNI_INCLUDE_DIRS})
endif()

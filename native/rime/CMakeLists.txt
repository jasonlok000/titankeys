# TitanKeys' Rime: librime and OpenCC for the app, built the way Trime builds them.
# This is Trime's app/src/main/jni/CMakeLists.txt (GPL-3.0-or-later, Rime community) at the commit pinned in build.sh,
# with only one of its plugins, librime-predict (next-word suggestions, #56), merged into Rime as Trime merges it, with
# TitanKeys' own JNI layer in rime_jni.cc, and with a host tool (rime_tool.cc) that compiles the data and tries codes on the
# PC. The plugin's own build_predict tool compiles its tables (data.sh).
cmake_minimum_required(VERSION 3.18.0)
project(titankeys_rime VERSION 1.0.0)

# Reproducible: no build id, and no build paths in the binary.
add_link_options("LINKER:--build-id=none")
add_compile_options("-ffile-prefix-map=${CMAKE_SOURCE_DIR}=." "-ffile-prefix-map=${TK_RIME_SOURCES}=.")

set(CMAKE_MODULE_PATH "${CMAKE_SOURCE_DIR}/cmake" ${CMAKE_MODULE_PATH})

option(BUILD_SHARED_LIBS "" OFF)
option(BUILD_TESTING "" OFF)
option(ENABLE_LOGGING "Enable logging with spdlog library" ON)

include(Boost)

if(ENABLE_LOGGING)
  set(SPDLOG_BUILD_SHARED OFF CACHE BOOL "" FORCE)
  set(SPDLOG_BUILD_PIC ON CACHE BOOL "" FORCE)
  set(SPDLOG_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
  set(SPDLOG_BUILD_TESTS OFF CACHE BOOL "" FORCE)
  set(SPDLOG_INSTALL OFF CACHE BOOL "" FORCE)
  set(SPDLOG_FMT_EXTERNAL OFF CACHE BOOL "" FORCE)
  add_subdirectory(librime/deps/spdlog EXCLUDE_FROM_ALL)
endif()

option(YAML_CPP_BUILD_TOOLS "" OFF)
option(YAML_CPP_BUILD_TESTS "" OFF)
option(YAML_CPP_INSTALL "" OFF)
add_subdirectory(librime/deps/yaml-cpp)

option(SNAPPY_BUILD_TESTS "" OFF)
option(SNAPPY_BUILD_BENCHMARKS "" OFF)
option(SNAPPY_INSTALL "" OFF)
add_subdirectory(snappy)

set(HAVE_SNAPPY TRUE)
option(LEVELDB_BUILD_TESTS "" OFF)
option(LEVELDB_BUILD_BENCHMARKS "" OFF)
option(LEVELDB_INSTALL "" OFF)
add_subdirectory(librime/deps/leveldb)

add_subdirectory(librime/deps/marisa-trie)
set(LIBMARISA marisa)
option(USE_SYSTEM_MARISA "" ON)
add_subdirectory(librime/deps/opencc)
include(OpenccWorkarounds)

set(X11Keysym "${CMAKE_SOURCE_DIR}/librime/include")
include_directories("${X11Keysym}")

# librime-predict, at the commit Trime pins (its submodule, fetched with Trime by build.sh), linked into librime's plugins
# folder as Trime's cmake/Rime.cmake links it: librime merges every plugin it finds there into the library.
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/librime/plugins/librime-predict")
  file(CREATE_LINK "${TK_RIME_SOURCES}/trime/app/src/main/jni/librime-predict"
       "${CMAKE_SOURCE_DIR}/librime/plugins/librime-predict" SYMBOLIC)
endif()

option(BUILD_TEST "" OFF)
option(BUILD_STATIC "" ON)
add_subdirectory(librime)
target_compile_options(rime-static PRIVATE "-Wno-error=deprecated-declarations")
find_package(Opencc REQUIRED)

set(RIME_INCLUDES "${CMAKE_BINARY_DIR}/librime/src" "${CMAKE_SOURCE_DIR}/librime/src")

if(ANDROID)
  # The app's library: Rime, OpenCC and the JNI layer, nothing else exported.
  add_library(rime_tk SHARED rime_jni.cc)
  target_link_libraries(rime_tk rime-static ${Opencc_LIBRARY} log)
  target_include_directories(rime_tk PRIVATE ${RIME_INCLUDES})
  set_target_properties(rime_tk PROPERTIES CXX_VISIBILITY_PRESET hidden VISIBILITY_INLINES_HIDDEN ON)
  target_link_options(rime_tk PRIVATE "LINKER:-z,max-page-size=16384" "LINKER:--exclude-libs,ALL" "LINKER:--gc-sections")
endif()

# rime_tool deploy <shared> <user> | rime_tool try <shared> <user> <schema> <variant> <code> [count]
add_executable(rime_tool rime_tool.cc)
target_link_libraries(rime_tool rime-static ${Opencc_LIBRARY})
target_include_directories(rime_tool PRIVATE ${RIME_INCLUDES})

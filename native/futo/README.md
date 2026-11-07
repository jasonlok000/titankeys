# FUTO native libraries

`include/swipe_decoder/trie.hpp` and `itrie.h` are copied from the FUTO swipe-library public headers.

Source: https://gitlab.futo.org/keyboard/swipe-library

Retrieved revision: 1b13f2c85d6b347f6ea3fbc4b3aaf01fce42429a

The accompanying GPL-3.0 licence is preserved in `LICENSE` and kept under `licences/`. The recognition library is built from the revision above, using its public JNI bindings. The personal dictionary adapter uses the public Trie/ITrie interface. `sizeof(Trie)` is checked for the supported arm64 ABI, and `native/bridge.sha256` pins the backend, C++ runtime, adapter and headers used together. Model files are unchanged.

The adapter also compiles a generated copy of the pinned JNI source with `native/swipe-jni.patch` and distinct `Safe` entry points. These translate C++ exceptions into Java failures, stop on pending Java exceptions, release partially initialised engines and avoid holding JNI array pins while configuring the decoder. The upstream checkout stays unchanged, and the engine implementation remains in the original shared backend.

Personal spellings that share a letter path, such as `well` and `we'll`, retain
separate terminal nodes and frequencies in the adapter's ITrie view. The shared
letter path and decoder coordinates remain unchanged.

## Build the libraries

On macOS, install Git and `uv`, then run:

```sh
bash native/build-engine.sh
```

The script downloads the checksum-verified Android NDK r27c, checks out the pinned FUTO source and its submodules, and builds for arm64 Android API 26. ExecuTorch is pinned to `0b0e2c5cdd67c8b4396a46ea1d1aa72ffb0128d7` (v1.2.0). The build applies FUTO's exception-handling patch and flatcc buffer-size fix, then rebuilds the personal dictionary adapter against the resulting library and NDK C++ runtime.

Build tools and source checkouts stay under the ignored `build/` directory. To rebuild only the adapter after changing `native/personal_dictionary.cpp`, run `bash native/build.sh`.

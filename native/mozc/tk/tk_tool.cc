// TitanKeys' Mozc tool for the PC, on the same core as the app (tk_core.cc): native/mozc/build.sh builds it, and the
// Japanese checks use it.
//   mozc_tool <mozc.data> <profile folder> [--desktop] [--choices N]
// reads romaji, one line each, and prints for each "romaji<TAB>hiragana<TAB>choice<TAB>choice...": the first N choices
// (8 by default) in Android's way, as the app's bar gets them, or with --desktop Mozc's desktop conversion (Space), as one
// choice. Each line starts fresh and nothing is learned between lines. Characters other than a-z and - are typed as they
// are, as the app types them through Mozc's romaji table.
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <iostream>
#include <string>

#include "android/tk/tk_core.h"

int main(int argc, char** argv) {
  if (argc < 3) {
    std::fprintf(stderr, "usage: %s <mozc.data> <profile> [--desktop] [--choices N]\n", argv[0]);
    return 2;
  }
  bool desktop = false;
  int count = 8;
  for (int i = 3; i < argc; ++i) {
    if (std::strcmp(argv[i], "--desktop") == 0) desktop = true;
    if (std::strcmp(argv[i], "--choices") == 0 && i + 1 < argc) count = std::atoi(argv[++i]);
  }
  tk::Mozc mozc;
  if (!mozc.Start(argv[1], argv[2], desktop)) {
    std::fprintf(stderr, "Mozc didn't start on %s\n", argv[1]);
    return 1;
  }
  std::string line;
  while (std::getline(std::cin, line)) {
    mozc.Reset();
    mozc.TakeCommit();
    for (char c : line) mozc.Key(c);
    std::string out = line + "\t" + mozc.Preedit();
    if (desktop) {
      mozc.Space();
      out += "\t" + mozc.Preedit();
    } else {
      int n = 0;
      for (const std::string& choice : mozc.Choices()) {
        if (n++ >= count) break;
        out += "\t" + choice;
      }
    }
    std::printf("%s\n", out.c_str());
    std::fflush(stdout);
  }
  mozc.Stop();
  return 0;
}

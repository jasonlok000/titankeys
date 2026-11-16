// TitanKeys' Rime tool for the PC (data.sh uses it). Built from the same sources as the app's library.
//   rime_tool deploy <shared> <user>
//       compiles every schema in <shared>/default.yaml into <user>/build
//   rime_tool try <shared> <user> <schema> <variant> "<code>" [count]
//       types the code key by key (a space is sent as the delimiter, an apostrophe) and prints the first choices,
//       with the variant (zh_hans, zh_hk or zh_tw) set as the app sets it
// Based on the language study's test program (docs/studies/56-languages.md).
#include <rime_api.h>

#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

static const char* kVariants[] = {"zh_hans", "zh_hk", "zh_tw"};

static RimeApi* open(const char* shared, const char* user, bool prebuilt) {
  RimeApi* rime = rime_get_api();
  static std::string prebuiltDir, stagingDir;
  prebuiltDir = std::string(shared) + "/build";
  stagingDir = std::string(user) + "/build";
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = shared;
  traits.user_data_dir = user;
  if (prebuilt) traits.prebuilt_data_dir = prebuiltDir.c_str();
  traits.staging_dir = stagingDir.c_str();
  traits.distribution_name = "TitanKeys";
  traits.distribution_code_name = "titankeys";
  traits.distribution_version = "1";
  traits.app_name = "rime.titankeys";
  traits.min_log_level = 2;
  traits.log_dir = "";
  rime->setup(&traits);
  rime->initialize(&traits);
  return rime;
}

int main(int argc, char** argv) {
  if (argc >= 4 && strcmp(argv[1], "deploy") == 0) {
    RimeApi* rime = open(argv[2], argv[3], false);
    if (rime->start_maintenance(True)) rime->join_maintenance_thread();
    rime->finalize();
    return 0;
  }
  if (argc >= 7 && strcmp(argv[1], "try") == 0) {
    const char* schema = argv[4];
    const char* variant = argv[5];
    std::string code = argv[6];
    int count = argc > 7 ? atoi(argv[7]) : 6;
    RimeApi* rime = open(argv[2], argv[3], true);
    if (rime->start_maintenance(False)) rime->join_maintenance_thread();
    RimeSessionId s = rime->create_session();
    if (!rime->select_schema(s, schema)) {
      fprintf(stderr, "schema %s not found\n", schema);
      return 1;
    }
    for (const char* o : kVariants) rime->set_option(s, o, strcmp(o, variant) == 0 ? True : False);
    for (char c : code) rime->process_key(s, c == ' ' ? '\'' : (unsigned char)c, 0);
    RIME_STRUCT(RimeContext, ctx);
    printf("%s %s [%s]:", schema, variant, code.c_str());
    if (rime->get_context(s, &ctx)) {
      int n = ctx.menu.num_candidates < count ? ctx.menu.num_candidates : count;
      for (int i = 0; i < n; ++i) printf(" %s", ctx.menu.candidates[i].text);
      rime->free_context(&ctx);
    }
    printf("\n");
    rime->destroy_session(s);
    rime->finalize();
    return 0;
  }
  fprintf(stderr, "usage: %s deploy <shared> <user> | try <shared> <user> <schema> <variant> <code> [count]\n", argv[0]);
  return 2;
}

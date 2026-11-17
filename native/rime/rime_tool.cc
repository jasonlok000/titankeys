// TitanKeys' Rime tool for the PC (data.sh uses it). Built from the same sources as the app's library.
//   rime_tool deploy <shared> <user>
//       compiles every schema in <shared>/default.yaml into <user>/build
//   rime_tool try <shared> <user> <schema> <variant> "<code>" [count]
//       types the code key by key (a space is sent as the delimiter, an apostrophe) and prints the first choices,
//       with the variant (zh_hans, zh_hk or zh_tw) set as the app sets it
//   rime_tool next <shared> <user> <schema> <variant> "<code>" [key...]
//       types the code and Space, and prints the next-word suggestions, then after each further key
//   rime_tool space <shared> <user> <schema> <variant> "<code>"
//       types the code as typed, spaces and all, then Space once, as the app does; prints "committed|left over". A code of "-" reads one code a line from stdin.
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
  // As the app opens Rime (rime_jni.cc): no deploy, only the deployer's tasks, which sync needs, and the installation.
  if (prebuilt) {
    rime->deployer_initialize(&traits);
    rime->run_task("installation_update");
  }
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
  if (argc >= 7 && strcmp(argv[1], "next") == 0) {
    // The next-word suggestions (#56): types the code, Space picks its first choice, and it prints what was typed and the
    // choices Rime offers with nothing typed. Then each further key ("esc" for Escape, "bs" for Backspace, "space", "reset"
    // for clearing as the keyboard does at a new field, or letters), with the choices after it.
    RimeApi* rime = open(argv[2], argv[3], true);
    RimeSessionId s = rime->create_session();
    if (!rime->select_schema(s, argv[4])) return 1;
    for (const char* o : kVariants) rime->set_option(s, o, strcmp(o, argv[5]) == 0 ? True : False);
    auto show = [&](const char* step) {
      RIME_STRUCT(RimeCommit, commit);
      std::string typed;
      if (rime->get_commit(s, &commit)) {
        typed = commit.text ? commit.text : "";
        rime->free_commit(&commit);
      }
      printf("%s [%s] input '%s':", step, typed.c_str(), rime->get_input(s));
      RIME_STRUCT(RimeContext, ctx);
      if (rime->get_context(s, &ctx)) {
        for (int i = 0; i < ctx.menu.num_candidates; ++i) printf(" %s", ctx.menu.candidates[i].text);
        rime->free_context(&ctx);
      }
      printf("\n");
    };
    for (const char* c = argv[6]; *c; ++c) rime->process_key(s, *c == ' ' ? '\'' : (unsigned char)*c, 0);
    rime->process_key(s, ' ', 0);
    show("space");
    for (int i = 7; i < argc; ++i) {
      std::string key = argv[i];
      if (key == "esc") rime->process_key(s, 0xff1b, 0);
      else if (key == "bs") rime->process_key(s, 0xff08, 0);
      else if (key == "space") rime->process_key(s, ' ', 0);
      else if (key == "reset") rime->clear_composition(s);
      else for (char c : key) rime->process_key(s, (unsigned char)c, 0);
      show(key.c_str());
    }
    rime->destroy_session(s);
    rime->finalize();
    return 0;
  }
  if (argc >= 7 && strcmp(argv[1], "space") == 0) {
    const char* schema = argv[4];
    const char* variant = argv[5];
    RimeApi* rime = open(argv[2], argv[3], true);
    RimeSessionId s = rime->create_session();
    if (!rime->select_schema(s, schema)) {
      fprintf(stderr, "schema %s not found\n", schema);
      return 1;
    }
    for (const char* o : kVariants) rime->set_option(s, o, strcmp(o, variant) == 0 ? True : False);
    // "-": one code a line from stdin, each after a cleared input, for a corpus test.
    bool many = strcmp(argv[6], "-") == 0;
    char line[4096];
    while (many ? fgets(line, sizeof line, stdin) != nullptr : true) {
      std::string code = many ? std::string(line, strcspn(line, "\r\n")) : argv[6];
      rime->clear_composition(s);
      for (char c : code) rime->process_key(s, (unsigned char)c, 0);
      rime->process_key(s, ' ', 0);
      RIME_STRUCT(RimeCommit, commit);
      std::string typed = rime->get_commit(s, &commit) ? commit.text : "";
      if (commit.text) rime->free_commit(&commit);
      RIME_STRUCT(RimeContext, ctx);
      std::string left = rime->get_context(s, &ctx) && ctx.composition.preedit ? ctx.composition.preedit : "";
      rime->free_context(&ctx);
      printf("%s|%s\n", typed.c_str(), left.c_str());
      if (!many) break;
    }
    rime->destroy_session(s);
    rime->finalize();
    return 0;
  }
  if (argc >= 7 && strcmp(argv[1], "learn") == 0) {
    // Types the code and picks choice <index>, as a user would, so Rime learns it.
    RimeApi* rime = open(argv[2], argv[3], true);
    RimeSessionId s = rime->create_session();
    if (!rime->select_schema(s, argv[4])) return 1;
    for (const char* c = argv[5]; *c; ++c) rime->process_key(s, *c == ' ' ? '\'' : (unsigned char)*c, 0);
    Bool ok = rime->select_candidate_on_current_page(s, (size_t)atoi(argv[6]));
    rime->destroy_session(s);
    rime->finalize();
    return ok ? 0 : 1;
  }
  if (argc >= 4 && strcmp(argv[1], "sync") == 0) {
    // As the app's backup: merges every snapshot in <user>/sync/ and writes this install's own.
    RimeApi* rime = open(argv[2], argv[3], true);
    Bool ok = rime->sync_user_data();
    rime->join_maintenance_thread();
    rime->finalize();
    return ok ? 0 : 1;
  }
  fprintf(stderr, "usage: %s deploy <shared> <user> | try <shared> <user> <schema> <variant> <code> [count]\n"
          "  | next <shared> <user> <schema> <variant> <code> [key...]\n"
          "  | space <shared> <user> <schema> <variant> <code>\n"
          "  | learn <shared> <user> <schema> <code> <index> | sync <shared> <user>\n", argv[0]);
  return 2;
}

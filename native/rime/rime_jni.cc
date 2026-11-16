// TitanKeys' JNI layer for Rime: only what the bar's picker needs.
// Trime's librime_jni (GPL-3.0-or-later) is the reference; this is a much smaller layer written for TitanKeys.
//
// One Rime session for the whole keyboard. Every call takes the same lock, so start() can run on a background
// thread while the keyboard waits for it.
//
// The Kotlin side is com.atlas.rime.RimeNative. Its methods are registered in JNI_OnLoad, so a package rename changes
// only kClass below.
#include <jni.h>
#include <rime_api.h>

#include <mutex>
#include <string>
#include <vector>

namespace {

const char* const kClass = "com/atlas/rime/RimeNative";

std::mutex lock;
RimeApi* rime = nullptr;
RimeSessionId session = 0;
bool started = false;
// The bar shows "page/pages" for up to this many pages of choices, and the page with an arrow past it.
const int kCountPages = 10;

jstring text(JNIEnv* env, const char* s) {
  return s ? env->NewStringUTF(s) : nullptr;
}

std::string chars(JNIEnv* env, jstring s) {
  if (!s) return {};
  const char* c = env->GetStringUTFChars(s, nullptr);
  std::string out(c ? c : "");
  if (c) env->ReleaseStringUTFChars(s, c);
  return out;
}

// The session, made again if Rime dropped it.
RimeSessionId live() {
  if (!started) return 0;
  if (session == 0 || !rime->find_session(session)) session = rime->create_session();
  return session;
}

jboolean start(JNIEnv* env, jclass, jstring shared, jstring user) {
  std::lock_guard<std::mutex> guard(lock);
  if (started) return JNI_TRUE;
  rime = rime_get_api();
  if (!rime) return JNI_FALSE;
  // RimeTraits keeps pointers: these must outlive initialize(), so they're static.
  static std::string sharedDir, userDir, prebuiltDir, stagingDir;
  sharedDir = chars(env, shared);
  userDir = chars(env, user);
  prebuiltDir = sharedDir + "/build";  // the compiled tables that ship in the app
  stagingDir = userDir + "/build";     // anything Rime compiles on the phone
  RIME_STRUCT(RimeTraits, traits);
  traits.shared_data_dir = sharedDir.c_str();
  traits.user_data_dir = userDir.c_str();
  traits.prebuilt_data_dir = prebuiltDir.c_str();
  traits.staging_dir = stagingDir.c_str();
  traits.distribution_name = "TitanKeys";
  traits.distribution_code_name = "titankeys";
  traits.distribution_version = "1";
  traits.app_name = "rime.titankeys";
  traits.min_log_level = 3;  // fatal only: nothing is logged
  traits.log_dir = "";
  rime->setup(&traits);
  rime->initialize(&traits);
  // A quick check, not a full one: the tables are already compiled.
  if (rime->start_maintenance(False)) rime->join_maintenance_thread();
  started = true;
  return live() != 0 ? JNI_TRUE : JNI_FALSE;
}

void stop(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  if (!started) return;
  if (session) rime->destroy_session(session);
  session = 0;
  rime->finalize();
  started = false;
}

jboolean selectSchema(JNIEnv* env, jclass, jstring schema) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return JNI_FALSE;
  return rime->select_schema(s, chars(env, schema).c_str()) ? JNI_TRUE : JNI_FALSE;
}

void setOption(JNIEnv* env, jclass, jstring name, jboolean on) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return;
  rime->set_option(s, chars(env, name).c_str(), on ? True : False);
}

jboolean getOption(JNIEnv* env, jclass, jstring name) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return JNI_FALSE;
  return rime->get_option(s, chars(env, name).c_str()) ? JNI_TRUE : JNI_FALSE;
}

jboolean key(JNIEnv*, jclass, jint keycode, jint mask) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return JNI_FALSE;
  return rime->process_key(s, keycode, mask) ? JNI_TRUE : JNI_FALSE;
}

jboolean pick(JNIEnv*, jclass, jint index) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s || index < 0) return JNI_FALSE;
  return rime->select_candidate_on_current_page(s, (size_t)index) ? JNI_TRUE : JNI_FALSE;
}

void clear(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (s) rime->clear_composition(s);
}

// Object[] { String commit, String input, String preedit,
//            int[] { page, lastPage, highlighted, caret, pageSize, choices in all or -1 for more than kCountPages pages },
//            String[] choices, String[] comments }
// commit is the text Rime let go of since the last call (taken, so it's returned once), or null.
jobjectArray state(JNIEnv* env, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return nullptr;
  jclass objectClass = env->FindClass("java/lang/Object");
  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray out = env->NewObjectArray(6, objectClass, nullptr);

  RIME_STRUCT(RimeCommit, commit);
  if (rime->get_commit(s, &commit)) {
    env->SetObjectArrayElement(out, 0, text(env, commit.text));
    rime->free_commit(&commit);
  }
  env->SetObjectArrayElement(out, 1, text(env, rime->get_input(s)));

  jint numbers[6] = {0, 1, 0, 0, 0, -1};
  std::vector<std::string> choices, comments;
  RIME_STRUCT(RimeContext, context);
  if (rime->get_context(s, &context)) {
    if (context.composition.length > 0 || context.composition.preedit) {
      env->SetObjectArrayElement(out, 2, text(env, context.composition.preedit));
      numbers[3] = context.composition.cursor_pos;
    }
    numbers[0] = context.menu.page_no;
    numbers[1] = context.menu.is_last_page ? 1 : 0;
    numbers[2] = context.menu.highlighted_candidate_index;
    numbers[4] = context.menu.page_size;
    for (int i = 0; i < context.menu.num_candidates; ++i) {
      const RimeCandidate& c = context.menu.candidates[i];
      choices.emplace_back(c.text ? c.text : "");
      comments.emplace_back(c.comment ? c.comment : "");
    }
    if (context.menu.num_candidates > 0) {
      if (context.menu.is_last_page) {
        numbers[5] = context.menu.page_no * context.menu.page_size + context.menu.num_candidates;
      } else {
        // How many choices there are, counted up to kCountPages pages' worth: Rime makes them as they're asked for, so a
        // short code with thousands of choices stays cheap. -1 when there are more.
        const int most = kCountPages * context.menu.page_size;
        RimeCandidateListIterator it = {0};
        int count = 0;
        if (rime->candidate_list_begin(s, &it)) {
          while (count <= most && rime->candidate_list_next(&it)) ++count;
          rime->candidate_list_end(&it);
        }
        numbers[5] = count <= most ? count : -1;
      }
    }
    rime->free_context(&context);
  }
  jintArray ints = env->NewIntArray(6);
  env->SetIntArrayRegion(ints, 0, 6, numbers);
  env->SetObjectArrayElement(out, 3, ints);
  jobjectArray texts = env->NewObjectArray((jsize)choices.size(), stringClass, nullptr);
  jobjectArray notes = env->NewObjectArray((jsize)comments.size(), stringClass, nullptr);
  for (size_t i = 0; i < choices.size(); ++i) {
    jstring t = env->NewStringUTF(choices[i].c_str());
    env->SetObjectArrayElement(texts, (jsize)i, t);
    env->DeleteLocalRef(t);
    jstring n = env->NewStringUTF(comments[i].c_str());
    env->SetObjectArrayElement(notes, (jsize)i, n);
    env->DeleteLocalRef(n);
  }
  env->SetObjectArrayElement(out, 4, texts);
  env->SetObjectArrayElement(out, 5, notes);
  return out;
}

const JNINativeMethod kMethods[] = {
    {"start", "(Ljava/lang/String;Ljava/lang/String;)Z", (void*)start},
    {"stop", "()V", (void*)stop},
    {"selectSchema", "(Ljava/lang/String;)Z", (void*)selectSchema},
    {"setOption", "(Ljava/lang/String;Z)V", (void*)setOption},
    {"getOption", "(Ljava/lang/String;)Z", (void*)getOption},
    {"key", "(II)Z", (void*)key},
    {"pick", "(I)Z", (void*)pick},
    {"clear", "()V", (void*)clear},
    {"state", "()[Ljava/lang/Object;", (void*)state},
};

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
  JNIEnv* env = nullptr;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
  jclass c = env->FindClass(kClass);
  if (!c) return JNI_ERR;
  if (env->RegisterNatives(c, kMethods, sizeof(kMethods) / sizeof(kMethods[0])) != JNI_OK) return JNI_ERR;
  return JNI_VERSION_1_6;
}

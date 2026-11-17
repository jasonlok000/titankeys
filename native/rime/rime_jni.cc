// TitanKeys' JNI layer for Rime: only what the bar's picker needs.
// Trime's librime_jni (GPL-3.0-or-later) is the reference; this is a much smaller layer written for TitanKeys.
//
// One Rime session for the whole keyboard. Every call takes the same lock, so start() can run on a background
// thread while the keyboard waits for it.
//
// The Kotlin side is com.titankeys.rime.RimeNative. Its methods are registered in JNI_OnLoad, so a package rename changes
// only kClass below.
#include <jni.h>
#include <rime_api.h>

#include "tk_predict.h"

#include <mutex>
#include <string>
#include <vector>

namespace {

const char* const kClass = "com/titankeys/rime/RimeNative";

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
  // No deploy: the tables ship compiled (data.sh), and a deploy compiles them again whenever the files' times differ from
  // the ones data.sh compiled with (a phone test, 2026-11-16). Only the deployer's tasks, which sync needs, and this
  // installation's name.
  rime->deployer_initialize(&traits);
  rime->run_task("installation_update");
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
  const auto* before = tk::LastCommit(s);
  const bool taken = rime->process_key(s, keycode, mask);
  // A phrase committed at once: its next words from what it ends with (tk_predict.h).
  tk::PredictFromEnd(s, before);
  return taken ? JNI_TRUE : JNI_FALSE;
}

jboolean pick(JNIEnv*, jclass, jint index) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s || index < 0) return JNI_FALSE;
  const auto* before = tk::LastCommit(s);
  const bool picked = rime->select_candidate_on_current_page(s, (size_t)index);
  tk::PredictFromEnd(s, before);
  return picked ? JNI_TRUE : JNI_FALSE;
}

// A flick: the pending code goes, else the last picked word (tk_predict.h).
jboolean dropLast(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return JNI_FALSE;
  return tk::DropLast(s) ? JNI_TRUE : JNI_FALSE;
}

// A word the keyboard typed itself, outside Rime: its next words come up (tk_predict.h).
jboolean predictAfter(JNIEnv* env, jclass, jstring text) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s) return JNI_FALSE;
  return tk::PredictAfter(s, chars(env, text)) ? JNI_TRUE : JNI_FALSE;
}

// Picks choice [index] counted across every page, as choices() gives them.
jboolean pickAt(JNIEnv*, jclass, jint index) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (!s || index < 0) return JNI_FALSE;
  const auto* before = tk::LastCommit(s);
  const bool picked = rime->select_candidate(s, (size_t)index);
  tk::PredictFromEnd(s, before);
  return picked ? JNI_TRUE : JNI_FALSE;
}

// Up to [count] choices from choice [start], across every page: the bar's row and its bigger menu read past Rime's page.
// Rime makes them as they're asked for, so asking for a few is cheap.
jobjectArray choices(JNIEnv* env, jclass, jint start, jint count) {
  std::lock_guard<std::mutex> guard(lock);
  jclass stringClass = env->FindClass("java/lang/String");
  std::vector<std::string> texts;
  RimeSessionId s = live();
  if (s && start >= 0 && count > 0) {
    RimeCandidateListIterator it = {0};
    if (rime->candidate_list_from_index(s, &it, start)) {
      while ((int)texts.size() < count && rime->candidate_list_next(&it))
        texts.emplace_back(it.candidate.text ? it.candidate.text : "");
      rime->candidate_list_end(&it);
    }
  }
  jobjectArray out = env->NewObjectArray((jsize)texts.size(), stringClass, nullptr);
  for (size_t i = 0; i < texts.size(); ++i) {
    jstring t = env->NewStringUTF(texts[i].c_str());
    env->SetObjectArrayElement(out, (jsize)i, t);
    env->DeleteLocalRef(t);
  }
  return out;
}

// Rime's own backup of the learned words: writes each user dictionary as a text snapshot in <user>/sync/<installation id>/,
// after merging in every snapshot already in <user>/sync/ (a restored backup's, for one). It closes the session first, so
// the next call makes a new one, with the default schema: the Kotlin side selects its schema again.
jboolean sync(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  if (!started) return JNI_FALSE;
  if (!rime->sync_user_data()) return JNI_FALSE;
  rime->join_maintenance_thread();
  session = 0;
  return JNI_TRUE;
}

void clear(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  RimeSessionId s = live();
  if (s) rime->clear_composition(s);
}

// Object[] { String commit, String input, String preedit,
//            int[] { page, lastPage, highlighted, caret, pageSize, choices in all or -1 for more than kCountPages pages,
//                    1 when the choices cover only part of the code (Rime couldn't read the rest) },
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

  jint numbers[7] = {0, 1, 0, 0, 0, -1, 0};
  std::vector<std::string> choices, comments;
  RIME_STRUCT(RimeContext, context);
  if (rime->get_context(s, &context)) {
    if (context.composition.length > 0 || context.composition.preedit) {
      env->SetObjectArrayElement(out, 2, text(env, context.composition.preedit));
      numbers[3] = context.composition.cursor_pos;
      if (context.composition.sel_end < context.composition.length) numbers[6] = 1;
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
  jintArray ints = env->NewIntArray(7);
  env->SetIntArrayRegion(ints, 0, 7, numbers);
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
    {"pickAt", "(I)Z", (void*)pickAt},
    {"predictAfter0", "(Ljava/lang/String;)Z", (void*)predictAfter},
    {"dropLast0", "()Z", (void*)dropLast},
    {"choiceList", "(II)[Ljava/lang/String;", (void*)choices},
    {"clear", "()V", (void*)clear},
    {"sync0", "()Z", (void*)sync},
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

// TitanKeys' JNI layer for Mozc: only what the bar needs, over tk_core (docs/languages.md, "Japanese").
// The Kotlin side is com.titankeys.mozc.MozcNative. Its methods are registered in JNI_OnLoad, the only symbol the library
// exports (tk.map). Every call takes one lock, so start() can run on a background thread.
#include <jni.h>

#include <mutex>
#include <string>
#include <vector>

#include "android/tk/tk_core.h"

namespace {

const char* const kClass = "com/titankeys/mozc/MozcNative";

std::mutex lock;
tk::Mozc mozc;

std::string chars(JNIEnv* env, jstring s) {
  if (!s) return {};
  const char* c = env->GetStringUTFChars(s, nullptr);
  std::string out(c ? c : "");
  if (c) env->ReleaseStringUTFChars(s, c);
  return out;
}

jboolean start(JNIEnv* env, jclass, jstring data, jstring profile) {
  std::lock_guard<std::mutex> guard(lock);
  return mozc.Start(chars(env, data), chars(env, profile)) ? JNI_TRUE : JNI_FALSE;
}

void stop(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  mozc.Stop();
}

jboolean key(JNIEnv*, jclass, jint c) {
  std::lock_guard<std::mutex> guard(lock);
  return mozc.Key(static_cast<char>(c)) ? JNI_TRUE : JNI_FALSE;
}

jboolean pick(JNIEnv*, jclass, jint index) {
  std::lock_guard<std::mutex> guard(lock);
  return mozc.Pick(index) ? JNI_TRUE : JNI_FALSE;
}

void clear(JNIEnv*, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  mozc.Reset();
}

// Object[] { String commit (taken, or null), String preedit, String[] choices }
jobjectArray state(JNIEnv* env, jclass) {
  std::lock_guard<std::mutex> guard(lock);
  jclass objectClass = env->FindClass("java/lang/Object");
  jclass stringClass = env->FindClass("java/lang/String");
  jobjectArray out = env->NewObjectArray(3, objectClass, nullptr);
  const std::string commit = mozc.TakeCommit();
  if (!commit.empty()) env->SetObjectArrayElement(out, 0, env->NewStringUTF(commit.c_str()));
  env->SetObjectArrayElement(out, 1, env->NewStringUTF(mozc.Preedit().c_str()));
  const std::vector<std::string> choices = mozc.Choices();
  jobjectArray texts = env->NewObjectArray(static_cast<jsize>(choices.size()), stringClass, nullptr);
  for (size_t i = 0; i < choices.size(); ++i) {
    jstring t = env->NewStringUTF(choices[i].c_str());
    env->SetObjectArrayElement(texts, static_cast<jsize>(i), t);
    env->DeleteLocalRef(t);
  }
  env->SetObjectArrayElement(out, 2, texts);
  return out;
}

const JNINativeMethod kMethods[] = {
    {"start", "(Ljava/lang/String;Ljava/lang/String;)Z", reinterpret_cast<void*>(start)},
    {"stop", "()V", reinterpret_cast<void*>(stop)},
    {"key", "(I)Z", reinterpret_cast<void*>(key)},
    {"pickAt", "(I)Z", reinterpret_cast<void*>(pick)},
    {"clear", "()V", reinterpret_cast<void*>(clear)},
    {"state", "()[Ljava/lang/Object;", reinterpret_cast<void*>(state)},
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

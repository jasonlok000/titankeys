#include <jni.h>
#include <algorithm>
#include <cmath>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>
#include "swipe_decoder/trie.hpp"

// This adapter uses FUTO's public Trie/ITrie API.
static_assert(sizeof(swipe_decoder::Trie) == 144, "Unexpected arm64 FUTO Trie ABI");
static constexpr TrieId EXTRA_WORD = 1u << 31;
struct Surface {
    std::string spelling;
    float frequency;
};
struct ExtraWord {
    TrieId node;
    Surface surface;
};
struct Dictionary {
    std::unique_ptr<swipe_decoder::Trie> trie;
    std::unordered_map<std::string, std::vector<Surface>> surfaces;
    std::unordered_map<TrieId, Surface> terminals;
    std::unordered_map<TrieId, std::vector<TrieId>> branches;
    std::vector<ExtraWord> extra;
    ITrie view;
    explicit Dictionary(std::unique_ptr<swipe_decoder::Trie> source) : trie(std::move(source)) {}
};
static Dictionary* dict(void* data) { return static_cast<Dictionary*>(data); }
static ITrie* base(void* data) { return dict(data)->trie->itrie(); }
static TrieId node(void* data, TrieId id) { return id & EXTRA_WORD ? dict(data)->extra[id & ~EXTRA_WORD].node : id; }
static const Surface* surface(void* data, TrieId id) {
    if (id & EXTRA_WORD) return &dict(data)->extra[id & ~EXTRA_WORD].surface;
    auto found = dict(data)->terminals.find(id);
    return found == dict(data)->terminals.end() ? nullptr : &found->second;
}
static int chars(void* data) { return base(data)->num_chars(); }
static TrieId root(void* data) { return base(data)->root(); }
static int character(void* data, TrieId id) { return base(data)->get_char_idx(node(data, id)); }
static uint32_t children(void* data, TrieId id) {
    if (id & EXTRA_WORD) return 0;
    auto found = dict(data)->branches.find(id);
    return found == dict(data)->branches.end() ? base(data)->get_child_count(id) : found->second.size();
}
static TrieId child(void* data, TrieId id, uint32_t index) {
    auto found = dict(data)->branches.find(id);
    return found == dict(data)->branches.end() ? base(data)->get_child(id, index) : found->second[index];
}
static bool is_word(void* data, TrieId id) { return (id & EXTRA_WORD) || base(data)->is_word(id); }
static float frequency(void* data, TrieId id) {
    const Surface* form = surface(data, id);
    return form ? form->frequency : base(data)->get_log_frequency(id);
}
static uint16_t depth(void* data, TrieId id) { return base(data)->get_depth(node(data, id)); }
static const char* word(void* data, TrieId id) {
    const Surface* form = surface(data, id);
    return form ? form->spelling.c_str() : base(data)->get_word(id);
}
static void end(void* data) { base(data)->end_search(); }
static const ITrieVTable functions = {chars, root, character, children, child, is_word, frequency, depth, word, end};
static jlong finish(std::unique_ptr<Dictionary> value) {
    value->view = {value.get(), &functions};
    return reinterpret_cast<jlong>(value.release());
}
static std::string text(JNIEnv* env, jstring value) {
    const char* raw = env->GetStringUTFChars(value, nullptr);
    if (!raw) throw std::bad_alloc();
    try {
        std::string copy(raw);
        env->ReleaseStringUTFChars(value, raw);
        return copy;
    } catch (...) {
        env->ReleaseStringUTFChars(value, raw);
        throw;
    }
}
static void error(JNIEnv* env, const char* message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message);
}
extern "C" JNIEXPORT jlong JNICALL
Java_org_futo_ml_inference_NativeDictionary_nativeLoad(JNIEnv* env, jclass, jstring path) {
    try {
        auto loaded = swipe_decoder::load_trie_simple(text(env, path));
        if (!loaded) { error(env, "Could not load the base dictionary"); return 0; }
        return finish(std::make_unique<Dictionary>(std::make_unique<swipe_decoder::Trie>(std::move(*loaded))));
    } catch (...) { error(env, "Could not load the base dictionary"); return 0; }
}
extern "C" JNIEXPORT jlong JNICALL
Java_org_futo_ml_inference_NativeDictionary_nativeCreate(JNIEnv* env, jclass, jobjectArray words, jfloatArray frequencies) {
    try {
        int count = env->GetArrayLength(words);
        if (count > 256 || count != env->GetArrayLength(frequencies)) { error(env, "Invalid personal dictionary size"); return 0; }
        auto value = std::make_unique<Dictionary>(std::make_unique<swipe_decoder::Trie>());
        for (int i = 0; i < count; i++) {
            auto surface = static_cast<jstring>(env->GetObjectArrayElement(words, i));
            std::string spelling = text(env, surface); env->DeleteLocalRef(surface);
            std::string alpha;
            for (char ch : spelling) {
                if (ch >= 'a' && ch <= 'z') alpha += ch;
                else if (ch != '\'') { error(env, "Unsupported personal word"); return 0; }
            }
            float freq; env->GetFloatArrayRegion(frequencies, i, 1, &freq);
            if (alpha.size() < 2 || alpha.size() > 24 || !std::isfinite(freq) || freq < 1 || freq > 255) {
                error(env, "Invalid personal dictionary entry"); return 0;
            }
            auto& forms = value->surfaces[alpha];
            auto found = std::find_if(forms.begin(), forms.end(), [&](const Surface& form) { return form.spelling == spelling; });
            if (found == forms.end()) forms.push_back({spelling, freq});
            else found->frequency = std::max(found->frequency, freq);
        }
        for (auto& group : value->surfaces) {
            auto& forms = group.second;
            std::sort(forms.begin(), forms.end(), [](const Surface& a, const Surface& b) {
                return a.frequency != b.frequency ? a.frequency > b.frequency : a.spelling < b.spelling;
            });
            value->trie->insert(group.first, forms.front().frequency, true);
        }
        ITrie* trie = value->trie->itrie();
        std::vector<TrieId> pending{trie->root()};
        while (!pending.empty()) {
            TrieId parent = pending.back(); pending.pop_back();
            std::vector<TrieId> children;
            bool expanded = false;
            for (uint32_t i = 0; i < trie->get_child_count(parent); i++) {
                TrieId id = trie->get_child(parent, i);
                pending.push_back(id);
                children.push_back(id);
                if (!trie->is_word(id)) continue;
                const auto& forms = value->surfaces.at(trie->get_word(id));
                value->terminals.emplace(id, forms.front());
                // Surface variants share the same letter path but remain distinct decoder leaves.
                for (size_t n = 1; n < forms.size(); n++) {
                    children.push_back(EXTRA_WORD | static_cast<TrieId>(value->extra.size()));
                    value->extra.push_back({id, forms[n]});
                    expanded = true;
                }
            }
            if (expanded) value->branches.emplace(parent, std::move(children));
        }
        return finish(std::move(value));
    } catch (...) { error(env, "Could not create the personal dictionary"); return 0; }
}
extern "C" JNIEXPORT jlong JNICALL
Java_org_futo_ml_inference_NativeDictionary_nativePointer(JNIEnv*, jclass, jlong handle) {
    return reinterpret_cast<jlong>(&reinterpret_cast<Dictionary*>(handle)->view);
}
extern "C" JNIEXPORT jfloat JNICALL
Java_org_futo_ml_inference_NativeDictionary_nativeFrequency(JNIEnv* env, jclass, jlong handle, jstring value) {
    try {
        Dictionary* dictionary = reinterpret_cast<Dictionary*>(handle);
        if (!dictionary->surfaces.empty()) {
            std::string spelling = text(env, value);
            std::string alpha = spelling;
            alpha.erase(std::remove(alpha.begin(), alpha.end(), '\''), alpha.end());
            const auto& groups = dictionary->surfaces;
            auto found = groups.find(alpha);
            if (found == groups.end()) return 0;
            for (const Surface& form : found->second) if (form.spelling == spelling) return form.frequency;
            return 0;
        }
        ITrie* trie = &dictionary->view;
        TrieId node = trie->root();
        for (char ch : text(env, value)) {
            if (ch == '\'') continue;
            bool found = false;
            for (uint32_t i = 0; i < trie->get_child_count(node); i++) {
                TrieId next = trie->get_child(node, i);
                if (trie->get_char_idx(next) == ch - 'a') { node = next; found = true; break; }
            }
            if (!found) return 0;
        }
        return trie->is_word(node) ? trie->get_log_frequency(node) : 0;
    } catch (...) { error(env, "Could not query the dictionary"); return 0; }
}
extern "C" JNIEXPORT void JNICALL
Java_org_futo_ml_inference_NativeDictionary_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<Dictionary*>(handle);
}

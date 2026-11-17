// TitanKeys' next-word fallback for librime-predict (#56; docs/languages.md, "Next-word suggestions"). The predictor looks up
// the whole last commit, so a phrase committed at once (我哋今日, a whole sentence typed before Space) gets no suggestions.
// After a commit with none, this tries what the phrase ends with instead, longest first, down to its last character: the
// predictor's own lookup on its own table, through Rime's commit history, so the suggestions are Rime's choices as any
// others, and a pick goes on from them as usual. Used by rime_jni.cc (the app) and rime_tool.cc (data.sh's checks).
#ifndef TK_PREDICT_H_
#define TK_PREDICT_H_

#include <rime/commit_history.h>
#include <rime/context.h>
#include <rime/service.h>
#include <rime_api.h>

#include <string>
#include <vector>

namespace tk {

// The last commit record, to tell afterwards whether a call committed anything: a new commit is a new record.
inline const rime::CommitRecord* LastCommit(RimeSessionId id) {
  auto session = rime::Service::instance().GetSession(id);
  if (!session || !session->context()) return nullptr;
  const auto& history = session->context()->commit_history();
  return history.empty() ? nullptr : &history.back();
}

// After a call that may have committed: if it did, and the predictor offered nothing for the whole commit, the commit's
// endings are tried, longest first. True if suggestions came. [before] is LastCommit() from before the call.
inline bool PredictFromEnd(RimeSessionId id, const rime::CommitRecord* before) {
  auto session = rime::Service::instance().GetSession(id);
  if (!session) return false;
  rime::Context* ctx = session->context();
  if (!ctx || !ctx->composition().empty() || !ctx->get_option("prediction")) return false;
  auto& history = ctx->commit_history();
  if (history.empty() || &history.back() == before) return false;
  const rime::CommitRecord last = history.back();
  // As the predictor: nothing after a mark, raw letters or a key Rime let through.
  if (last.type == "punct" || last.type == "raw" || last.type == "thru") return false;
  std::vector<size_t> starts;
  for (size_t i = 0; i < last.text.size(); ++i)
    if ((static_cast<unsigned char>(last.text[i]) & 0xC0) != 0x80) starts.push_back(i);
  for (size_t k = 1; k < starts.size(); ++k) {
    history.push_back(rime::CommitRecord(last.type, last.text.substr(starts[k])));
    ctx->update_notifier()(ctx);
    if (!ctx->composition().empty()) return true;
    history.pop_back();
  }
  return false;
}

}  // namespace tk

#endif  // TK_PREDICT_H_

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

// A word typed outside Rime (a pick from the bar's next words, which the keyboard typed itself): Rime hears of it as a commit,
// so the predictor offers what follows it, and the endings fallback after that. True if suggestions came. Nothing while Rime
// is composing.
inline bool PredictAfter(RimeSessionId id, const std::string& text) {
  auto session = rime::Service::instance().GetSession(id);
  if (!session || text.empty()) return false;
  rime::Context* ctx = session->context();
  if (!ctx || ctx->IsComposing() || !ctx->get_option("prediction")) return false;
  ctx->commit_history().Push(rime::CommitRecord("tk_word", text));
  ctx->update_notifier()(ctx);
  if (!ctx->composition().empty()) return true;
  return PredictFromEnd(id, nullptr);
}

// A flick with a code being built (owner, 2026-11-17): the code still pending goes, and what was picked of it stays (頂你個
// picked, "fai" pending: "fai" goes). True if anything went: false with nothing pending, and the keyboard then types what was
// picked, less its last character (RimeEngine.flick), without Rime committing it, so it learns nothing from it.
inline bool DropLast(RimeSessionId id) {
  auto session = rime::Service::instance().GetSession(id);
  if (!session) return false;
  rime::Context* ctx = session->context();
  if (!ctx || ctx->input().empty()) return false;
  const size_t length = ctx->input().length();
  ctx->set_caret_pos(length);
  size_t picked = 0;
  for (const auto& segment : ctx->composition()) {
    if (segment.status >= rime::Segment::kSelected) picked = segment.end;
  }
  if (picked == 0 || picked >= length) return false;
  return ctx->PopInput(length - picked);
}

}  // namespace tk

#endif  // TK_PREDICT_H_

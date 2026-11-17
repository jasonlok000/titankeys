// TitanKeys' Japanese engine: see tk_core.h. Based on Mozc's own android/jni/mozcjni.cc (BSD-3-Clause, Google) and its
// session tool, written for TitanKeys.
#include "android/tk/tk_core.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/status/statusor.h"
#include "base/system_util.h"
#include "data_manager/data_manager.h"
#include "engine/engine.h"
#include "protocol/candidate_window.pb.h"
#include "protocol/commands.pb.h"
#include "session/session_handler.h"

namespace tk {

using mozc::commands::Command;
using mozc::commands::Input;
using mozc::commands::KeyEvent;
using mozc::commands::Output;
using mozc::commands::Request;
using mozc::commands::SessionCommand;

namespace {
// Input types and session commands, as Send() takes them.
constexpr int kNone = -1;
}  // namespace

Mozc::Mozc() : last_(std::make_unique<Output>()) {}
Mozc::~Mozc() { Stop(); }

bool Mozc::Start(const std::string& data, const std::string& profile, bool desktop) {
  if (handler_) return true;
  mozc::SystemUtil::SetUserProfileDirectory(profile);
  absl::StatusOr<std::unique_ptr<const mozc::DataManager>> manager = mozc::DataManager::CreateFromFile(data);
  if (!manager.ok()) return false;
  auto engine = mozc::Engine::CreateEngine(*std::move(manager));
  if (!engine.ok()) return false;
  handler_ = std::make_unique<mozc::SessionHandler>(*std::move(engine));

  Command create;
  create.mutable_input()->set_type(Input::CREATE_SESSION);
  create.mutable_input()->mutable_capability()->set_text_deletion(mozc::commands::Capability::DELETE_PRECEDING_TEXT);
  handler_->EvalCommand(&create);
  if (create.output().error_code() != Output::SESSION_SUCCESS) {
    handler_.reset();
    return false;
  }
  session_ = create.output().id();

  if (!desktop) {
    // Android's way (Mozc's own mobile request), with the QWERTY romaji table: choices as you type, whole sentences
    // first, and the parts of a sentence among them, so a pick can take a part and leave the rest.
    Command request;
    request.mutable_input()->set_id(session_);
    request.mutable_input()->set_type(Input::SET_REQUEST);
    Request* r = request.mutable_input()->mutable_request();
    r->set_zero_query_suggestion(false);
    r->set_mixed_conversion(true);
    r->set_update_input_mode_from_surrounding_text(false);
    r->set_special_romanji_table(Request::QWERTY_MOBILE_TO_HIRAGANA);
    r->set_kana_modifier_insensitive_conversion(false);
    r->set_auto_partial_suggestion(true);
    r->set_language_aware_input(Request::NO_LANGUAGE_AWARE_INPUT);
    handler_->EvalCommand(&request);
  }
  // Japanese input on, in hiragana.
  Output out;
  Send(&out, Input::SEND_KEY, kNone, 0, 0, KeyEvent::ON);
  Command mode;
  mode.mutable_input()->set_id(session_);
  mode.mutable_input()->set_type(Input::SEND_COMMAND);
  mode.mutable_input()->mutable_command()->set_type(SessionCommand::SWITCH_COMPOSITION_MODE);
  mode.mutable_input()->mutable_command()->set_composition_mode(mozc::commands::HIRAGANA);
  handler_->EvalCommand(&mode);
  last_->Clear();
  commit_.clear();
  return true;
}

void Mozc::Stop() {
  if (!handler_) return;
  Command del;
  del.mutable_input()->set_id(session_);
  del.mutable_input()->set_type(Input::DELETE_SESSION);
  handler_->EvalCommand(&del);
  handler_.reset();
  last_->Clear();
  commit_.clear();
}

bool Mozc::Send(Output* out, int type, int command, int id, char key, int special) {
  if (!handler_) return false;
  Command c;
  Input* input = c.mutable_input();
  input->set_id(session_);
  input->set_type(static_cast<Input::CommandType>(type));
  if (type == Input::SEND_KEY) {
    if (special != kNone) input->mutable_key()->set_special_key(static_cast<KeyEvent::SpecialKey>(special));
    else input->mutable_key()->set_key_code(static_cast<unsigned char>(key));
  } else if (command != kNone) {
    input->mutable_command()->set_type(static_cast<SessionCommand::CommandType>(command));
    if (command == SessionCommand::SUBMIT_CANDIDATE) input->mutable_command()->set_id(id);
  }
  if (!handler_->EvalCommand(&c)) return false;
  *out = c.output();
  if (out->has_result()) commit_ += out->result().value();
  return out->consumed();
}

bool Mozc::Key(char key) { return Send(last_.get(), Input::SEND_KEY, kNone, 0, key, kNone); }

bool Mozc::Space() { return Send(last_.get(), Input::SEND_KEY, kNone, 0, 0, KeyEvent::SPACE); }

bool Mozc::Pick(int index) {
  const auto& words = last_->all_candidate_words().candidates();
  if (index < 0 || index >= words.size()) return false;
  const int id = words[index].id();
  Send(last_.get(), Input::SEND_COMMAND, SessionCommand::SUBMIT_CANDIDATE, id, 0, kNone);
  return true;
}

void Mozc::Reset() {
  Output out;
  Send(&out, Input::SEND_COMMAND, SessionCommand::REVERT, 0, 0, kNone);
  last_->Clear();
}

std::string Mozc::TakeCommit() {
  std::string out;
  out.swap(commit_);
  return out;
}

std::string Mozc::Preedit() const {
  std::string text;
  for (const auto& segment : last_->preedit().segment()) text += segment.value();
  return text;
}

std::vector<std::string> Mozc::Choices() const {
  std::vector<std::string> out;
  for (const auto& word : last_->all_candidate_words().candidates()) out.push_back(word.value());
  return out;
}

}  // namespace tk

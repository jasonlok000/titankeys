// TitanKeys' Japanese engine: Mozc's session handler, driven the way the bar needs it (docs/languages.md, "Japanese").
// One session, in Mozc's Android mode: romaji in, hiragana built as it's typed, and every choice for it at once, whole
// sentences first. The JNI layer (tk_jni.cc) and the PC tool (tk_tool.cc) both use this, so the PC checks run the app's path.
#ifndef TK_MOZC_CORE_H_
#define TK_MOZC_CORE_H_

#include <memory>
#include <string>
#include <vector>

namespace mozc {
class SessionHandler;
namespace commands {
class Output;
}  // namespace commands
}  // namespace mozc

namespace tk {

class Mozc {
 public:
  Mozc();
  ~Mozc();

  // Opens Mozc on its data file, keeping the user's history in [profile]. [desktop] is Mozc's desktop way instead, where
  // Space converts: only the PC tool uses it, to compare. False if the data can't be read.
  bool Start(const std::string& data, const std::string& profile, bool desktop = false);
  void Stop();
  bool started() const { return handler_ != nullptr; }

  // One printable key, as Mozc's romaji table takes it (a to z, - for ー).
  bool Key(char key);
  // Space: in the desktop way, converts. Not used in Android's way, where the keyboard picks the first choice.
  bool Space();
  // Picks the choice at [index] in Choices(): what it covers is typed, and the rest of the text stays to convert.
  bool Pick(int index);
  // Drops the text being built.
  void Reset();

  // What Mozc let go of since the last call, taken.
  std::string TakeCommit();
  // The text being built, as Mozc shows it (hiragana, with the romaji not yet a kana at its end).
  std::string Preedit() const;
  // Every choice for it, best first.
  std::vector<std::string> Choices() const;

 private:
  bool Send(mozc::commands::Output* out, int type, int command, int id, char key, int special);
  std::unique_ptr<mozc::SessionHandler> handler_;
  std::unique_ptr<mozc::commands::Output> last_;
  unsigned long long session_ = 0;
  std::string commit_;
};

}  // namespace tk

#endif  // TK_MOZC_CORE_H_

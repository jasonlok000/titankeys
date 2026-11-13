#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
sdk=${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
engine=android/kotlin/com/atlas/engine
javac --release 17 -cp "$sdk/platforms/android-36/android.jar" -d "$work" \
  "$engine/LanguageHabits.java" "$engine/SwipeWords.java" "$engine/SuggestionPolicy.java" \
  "$engine/BlockedWords.java" "$engine/ForgetWords.java" "$engine/TypedPredictions.java" "$engine/BuiltInWords.java" "$engine/SentenceContext.java" \
  "$engine/NextWordPredictor.java" "$engine/UserNGramRepository.java" "$engine/KeptWords.java" "$engine/SentenceCase.java" "$engine/PhoneNames.java" \
  "$engine/EnglishSpelling.java" "$engine/SwipeSpelling.java" "$engine/ProperNames.java" \
  tests/words/SavedWordsChecks.java tests/words/BlockedWordsChecks.java tests/words/ContextSuggestionChecks.java \
  tests/words/NextWordChecks.java tests/words/SentenceCaseChecks.java tests/words/ForgetWordsChecks.java tests/words/PhoneNamesChecks.java \
  tests/words/EnglishSpellingChecks.java
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.SavedWordsChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.BlockedWordsChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.ContextSuggestionChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.NextWordChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.SentenceCaseChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.ForgetWordsChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.PhoneNamesChecks
java -cp "$work:$sdk/platforms/android-36/android.jar" com.atlas.engine.EnglishSpellingChecks
# The FUTO engine's native personal list refuses more words than SwipeTyping sends it, which would stop every swipe.
grep -q 'count > 256' native/personal_dictionary.cpp && grep -q 'PERSONAL_LIMIT = 256;' "$engine/SwipeTyping.java" \
  || { echo "SwipeTyping.PERSONAL_LIMIT must match the native personal list's limit"; exit 1; }
echo "Personal list limit matches the native library"

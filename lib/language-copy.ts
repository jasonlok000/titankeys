import originals from "../tools/im/page-text.json";
import translations from "../tools/im/native-pages.json";

type Dictionary = Record<string, string>;
const dictionaries: Record<string, Dictionary> = translations;
const cache = new Map<string, typeof originals>();

// Resolve the input method first, since Cantonese and Mandarin can use different instructions within 中文.
export function translator(id?: string) {
  const parent = id?.startsWith("zh-") || id?.startsWith("yue-") ? "zh" : id;
  return (source: string) => dictionaries[id ?? ""]?.[source] ?? dictionaries[parent ?? ""]?.[source] ?? source;
}

export function textFor(id?: string): typeof originals {
  if (!id || id === "en") return originals;
  const stored = cache.get(id);
  if (stored) return stored;
  const translate = translator(id);
  const result: typeof originals = JSON.parse(JSON.stringify(originals), (_key, value: unknown) =>
    typeof value === "string" ? translate(value) : value);
  cache.set(id, result);
  return result;
}

export function guidePracticeLabel(id?: string) {
  if (!id || id === "en") return originals.page.guidePractice;
  const { how, tryIt } = textFor(id).page;
  return `${how} · ${tryIt}`;
}

import { expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import guide from "../../android/assets/im/zh-zhuyin/im.json";

test("the Hsu diagram shows each initial, final and tone on its Rime key", () => {
  const schema = readFileSync("native/rime/schemas/tk_zhuyin.schema.yaml", "utf8");
  const keys = schema.match(/xlit\|[^|]+\|([a-z]+)\|/)![1];
  const symbols = "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦˊˇˋ˙";
  expect(keys.length).toBe(symbols.length);
  const labels: Record<string, string> = { q: "" };
  [...symbols].forEach((symbol, i) => { labels[keys[i]] = (labels[keys[i]] ?? "") + symbol; });
  expect(Object.keys(guide.guide.keys.map)).toHaveLength(26);
  expect(guide.guide.keys.map.q).toBe("");
  expect(guide.guide.keys.map).toEqual(labels);
});

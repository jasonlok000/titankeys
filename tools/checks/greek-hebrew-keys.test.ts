import { expect, test } from "bun:test";
import greek from "../../android/assets/im/el/im.json";
import hebrew from "../../android/assets/im/he/im.json";
import labels from "../../tools/im/page-text.json";

test("Greek Q keeps its punctuation on both layers and on the diagram", () => {
  expect(greek.keys.q.split("/")).toEqual([";", ":"]);
  expect(greek.guide.keys.map.q).toBe(";");
  expect(greek.guide.keys.shift.q).toBe(":");
});

test("Hebrew's Shift legend describes the unchanged letter", () => {
  expect(hebrew.guide.keys.roles.find(item => item.key === "shift")?.role).toBe("sameLetter");
  expect(labels.roles.shift.sameLetter).toBeTruthy();
});

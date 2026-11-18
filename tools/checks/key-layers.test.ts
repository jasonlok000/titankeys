import { expect, test } from "bun:test";
import cangjie from "../../android/assets/im/zh-cangjie/im.json";
import quick from "../../android/assets/im/zh-quick/im.json";
import thai from "../../android/assets/im/th/im.json";
import korean from "../../android/assets/im/ko/im.json";
import spanish from "../../android/assets/im/es/im.json";
import { keyFace, keyLayers } from "../../lib/key-layers";

test("both shape guides contain every physical key in keyboard order", () => {
  for (const method of [cangjie, quick]) {
    const { keys } = method.guide;
    expect(Array.from("qwertyuiopasdfghjklzxcvbnm", k => keyFace(keys, "base", k, method.id)).join(""))
      .toBe("手田水口廿卜山戈人心日尸木火土竹十大中符難金女月弓一");
    expect(keyLayers(keys)).toEqual(["base"]);
  }
});

test("Shift, hold and shifted hold each show their actual outputs", () => {
  expect(keyFace(korean.guide.keys, "shift", "q", "ko")).toBe("ㅃ");
  expect(keyFace(korean.guide.keys, "shift", "a", "ko")).toBe("ㅁ");
  expect(keyLayers(thai.guide.keys)).toEqual(["base", "shift", "held", "heldShift"]);
  for (const [key, values] of Object.entries(thai.keys)) {
    if (Array.isArray(values) && values.length === 4) {
      expect(keyFace(thai.guide.keys, "heldShift", key, "th")).toBe(values[3]);
    }
  }
  expect(keyFace(spanish.guide.keys, "held", "u", "es")).toBe("úü");
  expect(keyFace(spanish.guide.keys, "heldShift", "u", "es")).toBe("ÚÜ");
});

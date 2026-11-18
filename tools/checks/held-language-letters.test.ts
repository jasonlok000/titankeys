import { expect, test } from "bun:test";
import af from "../../android/assets/im/af/im.json";
import bg from "../../android/assets/im/bg/im.json";
import mk from "../../android/assets/im/mk/im.json";

test("Afrikaans holds can type môre, brûe and appèl", () => {
  expect(af.accents.o).toContain("ô");
  expect(af.accents.u).toContain("û");
  expect(af.accents.e).toContain("è");
});

test("Bulgarian and Macedonian can type their stressed short forms", () => {
  expect(bg.accents.i).toContain("ѝ");
  expect(mk.accents.i).toContain("ѝ");
  expect(mk.accents.e).toContain("ѐ");
});

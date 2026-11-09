import { expect, test } from "bun:test";
import { searchGuide } from "../../lib/guide";

const titles = (query: string) => searchGuide(query)?.map(row => row.title);

const cases: [query: string, first: string][] = [
  ["cursor", "Move the cursor"],
  ["how do i delete a word", "Delete a word"],
  ["deleting words", "Delete a word"],
  ["swpie", "Swipe a word"],
  ["caps lock", "Shift and Alt"],
  ["period", "Full stop"],
  ["autocorrect", "Word suggestions"],
  ["export", "Backup"],
  ["emoji not working", "Sym does not show emoji"],
  ["swipe not working", "Swiping does not operate in an app"],
  ["my keyboard wont show", "Check the setup"],
  ["where are the capital letters", "Shift and Alt"],
  ["how do i get emojis", "Emoji"],
  ["question mark", "More symbols"],
  ["it keeps suggesting a word", "New words"],
  ["swipe cuts off", "Slip recovery"],
];

for (const [query, first] of cases) {
  test(`guide search: "${query}" finds ${first}`, () => {
    expect(titles(query)?.[0]).toBe(first);
  });
}

test("guide search shows only the clear match", () => {
  expect(titles("swpie")).toEqual(["Swipe a word"]);
  expect(titles("caps lock")).toEqual(["Shift and Alt"]);
});

test("guide search shows the full guide until there is a word to search for", () => {
  for (const query of ["", " ", "s", "the", "how do i"]) expect(searchGuide(query)).toBeNull();
});

test("guide search shows no results for unknown words", () => {
  expect(titles("zzzz")).toEqual([]);
});

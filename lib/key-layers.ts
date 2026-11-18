import type { Guide } from "./titankeys";

type Keys = NonNullable<Guide["keys"]>;
export type KeyLayer = "base" | "shift" | "held" | "heldShift";

export function keyLayers(keys: Keys): KeyLayer[] {
  const layers: KeyLayer[] = ["base"];
  if (keys.capitals || keys.shift) layers.push("shift");
  if (keys.held || keys.accents) layers.push("held");
  if (keys.heldShift || (keys.capitals && keys.accents)) layers.push("heldShift");
  return layers;
}

export function keyFace(keys: Keys, layer: KeyLayer, letter: string, language: string, unmapped = false) {
  const base = keys.map?.[letter] ?? (unmapped ? "" : letter);
  if (layer === "base") return base;
  if (layer === "shift") return keys.shift?.[letter] ?? (keys.capitals ? base.toLocaleUpperCase(language) : base);
  const held = keys.held?.[letter];
  const accents = keys.accents?.[letter] ?? "";
  if (layer === "held") return held ?? accents;
  return keys.heldShift?.[letter] ?? held ?? (keys.capitals ? accents.toLocaleUpperCase(language) : accents);
}

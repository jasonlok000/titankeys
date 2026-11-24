/**
 * Settings > Accent colour's Custom (#111): a hue as Accent.kt's hue() draws it, for the hue bar and the live swatch. It mirrors
 * Accent.kt (HUE_SATURATION, HUE_LIGHTNESS, MIN_LIGHTNESS, MAX_LIGHTNESS, fit), and tools/checks/accent.test.ts checks the two
 * agree on the numbers and that every hue it gives reads on every surface.
 */
export const HUE_SATURATION = .85;
export const HUE_LIGHTNESS = .6;
export const MIN_LIGHTNESS = .22;
export const MAX_LIGHTNESS = .36;

type Rgb = [number, number, number];

const channel = (value: number) => { const c = value / 255; return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4; };
const luminance = ([r, g, b]: Rgb) => .2126 * channel(r) + .7152 * channel(g) + .0722 * channel(b);
const mix = (from: Rgb, to: Rgb, amount: number) => from.map((c, i) => Math.round(c * (1 - amount) + to[i] * amount)) as Rgb;
const hex = (rgb: Rgb) => "#" + rgb.map(c => c.toString(16).padStart(2, "0").toUpperCase()).join("");

/** Blended towards white while too dark, towards black while too light, until inside the band (Accent.fit). */
function fit(rgb: Rgb): Rgb {
  let result = rgb;
  for (let step = 0; step < 64; step++) {
    const l = luminance(result);
    if (l < MIN_LIGHTNESS + .005) result = mix(result, [255, 255, 255], .04);
    else if (l > MAX_LIGHTNESS - .005) result = mix(result, [0, 0, 0], .04);
    else return result;
  }
  return result;
}

/** The colour Custom draws for [degrees], as "#RRGGBB". */
export function hueColour(degrees: number): string {
  const h = ((Math.round(degrees) % 360) + 360) % 360;
  const a = HUE_SATURATION * Math.min(HUE_LIGHTNESS, 1 - HUE_LIGHTNESS);
  const part = (n: number) => {
    const k = (n + h / 30) % 12;
    return Math.round(255 * (HUE_LIGHTNESS - a * Math.max(-1, Math.min(k - 3, 9 - k, 1))));
  };
  return hex(fit([part(0), part(8), part(4)]));
}

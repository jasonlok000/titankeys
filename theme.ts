import { defineTheme, defineToggle } from "ink/theme";

// Pure black with orange accents. Same names and values as lib/brand.ts, which a theme can't import.
// The orange of the Titan 2 Elite, sampled from Unihertz's product photos, marks titles, section labels, values and
// switches that are on. Everything else is cream on black, with muted cream for what's secondary.
const black = "#000000";
const orange = "#EF804E";
const cream = "#FFF1E6";
const muted = "#99918A";
// Only for the knob on an orange switch.
const ink = "#160A04";

export default defineTheme({
  fonts: { body: "./assets/fonts/Inter-Regular.otf", display: "./assets/fonts/Inter-Medium.otf" },
  // Without its own light colours, a phone in light mode would get the inverse of these.
  colours: {
    background: black, foreground: cream, muted, accent: orange,
    light: { background: black, foreground: cream, muted, accent: orange },
  },
  text: { size: 45 },
  spacing: { inset: 92, gap: 69, rowGap: 52 },
  // Ink clips the first row's top edge at the scroll viewport.
  hitSlop: { top: 16, right: 18, bottom: 30, left: 18 },
  header: { layout: "large", size: 84, top: 21, bottom: 51, cutout: "below", back: { position: "end", icon: "arrow" }, iconSize: 60 },
  section: { uppercase: true, size: 31, gap: 48 },
  // Subtitles, row and switch descriptions and a screen's fine print share one size: 39 pixels. Rows are 45. lib/note.tsx's
  // Note draws explanations at the same size. A theme pixel is a screen pixel on the Titan 2 Elite (1076 / 1080), and a dp is
  // 1.5625 of them (FixedScale), so: titles 54 dp, rows 29, notes 25, section labels 20. tools/checks/ink-sizes.test.ts keeps
  // them at or above the owner's floor: rows 18 dp, notes 15, section labels 14.
  subtitle: { size: 39, tone: "muted" },
  scrollBar: { colour: muted, visibility: "scrolling", track: false, width: 8, inset: 30 },
  field: "inline",
  selection: "check",
  // The medium radius from lib/radii.ts, which tools/checks/radii.test.ts checks. A theme cannot import it.
  textInput: { radius: 32 },
  toggle: defineToggle({
    // Ink's toggle row is 69 tall, and a taller switch is cut off at the top of a screen.
    width: 118,
    height: 69,
    render: ({ on, disabled }) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130 76"><g opacity="${disabled ? 0.4 : 1}">
      <rect x="2.5" y="2.5" width="125" height="71" rx="35.5" fill="${on ? orange : "none"}" stroke="${on ? orange : muted}" stroke-width="5"/>
      <circle cx="${on ? 92 : 38}" cy="38" r="25" fill="${on ? ink : muted}"/>
    </g></svg>`,
  }),
  variants: {
    onboarding: { header: { top: 0, bottom: 0, cutout: "beside" } },
  },
});

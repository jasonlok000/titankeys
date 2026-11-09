import { defineTheme, defineToggle } from "ink/theme";

// The orange of the Titan 2 Elite, sampled from Unihertz's product photos. Same values as lib/brand.ts.
const orange = "#EF804E";
const ink = "#160A04";
const muted = "#522610";

export default defineTheme({
  fonts: { body: "./assets/fonts/Inter-Regular.otf", display: "./assets/fonts/Inter-Medium.otf" },
  // Without its own light colours, a phone in light mode would get the inverse of this orange.
  colours: { background: orange, foreground: ink, muted, light: { background: orange, foreground: ink, muted } },
  text: { size: 45 },
  spacing: { inset: 92, gap: 69, rowGap: 52 },
  // Ink clips the first row's top edge at the scroll viewport.
  hitSlop: { top: 16, right: 18, bottom: 30, left: 18 },
  header: { layout: "large", size: 84, top: 21, bottom: 51, cutout: "below", back: { position: "end", icon: "arrow" }, iconSize: 60 },
  section: { uppercase: true, size: 31, gap: 48 },
  subtitle: { size: 30, tone: "muted" },
  scrollBar: { colour: muted, visibility: "scrolling", track: false, width: 8, inset: 30 },
  field: "inline",
  selection: "check",
  toggle: defineToggle({
    // Ink's toggle row is 69 tall, and a taller switch is cut off at the top of a screen.
    width: 118,
    height: 69,
    render: ({ on, disabled }) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 130 76"><g opacity="${disabled ? 0.4 : 1}">
      <rect x="2.5" y="2.5" width="125" height="71" rx="35.5" fill="${on ? ink : "none"}" stroke="${ink}" stroke-width="5"/>
      <circle cx="${on ? 92 : 38}" cy="38" r="25" fill="${on ? orange : ink}"/>
    </g></svg>`,
  }),
  variants: {
    onboarding: { header: { top: 0, bottom: 0, cutout: "beside" } },
  },
});

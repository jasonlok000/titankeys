# TitanKeys's Ink patch

`titankeys.patch` adds what the Settings screens need to [Ink](https://github.com/No-Scrolling/ink), on top of Ink 8a833ad. Apply it to the Ink checkout beside TitanKeys before you build:

```bash
cd ../ink && git apply ../titankeys/tools/ink/titankeys.patch
```

It adds:

- An `accent` theme colour, for screen titles, section titles and field values.
- `tone` on `Text`: `"muted"` for notes, `"accent"` for what stands out.
- Wrapped descriptions: a switch's description always wraps, and a row's wraps with `wrapSubtitle`.
- A screen's `note` at the theme's subtitle size, so fine print is never smaller than a subtitle.
- A short fade under the header, so content scrolls away softly instead of being cut.
- `setRouteParents`, so a link into a Settings page lands with its parent pages under it, and ← goes up.
- `appBaseContext`, which the app defines beside `createAppNativeAdapters`: the activity's base context, so TitanKeys can keep
  Ink's activity at fixed sizes whatever the phone's display size or font size.

import { useEffect, useState } from "react";
import { Field, Screen, Section, useSnapshot } from "ink";
import { apps, captureKey, setShortcut, state, type App } from "../../../lib/atlas";

const subtitle = "Hold Fn and push a letter to open an app.";

/** An app on the page: installed, or only known from its shortcut after it was removed. */
type Listed = { app: string; name: string; icon: string | null; letter: string | null };

const byName = (a: Listed, b: Listed) => a.name.localeCompare(b.name);

export default function OpenApps() {
  const settings = useSnapshot(state);
  const installed = useSnapshot(apps);
  /** The app that waits for a letter. */
  const [waiting, setWaiting] = useState<string | null>(null);
  /** A letter that another app uses, pushed once. Pushed again, it moves to the waiting app. */
  const [taken, setTaken] = useState<string | null>(null);
  const shortcuts = settings.status === "ready" ? settings.data.shortcuts : [];

  useEffect(() => {
    if (!waiting) return;
    return captureKey(key => {
      const own = shortcuts.find(shortcut => shortcut.app === waiting);
      if (key === "Backspace") {
        if (own) void setShortcut(own.letter, null);
      } else if (key !== " ") {
        const owner = shortcuts.find(shortcut => shortcut.letter === key);
        if (owner && owner.app !== waiting && taken !== key) return setTaken(key);
        if (owner?.app !== waiting) void setShortcut(key, waiting);
      } else return;
      setWaiting(null);
      setTaken(null);
    });
  }, [waiting, taken, shortcuts]);

  if (settings.status !== "ready" || installed.status !== "ready") return <Screen title="Quick open" subtitle={subtitle} />;

  const letters = new Map(shortcuts.map(shortcut => [shortcut.app, shortcut.letter]));
  const listed: Listed[] = installed.data.map((app: App) => ({ ...app, letter: letters.get(app.app) ?? null }));
  const known = new Set(installed.data.map(app => app.app));
  for (const shortcut of shortcuts)
    if (!known.has(shortcut.app)) listed.push({ app: shortcut.app, name: shortcut.name, icon: null, letter: shortcut.letter });

  const value = ({ app, letter }: Listed) => {
    if (app !== waiting) return letter ?? "";
    if (taken) {
      const owner = shortcuts.find(shortcut => shortcut.letter === taken);
      return `${owner?.name} uses ${taken}. Push ${taken} again`;
    }
    return letter ? "Push a letter or Backspace" : "Push a letter";
  };
  const touch = (app: string) => {
    setTaken(null);
    setWaiting(waiting === app ? null : app);
  };
  const row = (app: Listed) => <Field key={app.app} label={app.name} image={app.icon ?? undefined}
    onPress={() => touch(app.app)}>{value(app)}</Field>;

  const withLetters = listed.filter(app => app.letter).sort(byName);
  const others = listed.filter(app => !app.letter).sort(byName);
  return <Screen title="Quick open" subtitle={subtitle}>
    {withLetters.length ? <>
      <Section title="Shortcuts">{withLetters.map(row)}</Section>
      <Section title="Other apps">{others.map(row)}</Section>
    </> : others.map(row)}
  </Screen>;
}

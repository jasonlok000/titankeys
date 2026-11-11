import { useEffect, useRef, useState } from "react";
import { Button, Field, Screen, Section, Stack, Text, TextInput, useSnapshot } from "ink";
import { type HapticsLab, playHaptic, readLabComments, saveLabComments, showHapticsGraph, state } from "../../lib/atlas";
import { hapticGroups } from "../../lib/haptics";

const title = "Haptics lab";
const subtitle = "Play each haptic and say how it feels. Your notes save as you type.";

type Comments = { general: string; haptics: Record<string, string> };

/** TitanKeys Debug only. Every other build shows nothing here, even through a direct link. */
export default function HapticsLabPage() {
  const result = useSnapshot(state);
  if (result.status !== "ready") return <Screen title={title} />;
  const lab = result.data.hapticsLab;
  if (!lab) return <Screen title={title} subtitle="The Haptics lab is only in TitanKeys Debug." />;
  return <Lab lab={lab} version={result.data.version} />;
}

function Lab({ lab, version }: { lab: HapticsLab; version: string }) {
  const [comments, setComments] = useState<Comments | null>(null);
  const [error, setError] = useState("");
  const [text, setText] = useState("");
  const changed = useRef(false);

  useEffect(() => {
    readLabComments().then(saved => setComments(fromFile(saved)), () => setError("Couldn't read your saved notes."));
  }, []);

  // The whole file is written again after each change, once the saved notes are in, so nothing typed is lost.
  const writer = useWriter(setError);
  useEffect(() => {
    if (comments && changed.current) writer(JSON.stringify(toFile(comments, lab, version), null, 2));
  }, [comments, lab]);

  const comment = (change: (comments: Comments) => Comments) => {
    changed.current = true;
    setComments(current => current && change(current));
  };
  // The sliders are in the graph. The lengths come back with the state when it closes, and go in the file then.
  const showGraph = () => {
    changed.current = true;
    void showHapticsGraph(hapticGroups.flatMap(group => group.haptics));
  };

  return <Screen title={title} subtitle={error || subtitle}>
    <Section title="Lengths">
      <Field label="Moving">{`${lab.movingMillis} ms`}</Field>
      <Field label="Step">{`${lab.stepMillis} ms`}</Field>
      <Field label="Show the graph" onPress={showGraph}>Sliders, feels and runs</Field>
    </Section>
    <Section title="Try it">
      <TextInput value={text} onChange={setText} placeholder="Type or swipe here" action="return" minLines={3} maxLines={6} />
    </Section>
    {comments && <Section title="Overall">
      <TextInput value={comments.general} placeholder="How do they feel together?" action="return" minLines={2} maxLines={8}
        onChange={general => comment(current => ({ ...current, general }))} />
    </Section>}
    {comments && hapticGroups.map(group => <Section key={group.title} title={group.title}>
      {group.haptics.map(({ id, label }) => <Stack key={id} gap={10}>
        <Text size={20}>{label}</Text>
        <Text size={16}>{`${lab.haptics[id]?.feel}: ${lab.haptics[id]?.plays}`}</Text>
        <Button onPress={() => void playHaptic(id)}>Play</Button>
        <TextInput value={comments.haptics[id] ?? ""} placeholder="How does it feel?" action="return" minLines={1} maxLines={6}
          onChange={note => comment(current => ({ ...current, haptics: { ...current.haptics, [id]: note } }))} />
      </Stack>)}
    </Section>)}
  </Screen>;
}

/** Writes one file at a time. A text that comes while one is writing waits, and only the newest one is written next. */
function useWriter(failed: (message: string) => void) {
  const next = useRef<string | null>(null);
  const writing = useRef(false);
  return (text: string) => {
    next.current = text;
    if (writing.current) return;
    writing.current = true;
    void (async () => {
      while (next.current !== null) {
        const text = next.current;
        next.current = null;
        try {
          await saveLabComments(text);
          failed("");
        } catch {
          failed("Couldn't save your notes. Keep typing to try again.");
        }
      }
      writing.current = false;
    })();
  };
}

/** The notes from a saved file, or none if it has none. */
function fromFile(saved: unknown): Comments {
  const file = (saved ?? {}) as { general?: unknown; haptics?: unknown };
  const haptics: Record<string, string> = {};
  const list = Array.isArray(file.haptics) ? file.haptics as { id?: unknown; comment?: unknown }[] : [];
  for (const haptic of list)
    if (typeof haptic?.id === "string" && typeof haptic.comment === "string") haptics[haptic.id] = haptic.comment;
  return { general: typeof file.general === "string" ? file.general : "", haptics };
}

/** The file adb pulls: /sdcard/Android/data/com.titankeys.debug/files/haptics-lab/comments.json. */
function toFile(comments: Comments, lab: HapticsLab, version: string) {
  return {
    savedAt: new Date().toISOString(),
    version,
    movingMillis: lab.movingMillis,
    stepMillis: lab.stepMillis,
    general: comments.general,
    haptics: hapticGroups.flatMap(group => group.haptics.map(({ id, label }) => ({
      id,
      name: label,
      group: group.title,
      feel: lab.haptics[id]?.feel ?? "",
      plays: lab.haptics[id]?.plays ?? "",
      comment: comments.haptics[id] ?? "",
    }))),
  };
}

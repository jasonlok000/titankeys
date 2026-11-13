import { useState } from "react";
import { Button, Screen, Stack, Text, TextInput, back, useAction, useRouteParams, useSnapshot } from "ink";
import { putShorthand, state } from "../../../lib/atlas";

/** Adds the user's own shorthand, or changes the one in the route's `shorthand` parameter. */
export default function EditShorthand() {
  const param = useRouteParams().shorthand;
  const previous = typeof param === "string" && param ? param : undefined;
  const result = useSnapshot(state);
  const title = previous ? "Change shorthand" : "Add shorthand";
  if (result.status !== "ready") return <Screen title={title} />;
  const existing = result.data.ownShorthand.find(entry => entry.shorthand === previous);
  return <ShorthandForm title={title} previous={existing?.shorthand} shorthand={existing?.shorthand ?? ""} full={existing?.full ?? ""} />;
}

function ShorthandForm({ title, previous, ...initial }: {
  title: string;
  previous?: string;
  shorthand: string;
  full: string;
}) {
  const [shorthand, setShorthand] = useState(initial.shorthand);
  const [full, setFull] = useState(initial.full);
  const save = useAction(async () => {
    await putShorthand(shorthand.trim(), full.trim(), previous);
    back();
  });
  const ready = shorthand.trim().length > 0 && full.trim().length > 0;
  return <Screen title={title} subtitle="Letters, numbers and ' only. It works once Spell out shorthand is on.">
    <Stack gap={24}>
      <TextInput label="Shorthand" value={shorthand} onChange={setShorthand} placeholder="hw"
        autoFocus={!previous} autoCapitalise={false} action="done" />
      <TextInput label="Turns into" value={full} onChange={setFull} placeholder="homework" autoCapitalise={false} action="done" />
      {save.status === "error" && <Text size={18}>{save.error.message}</Text>}
      <Button disabled={!ready || save.status === "pending"} onPress={() => save.run()}>Save</Button>
    </Stack>
  </Screen>;
}

import { useState } from "react";
import { Screen, Stack, Text, TextInput, back, navigate, replace } from "ink";
import { saveProfile } from "../../../lib/atlas";

export default function NameProfile() {
  const [name, setName] = useState("My calibration");
  const save = (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;
    void saveProfile(trimmed).then(() => {
      back();
      replace("/settings");
      navigate("/settings/calibration");
    });
  };
  return <Screen title="Name profile">
    <Stack gap={16}>
      <TextInput value={name} onChange={setName} onSubmit={save} action="done" autoFocus />
      <Text>Press enter to save it.</Text>
    </Stack>
  </Screen>;
}

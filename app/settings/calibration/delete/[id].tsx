import { Field, Screen, back, useRouteParams, useSnapshot } from "ink";
import { deleteProfile, state } from "../../../../lib/atlas";

export default function DeleteProfile() {
  const { id } = useRouteParams("/settings/calibration/delete/[id]");
  const result = useSnapshot(state);
  const name = result.status === "ready" ? result.data.profiles.find(profile => profile.id === id)?.name : undefined;
  return <Screen title="Delete profile?" subtitle={name && `Deleting: ${name}.`}>
    <Field label="Delete profile" onPress={() => void deleteProfile(id).then(back)}>{""}</Field>
  </Screen>;
}

import { Canvas, CanvasText, Field, Screen, Stack, back, useAction } from "ink";
import { INK, useAccent } from "./brand";
import { RADIUS } from "./radii";
import { RoundedFill } from "./rounded";

const WIDTH = 596;
const HEIGHT = 96;

/**
 * The app's own confirm page, as Forget all words? and Delete the log? are: the question as the title, what happens under it,
 * the action filled in the accent with ink words, and a plain row that goes back without doing anything.
 */
export function ConfirmPage({ title, body, action, cancel, run }: {
  title: string;
  body: string;
  action: string;
  cancel: string;
  run: () => Promise<unknown>;
}) {
  const accent = useAccent();
  const go = useAction(run);
  return <Screen title={title} subtitle={body}>
    <Stack gap={24}>
      <Stack accessibilityLabel={action} disabled={go.status === "pending"}
        onPress={() => { if (go.status !== "pending") void go.run(); }}>
        <Canvas width={WIDTH} height={HEIGHT}>
          <RoundedFill x={0} y={0} width={WIDTH} height={HEIGHT} radius={RADIUS.large} fill={accent} />
          <CanvasText x={0} y={0} width={WIDTH} height={HEIGHT} size={32} colour={INK} align="center">{action}</CanvasText>
        </Canvas>
      </Stack>
      <Field label={cancel} onPress={back} />
    </Stack>
  </Screen>;
}

import { useEffect, useState } from "react";
import { Button, Canvas, CanvasText, Row, Screen, Stack, Text, TextInput } from "ink";
import { INK, useAccent } from "../lib/brand";
import { Note } from "../lib/note";
import { RoundedFill, RoundedOutline } from "../lib/rounded";
import { CoffeeCup, OrangeGlass, espresso, supportWidth } from "../lib/support-art";
import {
  buySupport, cupCents, cupQuantity, customPrices, drinkAmount, joinBoard, onSupportChanged, readBoard,
  refreshSupport, removeBoard, seeSupportReceipt, supportPending, supportState, supportUnavailable, tipUnit,
  type Board, type Product, type Receipt, type SupportState,
} from "../lib/support";

const tiers = [
  { id: "sip", label: "A sip", amount: .1 },
  { id: "small", label: "1/5 cup", amount: .2 },
  { id: "full", label: "2/5 cup", amount: .4 },
] as const;

export default function SupportPage() {
  return <SupportFlow />;
}

export function SupportFlow({ scene = "cup" }: { scene?: "cup" | "custom" | "board" }) {
  const [state, setState] = useState<SupportState>();
  const [board, setBoard] = useState<Board>({ available: false });
  const [status, setStatus] = useState("");
  const [pending, setPending] = useState(false);
  const [offerBoard, setOfferBoard] = useState(false);
  const [monthly, setMonthly] = useState(true);
  const [tier, setTier] = useState(0);
  const accent = useAccent();
  useEffect(() => {
    let active = true;
    const update = () => void supportState().then(data => { if (active) setState(data); }).catch(() => {});
    const stop = onSupportChanged(update);
    update();
    void refreshSupport().then(update).catch(update);
    if (scene === "board") void readBoard().then(data => { if (active) setBoard(data); }).catch(() => {});
    return () => { active = false; stop(); };
  }, [scene]);

  async function buy(id: string) {
    if (pending) return;
    setPending(true);
    setStatus("");
    try {
      const outcome = await buySupport(id);
      if (outcome === "unavailable") setStatus(supportUnavailable);
    } catch {
      setStatus(supportUnavailable);
    } finally {
      setPending(false);
    }
  }

  async function updateBoard(request: () => Promise<Board>) {
    try {
      const result = await request();
      if (!result.available) throw new Error();
      setBoard(previous => ({ ...previous, ...result }));
      setOfferBoard(false);
      setStatus("");
    } catch {
      setStatus("The supporters board is unavailable right now.");
    }
  }

  const receipt = state?.receipt;
  if (receipt) return <Thanks key={receipt.id} receipt={receipt} reduceMotion={state?.reduceMotion}
    done={() => void seeSupportReceipt(receipt.id).then(supportState).then(setState).catch(() => {})} />;

  const hasSupport = !!state?.totalCents;
  const canJoinBoard = hasSupport && board.available && board.postingReady === true;
  const billingStatus = state?.purchaseResult === "unavailable" ? supportUnavailable :
    state?.purchaseResult === "pending" ? supportPending : "";
  if (scene === "custom") return <Screen title="Pick your own" background={espresso}>
    <CustomTip products={state?.products ?? []} buy={buy} pending={pending} reduceMotion={state?.reduceMotion} />
    {(status || billingStatus) && <Note>{status || billingStatus}</Note>}
  </Screen>;
  if (scene === "board") return <Screen title={hasSupport ? "The orange juice fund" : "The coffee fund"} background={espresso}>
    <Note>{hasSupport ? "Our top 10 supporters, in cups of coffee I didn't drink." : "Our top 10 supporters, in cups of coffee."}</Note>
    {!board.available && <Note>The supporters board is unavailable right now.</Note>}
    {board.available && (board.entries?.length ?? 0) === 0 && <Note>Nobody here yet. The top spot is wide open.</Note>}
    {board.entries?.slice(0, 10).map(entry => <Row key={entry.id}
      title={`${entry.name || "Anonymous"}, ${entry.cups.toFixed(1)} cups`} subtitle={entry.message} />)}
    {canJoinBoard && <Note>Only what you type here is shown, never what you paid. Remove yourself any time.</Note>}
    {canJoinBoard && !offerBoard && <Button onPress={() => setOfferBoard(true)}>Join the supporters?</Button>}
    {board.available && (board.joined ?? state?.boardJoined) &&
      <Button onPress={() => void updateBoard(removeBoard)}>Remove me</Button>}
    {canJoinBoard && offerBoard &&
      <JoinSupporters join={(anonymous, name, message) => updateBoard(() => joinBoard(anonymous, name, message))} />}
    {status && <Note>{status}</Note>}
  </Screen>;

  const selected = tiers[tier];
  const id = `support_${monthly ? "monthly" : "tip"}_${selected.id}`;
  const product = state?.products.find(item => item.id === id);
  const price = product && `${product.price}${monthly ? " a month" : ""}`;
  const checkout = pending ? "Opening Google Play…" : price ? `Chip in · ${price}` : "Google Play checkout unavailable";
  return <Screen title="Buy me a coffee" background={espresso}>
    <Stack align="center" gap={12}>
      <CoffeeCup amount={selected.amount} reduceMotion={state?.reduceMotion} />
      <Stack axis="horizontal" align="center" justify="center" gap={10}>
        {tiers.map((option, i) => <Choice key={option.id} label={option.label} selected={i === tier}
          width={166} accent={accent} onPress={() => setTier(i)} />)}
      </Stack>
      <Stack axis="horizontal" align="center" justify="center" gap={10}>
        <Choice label="Monthly" selected={monthly} width={256} accent={accent} onPress={() => setMonthly(true)} />
        <Choice label="One-time" selected={!monthly} width={256} accent={accent} onPress={() => setMonthly(false)} />
      </Stack>
      <Checkout label={checkout} disabled={pending || !product} onPress={() => void buy(id)} />
      {monthly && product && <Text size={17} tone="muted" align="center">Cancel any time in Google Play.</Text>}
      {(status || billingStatus) && <Text size={17} tone="muted" align="center">{status || billingStatus}</Text>}
      {!monthly && <Text size={20} tone="accent" href="/support/custom">Pick your own amount</Text>}
      <Text size={20} tone="accent" href="/support/board">{hasSupport ? "The orange juice fund" : "The coffee fund"}</Text>
      <Text size={17} tone="muted" align="center">TitanKeys stays free. Your coffee buys me time to work on it.</Text>
    </Stack>
  </Screen>;
}

function Choice({ label, width, accent, selected, onPress }: {
  label: string; width: number; accent: string; selected: boolean; onPress: () => void;
}) {
  const box = { x: 2, y: 2, width: width - 4, height: 50, radius: 23 };
  return <Stack accessibilityLabel={selected ? `${label}, selected` : label} onPress={onPress}>
    <Canvas width={width} height={54}>
      <RoundedFill {...box} fill={selected ? "#45281D" : "#251713"} />
      <RoundedOutline {...box} stroke={selected ? accent : "#644130"} strokeWidth={2} />
      <CanvasText x={0} y={0} width={width} height={54} align="center" size={23}
        colour={selected ? accent : "#E7CFBA"}>{label}</CanvasText>
    </Canvas>
  </Stack>;
}

function Checkout({ label, onPress, disabled = false }: { label: string; onPress: () => void; disabled?: boolean }) {
  const accent = useAccent();
  return <Stack accessibilityLabel={label} disabled={disabled} onPress={onPress}>
    <Canvas width={supportWidth} height={64}>
      <RoundedFill x={1} y={1} width={supportWidth - 2} height={62} radius={28} fill={disabled ? "#3A281F" : accent} />
      <CanvasText x={12} y={0} width={supportWidth - 24} height={64} align="center" size={24}
        colour={disabled ? "#E4C8B6" : INK}>{label}</CanvasText>
    </Canvas>
  </Stack>;
}

function CustomTip({ products, buy, pending, reduceMotion }: {
  products: Product[]; buy: (id: string) => Promise<void>; pending: boolean; reduceMotion?: boolean;
}) {
  const [index, setIndex] = useState(0);
  const fixed = customPrices.flatMap(amount => {
    const id = `support_tip_${amount}`;
    const product = products.find(each => each.id === id);
    return product ? [{ amount, product }] : [];
  });
  const selected = fixed[Math.min(index, fixed.length - 1)];
  const unit = products.find(each => each.id === tipUnit);
  const quantity = selected && cupQuantity(selected.amount);
  return <Stack align="center" gap={16}>
    <CoffeeCup amount={selected ? selected.amount / 5 : .1} reduceMotion={reduceMotion} />
    {selected && <>
      <Text size={25}>{`${selected.product.price} · about ${quantity} ${selected.amount < 5 ? "of a cup" : selected.amount === 5 ? "cup" : "cups"}`}</Text>
      {selected.amount > 25 && <Note>That's a whole pot. I'll need a bigger table.</Note>}
      <Stack axis="horizontal" gap={16}>
        <Button disabled={index === 0} onPress={() => setIndex(Math.max(0, index - 1))}>Less</Button>
        <Button disabled={index >= fixed.length - 1} onPress={() => setIndex(Math.min(index + 1, fixed.length - 1))}>More</Button>
      </Stack>
      <Checkout label={`Chip in · ${selected.product.price}`} disabled={pending} onPress={() => void buy(selected.product.id)} />
    </>}
    {unit && <Button disabled={pending} onPress={() => void buy(tipUnit)}>{`Pick quantity · ${unit.price} each`}</Button>}
    {!unit && !selected && <Note>{supportUnavailable}</Note>}
  </Stack>;
}

function JoinSupporters({ join }: { join: (anonymous: boolean, name: string, message: string) => Promise<void> }) {
  const [name, setName] = useState("");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);
  async function send(anonymous: boolean) {
    setSaving(true);
    try { await join(anonymous, name, message); } finally { setSaving(false); }
  }
  return <Stack gap={12}>
    <Note>Put your name on the board, with a short message if you like.</Note>
    <TextInput label="Your name" value={name} onChange={value => setName(Array.from(value).slice(0, 80).join(""))} />
    <TextInput label="A short message (optional)" value={message} onChange={value => setMessage(Array.from(value).slice(0, 80).join(""))} />
    <Button disabled={saving || !name.trim()} onPress={() => void send(false)}>Add me</Button>
    <Button disabled={saving} onPress={() => void send(true)}>Keep me anonymous</Button>
  </Stack>;
}

function Thanks({ receipt, reduceMotion, done }: { receipt: Receipt; reduceMotion?: boolean; done: () => void }) {
  const [revealed, setRevealed] = useState(false);
  useEffect(() => {
    if (reduceMotion) return;
    const timer = setTimeout(() => setRevealed(true), 1270);
    return () => clearTimeout(timer);
  }, [reduceMotion]);
  const showMessage = reduceMotion || revealed;
  const total = receipt.totalCents / cupCents;
  return <Screen title="Thank you" background={showMessage ? "#36180E" : "#2B140E"}>
    <Stack align="center" gap={18}>
      <Stack accessibilityLabel="An orange rolls in and pours into a glass.">
        <OrangeGlass previous={receipt.previousCents / cupCents} total={total} reduceMotion={reduceMotion} />
      </Stack>
      {showMessage && <>
        <Text size={26} align="center">Confession. I don't drink coffee.</Text>
        <Text size={22} align="center">{`Thanks for ${drinkAmount(receipt)} of orange juice.`}</Text>
        <Text size={17} tone="muted" align="center">{`${Number(total.toFixed(2)).toLocaleString()} ${total === 1 ? "cup" : "cups"} so far. Cheers.`}</Text>
        <Checkout label="Done" onPress={done} />
      </>}
    </Stack>
  </Screen>;
}

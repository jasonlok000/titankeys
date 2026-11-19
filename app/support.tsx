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
  { id: "small", label: "A bigger sip", amount: .24 },
  { id: "full", label: "Half a coffee", amount: .5 },
] as const;

const sampleBoard: Board = { available: true, entries: [
  { id: "sample-1", name: "Mika", message: "For the swipe that finally got my name right." },
  { id: "sample-2", name: "Jo", message: "Still typing on real keys." },
  { id: "sample-3", name: "Ren", message: "More Cantonese, please." },
  { id: "sample-4", name: "Anonymous", message: "Thanks for keeping this going." },
] };

export default function SupportPage() {
  return <SupportFlow />;
}

export function SupportFlow({ scene = "cup" }: { scene?: "cup" | "custom" | "board" }) {
  const [state, setState] = useState<SupportState>();
  const [board, setBoard] = useState<Board>({ available: false });
  const [status, setStatus] = useState("");
  const [pending, setPending] = useState(false);
  const [offerBoard, setOfferBoard] = useState(false);
  const [monthly, setMonthly] = useState(scene !== "custom");
  const [tier, setTier] = useState(scene === "custom" ? 3 : 0);
  const [customIndex, setCustomIndex] = useState(0);
  const [testOrder, setTestOrder] = useState<{ product: Product; label: string; monthly: boolean }>();
  const [testReceipt, setTestReceipt] = useState<Receipt>();
  const accent = useAccent();
  useEffect(() => {
    let active = true;
    const update = () => void supportState().then(data => { if (active) setState(data); }).catch(() => {});
    const stop = onSupportChanged(update);
    update();
    void refreshSupport().then(update).catch(update);
    void readBoard().then(data => { if (active) setBoard(data); }).catch(() => {});
    return () => { active = false; stop(); };
  }, [scene]);

  async function buy(product: Product, label: string, recurring = false) {
    if (state?.preview) {
      setTestOrder({ product, label, monthly: recurring });
      return;
    }
    if (pending) return;
    setPending(true);
    setStatus("");
    try {
      const outcome = await buySupport(product.id);
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

  if (testOrder) return <Screen title="Test checkout" background={espresso}>
    <Stack gap={18}>
      <Note>Sample payment. No charge.</Note>
      <Row title={testOrder.label} subtitle={testOrder.monthly ? "Monthly support · sample price" : "One-time tip · sample price"} />
      <Text size={34}>{testOrder.monthly ? `${testOrder.product.price} a month` : testOrder.product.price}</Text>
      <Checkout label="Confirm test payment" onPress={() => {
        const previousCents = cupCents - 18;
        setTestReceipt({ id: "test-receipt", productId: testOrder.product.id, quantity: 1,
          previousCents, totalCents: previousCents + testOrder.product.baseCents });
        setTestOrder(undefined);
      }} />
      <Button onPress={() => setTestOrder(undefined)}>Cancel</Button>
    </Stack>
  </Screen>;

  const receipt = testReceipt ?? state?.receipt;
  if (receipt) return <Thanks key={receipt.id} receipt={receipt} reduceMotion={state?.reduceMotion}
    done={() => testReceipt ? setTestReceipt(undefined)
      : void seeSupportReceipt(receipt.id).then(supportState).then(setState).catch(() => {})} />;

  const ranking = state?.preview ? sampleBoard : board;
  const hasSupport = !!state?.totalCents;
  const canJoinBoard = hasSupport && board.available && board.postingReady === true;
  const billingStatus = state?.purchaseResult === "unavailable" ? supportUnavailable :
    state?.purchaseResult === "pending" ? supportPending : "";
  if (scene === "board") return <Screen title={hasSupport ? "The orange juice fund" : "The Coffee Fund"} background={espresso}>
    <Note>{state?.preview ? "Sample rankings" : "Our top 10 supporters."}</Note>
    {!ranking.available && <Note>The supporters board is unavailable right now.</Note>}
    {ranking.available && (ranking.entries?.length ?? 0) === 0 && <Note>Nobody here yet. The top spot is wide open.</Note>}
    {ranking.entries?.slice(0, 10).map((entry, index) => <Row key={entry.id}
      title={`${index + 1}. ${entry.name || "Anonymous"}`} subtitle={entry.message} />)}
    {canJoinBoard && <Note>Only what you type here is shown, never what you paid. Remove yourself any time.</Note>}
    {canJoinBoard && !offerBoard && <Button onPress={() => setOfferBoard(true)}>Join the supporters?</Button>}
    {board.available && (board.joined ?? state?.boardJoined) &&
      <Button onPress={() => void updateBoard(removeBoard)}>Remove me</Button>}
    {canJoinBoard && offerBoard &&
      <JoinSupporters join={(anonymous, name, message) => updateBoard(() => joinBoard(anonymous, name, message))} />}
    {status && <Note>{status}</Note>}
  </Screen>;

  const products = state?.products ?? [];
  const custom = tier === tiers.length;
  const customTiers = customPrices.flatMap(amount => {
    const product = products.find(each => each.id === `support_tip_${amount}`);
    return product ? [{ amount, product }] : [];
  });
  const customTip = customTiers[Math.min(customIndex, customTiers.length - 1)];
  const unit = products.find(each => each.id === tipUnit);
  const selected = tiers[tier] ?? tiers[2];
  const product = custom ? customTip?.product ?? unit
    : products.find(item => item.id === `support_${monthly ? "monthly" : "tip"}_${selected.id}`);
  const price = product && `${product.price}${monthly ? " a month" : ""}`;
  const checkout = pending ? "Opening Google Play…" : price
    ? `${state?.preview ? "Test checkout" : custom && !customTip ? "Pick quantity" : "Chip in"} · ${price}${custom && !customTip ? " each" : ""}`
    : state ? "Google Play checkout unavailable" : "Checking prices…";
  return <Screen title="Buy me a coffee" background={espresso}>
    <Stack align="center" gap={11}>
      <Stack align="center" gap={3}>
        <Text size={25} tone="accent" href="/support/board">{hasSupport ? "The orange juice fund" : "The Coffee Fund"}</Text>
        {state?.preview && <Text size={17} tone="muted">Sample rankings</Text>}
        {(ranking.entries?.length ?? 0) > 0 && <Text size={18} align="center" href="/support/board">{ranking.entries!.slice(0, 3)
          .map((entry, index) => `${index + 1}. ${(entry.name || "Anonymous").slice(0, 14)}`).join("   ·   ")}</Text>}
      </Stack>
      <CoffeeCup amount={custom ? customTip ? .46 + .1 * Math.log(customTip.amount / 3) : .6 : selected.amount} reduceMotion={state?.reduceMotion} />
      <Stack axis="horizontal" align="center" justify="center" gap={7}>
        {tiers.map((option, i) => <Choice key={option.id} label={option.label} selected={i === tier}
          width={140} accent={accent} onPress={() => setTier(i)} />)}
        <Choice label="Custom" selected={custom} width={140} accent={accent} onPress={() => {
          setTier(tiers.length);
          setMonthly(false);
        }} />
      </Stack>
      {!custom && <Stack axis="horizontal" align="center" justify="center" gap={10}>
        <Choice label="Monthly" selected={monthly} width={256} accent={accent} onPress={() => setMonthly(true)} />
        <Choice label="One-time" selected={!monthly} width={256} accent={accent} onPress={() => setMonthly(false)} />
      </Stack>}
      {custom && <Text size={17} tone="muted" align="center">Custom is a one-time tip.</Text>}
      {custom && customTip && <Stack axis="horizontal" align="center" gap={16}>
        <Button disabled={customIndex === 0} onPress={() => setCustomIndex(Math.max(0, customIndex - 1))}>Less</Button>
        <Text size={21}>{`${customTip.product.price} · ${cupQuantity(customTip.amount)} cup${customTip.amount > 5 ? "s" : ""}`}</Text>
        <Button disabled={customIndex >= customTiers.length - 1} onPress={() => setCustomIndex(customIndex + 1)}>More</Button>
      </Stack>}
      <Checkout label={checkout} disabled={pending || !product} onPress={() => {
        if (product) void buy(product, custom ? "Custom amount" : selected.label, monthly);
      }} />
      {custom && customTip && unit && <Text size={18} tone="accent" onPress={() => void buy(unit, "Custom quantity")}>
        {`Pick quantity · ${unit.price} each`}
      </Text>}
      {state?.preview && <Text size={17} tone="muted" align="center">Sample prices. No charge.</Text>}
      {!state?.preview && monthly && product && <Text size={17} tone="muted" align="center">Cancel any time in Google Play.</Text>}
      {(status || billingStatus) && <Text size={17} tone="muted" align="center">{status || billingStatus}</Text>}
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
      <CanvasText x={0} y={0} width={width} height={54} align="center" size={width < 150 ? 19 : 23}
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

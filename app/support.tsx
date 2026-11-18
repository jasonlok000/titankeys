import { useEffect, useState } from "react";
import { Button, Row, Screen, Section, Stack, TextInput } from "ink";
import { Note } from "../lib/note";
import { JuiceGlass } from "../lib/juice-glass";
import {
  buySupport, cupQuantity, customPrices, drinkAmount, joinBoard, onSupportChanged, readBoard,
  refreshSupport, removeBoard, seeSupportReceipt, supportPending, supportState, supportTitle, supportUnavailable, tipUnit,
  type Board, type Product, type Receipt, type SupportState,
} from "../lib/support";

const tiers = [
  ["sip", "A sip: about 1/10 of a cup"],
  ["small", "About 1/5 of a cup"],
  ["full", "About 2/5 of a cup"],
] as const;

export default function SupportPage() {
  const [state, setState] = useState<SupportState>();
  const [board, setBoard] = useState<Board>({ available: false });
  const [status, setStatus] = useState("");
  const [pending, setPending] = useState(false);
  const [custom, setCustom] = useState(false);
  const [offerBoard, setOfferBoard] = useState(false);
  useEffect(() => {
    let active = true;
    const update = () => void supportState().then(data => { if (active) setState(data); }).catch(() => {});
    const stop = onSupportChanged(update);
    update();
    void refreshSupport().then(update).catch(update);
    void readBoard().then(data => { if (active) setBoard(data); }).catch(() => {});
    return () => { active = false; stop(); };
  }, []);

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

  function paymentRows(monthly: boolean) {
    return tiers.map(([tier, label]) => {
      const id = `support_${monthly ? "monthly" : "tip"}_${tier}`;
      const product = state?.products.find(product => product.id === id);
      const price = product && (monthly ? `${product.price} a month` : product.price);
      return <Row key={id} title={label} subtitle={price} onPress={() => void buy(id)} />;
    });
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
    done={() => void seeSupportReceipt(receipt.id).then(supportState).then(setState)} />;

  const hasSupport = !!state?.totalCents;
  const billingStatus = state?.purchaseResult === "unavailable" ? supportUnavailable :
    state?.purchaseResult === "pending" ? supportPending : "";
  return <Screen title={supportTitle}>
    <Stack gap={12}>
      <Note>Donating gets you nothing. No badge, no secret theme, no bonus features. It's all free already.</Note>
      <Note>What it does get: one developer more time on TitanKeys.</Note>
      <Note>That means bugs fixed faster, more languages, and new tricks for the keys.</Note>
      <Note>No ads and no paywall. Ever.</Note>
    </Stack>
    <Section title="Monthly">
      {paymentRows(true)}
      <Note>Cancel any time in Google Play.</Note>
    </Section>
    <Section title="One-time tip">
      {paymentRows(false)}
      <Row title="Pick your own" onPress={() => setCustom(!custom)} />
    </Section>
    {custom && <CustomTip products={state?.products ?? []} buy={buy} />}
    {(status || billingStatus) && <Note>{status || billingStatus}</Note>}
    <Section title={hasSupport ? "The orange juice fund" : "The coffee fund"}>
      <Note>{hasSupport ? "Our top 10 supporters, in cups of coffee I didn't drink." : "Our top 10 supporters, in cups of coffee."}</Note>
      {(board.entries?.length ?? 0) === 0 && <Note>Nobody here yet. The top spot is wide open.</Note>}
      {board.entries?.slice(0, 10).map(entry => <Row key={entry.id}
        title={`${entry.name || "Anonymous"}, ${entry.cups.toFixed(1)} cups`} subtitle={entry.message} />)}
      {hasSupport && board.available && <Note>Only what you type here is shown, never what you paid. Remove yourself any time.</Note>}
      {hasSupport && board.available && !offerBoard && <Button onPress={() => setOfferBoard(true)}>Join the supporters?</Button>}
      {hasSupport && board.available && (board.joined ?? state?.boardJoined) &&
        <Button onPress={() => void updateBoard(removeBoard)}>Remove me</Button>}
      {hasSupport && board.available && offerBoard &&
        <JoinSupporters join={(anonymous, name, message) => updateBoard(() => joinBoard(anonymous, name, message))} />}
    </Section>
  </Screen>;
}

function CustomTip({ products, buy }: { products: Product[]; buy: (id: string) => Promise<void> }) {
  const [index, setIndex] = useState(0);
  const fixed = customPrices.flatMap(amount => {
    const id = `support_tip_${amount}`;
    const product = products.find(each => each.id === id);
    return product ? [{ amount, product }] : [];
  });
  const selected = fixed[Math.min(index, fixed.length - 1)];
  const unit = products.find(each => each.id === tipUnit);
  const quantity = selected && cupQuantity(selected.amount);
  return <Section title="How much coffee?">
    {selected && <>
      <Note>{`${selected.product.price} · about ${quantity} ${quantity === "1" ? "cup" : "cups"}`}</Note>
      {selected.amount > 25 && <Note>That's a whole pot. I'll need a bigger table.</Note>}
      <Stack gap={8}>
        <Button disabled={index === 0} onPress={() => setIndex(Math.max(0, index - 1))}>Less</Button>
        <Button disabled={index >= fixed.length - 1} onPress={() => setIndex(Math.min(index + 1, fixed.length - 1))}>More</Button>
      </Stack>
      <Button onPress={() => void buy(selected.product.id)}>{selected.product.price}</Button>
    </>}
    {unit && <Button onPress={() => void buy(tipUnit)}>{`Choose quantity in Google Play · ${unit.price} each`}</Button>}
    {!unit && !selected && <Note>{supportUnavailable}</Note>}
  </Section>;
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
  return <Screen title="Thank you!">
    <JuiceGlass receipt={receipt} reduceMotion={reduceMotion} />
    <Note>Thank you! Now, a confession: I don't drink coffee. It gives me insomnia and a headache. I do love orange juice, though.</Note>
    <Note>{`So thank you for ${drinkAmount(receipt)} of orange juice. Seriously, thank you.`}</Note>
    <Button onPress={done}>Done</Button>
  </Screen>;
}

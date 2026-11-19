import { useEffect, useState } from "react";
import { Button, Canvas, CanvasText, Row, Screen, Stack, Text, TextInput, navigate } from "ink";
import { CREAM, INK, MUTED, useAccent } from "../lib/brand";
import { Note } from "../lib/note";
import { RoundedFill, RoundedOutline } from "../lib/rounded";
import { RisingThanks, cupParts, supportWidth } from "../lib/support-art";
import {
  buySupport, joinBoard, messageLimit, onSupportChanged, previewRank, rankTier, readBoard, refreshSupport, removeBoard,
  reportEntry, sampleBoard, sampleJoin, sampleRank, seeSupportReceipt, supportPending, supportState, supportTitle,
  supportUnavailable, tipUnit, type Board, type BoardEntry, type Product, type Receipt, type SupportState,
} from "../lib/support";

// F: every line here comes from docs/support-text.md once F's rewrite lands.
const TEXT = {
  line: "TitanKeys stays free. Your coffee buys me time to work on it.",
  nothing: "F: You get nothing for it. No badges, no extras. Just more TitanKeys.",
  fund: "The coffee fund",
  supporters: "F: supporters title",
  everyone: "Everyone",
  empty: "Nobody here yet. The top spot is wide open.",
  unavailable: "The supporters board is unavailable right now.",
  privacy: "Only what you type here is shown, never what you paid. Remove yourself any time.",
  oneTime: "One-time", monthly: "Monthly",
  custom: "Custom", pickYourOwn: "Pick your own", wholePot: "That's a whole pot. I'll need a bigger table.",
  cancel: "Cancel any time in Google Play.",
  putsYouAt: (rank: number) => `This puts you at #${rank}`,
  join: "Join the supporters?", joinLine: "Put your name on the board, with a short message if you like.",
  name: "Your name", message: "A short message (optional)", addMe: "Add me", anonymous: "Keep me anonymous",
  limit: (tier: number | null, n: number) => tier ? `Top ${tier}: you can write up to ${n} characters.` : "Beyond the top 100, your name shows without a message.",
  moveUp: (tier: number) => `You're in the top ${tier} now. Want to add a longer message?`,
  thanks: "Thank you!", thanksLine: "Fun fact: I don't actually drink coffee. But this means a lot.",
  seeBoard: "See the board", done: "Done",
  report: "Report", reported: "Reported. Thanks.", removeMe: "Remove me",
  sample: "Sample board and prices. No charge.",
};

// The amounts, as Play's products: one-time and monthly have the same three, and Custom is one-time only (N x $1).
const tiers = [
  { id: "sip", label: "A sip" },
  { id: "small", label: "A bigger sip" },
  { id: "full", label: "Half a coffee" },
] as const;
/** Custom's steps, in dollars: $1 buys the unit product (Play asks how many), the rest are Play's fixed tips. */
export const customSteps = [1, 3, 5, 10, 20, 50];

export default function SupportPage() {
  return <SupportFlow />;
}

/** The support screens, one flow so a receipt from Play can take over whichever is open. */
export function SupportFlow({ scene = "page" }: { scene?: "page" | "custom" | "board" }) {
  const [state, setState] = useState<SupportState>();
  const [board, setBoard] = useState<Board>({ available: false });
  const [status, setStatus] = useState("");
  const [pending, setPending] = useState(false);
  const [monthly, setMonthly] = useState(false);
  const [tier, setTier] = useState(0);
  const [step, setStep] = useState(2);
  const [rank, setRank] = useState<number | null>(null);
  const [rankBefore, setRankBefore] = useState<number | null>(null);
  const [testReceipt, setTestReceipt] = useState<Receipt>();
  const [joinDone, setJoinDone] = useState(false);
  const accent = useAccent();
  const preview = !!state?.preview;

  useEffect(() => {
    let active = true;
    const update = () => void supportState().then(data => { if (active) setState(data); }).catch(() => {});
    const stop = onSupportChanged(update);
    update();
    void refreshSupport().then(update).catch(update);
    void readBoard().then(data => { if (active) setBoard(data); }).catch(() => {});
    return () => { active = false; stop(); };
  }, [scene]);
  const shownBoard = preview ? sampleBoard() : board;

  const products = state?.products ?? [];
  const custom = scene === "custom";
  const customDollars = customSteps[step];
  const product = custom
    ? products.find(each => each.id === (customDollars === 1 ? tipUnit : `support_tip_${customDollars}`))
    : products.find(each => each.id === `support_${monthly ? "monthly" : "tip"}_${tiers[tier].id}`);
  const addCents = product?.baseCents ?? 0;

  // "This puts you at #N": asked again whenever the amount changes; hidden when the server can't answer.
  useEffect(() => {
    if (!addCents) { setRank(null); return; }
    if (preview) { setRank(sampleRank(addCents)); return; }
    let active = true;
    void previewRank(addCents).then(result => { if (active) setRank(result.available ? result.rank ?? null : null); })
      .catch(() => { if (active) setRank(null); });
    return () => { active = false; };
  }, [addCents, preview]);

  async function buy(item: Product) {
    setRankBefore(shownBoard.myRank ?? null);
    if (preview) {
      // Debug's test checkout: no Billing, straight to the opt-in and the thank-you with a sample receipt.
      setTestReceipt({ id: "test-receipt", productId: item.id, quantity: 1, previousCents: 150, totalCents: 150 + item.baseCents });
      return;
    }
    if (pending) return;
    setPending(true);
    setStatus("");
    try {
      const outcome = await buySupport(item.id);
      if (outcome === "unavailable") setStatus(supportUnavailable);
    } catch {
      setStatus(supportUnavailable);
    } finally {
      setPending(false);
    }
  }

  const receipt = testReceipt ?? state?.receipt;
  if (receipt) {
    const finish = () => {
      setJoinDone(false);
      if (testReceipt) setTestReceipt(undefined);
      else void seeSupportReceipt(receipt.id).then(supportState).then(setState).catch(() => {});
    };
    const nowRank = preview ? sampleRank(receipt.totalCents - receipt.previousCents) : shownBoard.myRank ?? null;
    const joined = shownBoard.joined ?? state?.boardJoined;
    const movedUp = !!joined && rankTier(nowRank) !== null && (rankBefore === null || (rankTier(nowRank) ?? 999) < (rankTier(rankBefore) ?? 999));
    const offer = !joinDone && shownBoard.available && shownBoard.postingReady === true && (!joined || movedUp);
    if (offer) return <JoinSupporters rank={nowRank} moveUp={movedUp ? rankTier(nowRank) : null}
      join={async (anonymous, name, message) => {
        if (preview) sampleJoin(anonymous ? "" : name, message, receipt.totalCents - receipt.previousCents);
        else {
          try {
            const result = await joinBoard(anonymous, name, message);
            if (result.available) setBoard(previous => ({ ...previous, ...result }));
          } catch { /* The thank-you still comes. */ }
        }
        setJoinDone(true);
      }}
      skip={() => setJoinDone(true)} />;
    return <Thanks cups={receipt.totalCents / 500} reduceMotion={state?.reduceMotion}
      seeBoard={() => { finish(); navigate("/support/board"); }} done={finish} />;
  }

  const hasSupport = !!state?.totalCents || preview;
  if (scene === "board") return <SupportBoard board={shownBoard} title={hasSupport ? TEXT.supporters : TEXT.fund}
    preview={preview} remove={() => void removeBoard().then(setBoard).catch(() => setStatus(TEXT.unavailable))} />;

  const billingStatus = state?.purchaseResult === "unavailable" ? supportUnavailable :
    state?.purchaseResult === "pending" ? supportPending : "";
  const price = product ? `${product.price}${monthly && !custom ? " a month" : ""}` : "";
  const checkout = pending ? "Opening Google Play…" : price ? `Chip in · ${price}` : state ? "Google Play checkout unavailable" : "Checking prices…";
  const footer = <>
    {rank !== null && <RankLine rank={rank} accent={accent} />}
    <Checkout label={checkout} disabled={pending || !product} onPress={() => { if (product) void buy(product); }} />
    {monthly && !custom && product && <Note align="center">{TEXT.cancel}</Note>}
    {preview && <Note align="center">{TEXT.sample}</Note>}
    {(status || billingStatus) && <Note align="center">{status || billingStatus}</Note>}
  </>;

  if (custom) return <Screen title={TEXT.pickYourOwn}>
    <Stack align="center" gap={16}>
      <Note>{`${TEXT.oneTime}.`}</Note>
      <Canvas width={supportWidth} height={150}>
        {(() => {
          const count = Math.min(6, Math.max(1, Math.round(customDollars / 5)));
          return Array.from({ length: count }, (_, i) => cupParts(supportWidth / 2 - count * 30 + i * 60, 30, 2.6, `cup-${i}`, accent)).flat();
        })()}
      </Canvas>
      <Text size={56} align="center">{`$${customDollars}`}</Text>
      <Text size={22} tone="accent" align="center">{customDollars / 5 > 5 ? TEXT.wholePot : cupFeel(customDollars)}</Text>
      <Stack axis="horizontal" align="center" gap={16}>
        <Button disabled={step === 0} onPress={() => setStep(Math.max(0, step - 1))}>Less</Button>
        <StepBar steps={customSteps.length} at={step} accent={accent} />
        <Button disabled={step >= customSteps.length - 1} onPress={() => setStep(step + 1)}>More</Button>
      </Stack>
      {footer}
    </Stack>
  </Screen>;

  const entries = shownBoard.entries ?? [];
  return <Screen title={supportTitle}>
    <Stack gap={14}>
      <Text size={26}>{TEXT.line}</Text>
      <Note>{TEXT.nothing}</Note>
      <Text size={18} tone="accent">{TEXT.fund.toUpperCase()}</Text>
      {!shownBoard.available && <Note>{TEXT.unavailable}</Note>}
      {shownBoard.available && entries.length === 0 && <Note>{TEXT.empty}</Note>}
      {entries.length > 0 && <Podium entries={entries} accent={accent} />}
      {entries.slice(3, 6).map(entry => <Row key={entry.id} title={`${entry.rank}   ${entry.name || "Anonymous"}`} subtitle={entry.message || undefined} />)}
      {entries.length > 3 && <Text size={20} tone="accent" href="/support/board">{`${TEXT.everyone} ›`}</Text>}
      <Stack axis="horizontal" align="center" justify="center" gap={10}>
        <Choice label={TEXT.oneTime} selected={!monthly} width={292} accent={accent} onPress={() => setMonthly(false)} />
        <Choice label={TEXT.monthly} selected={monthly} width={292} accent={accent} onPress={() => setMonthly(true)} />
      </Stack>
      <Stack axis="horizontal" align="center" justify="center" gap={8}>
        {tiers.map((option, i) => <Choice key={option.id} label={option.label} selected={i === tier}
          width={monthly ? 192 : 142} accent={accent} onPress={() => setTier(i)} />)}
        {!monthly && <Choice label={TEXT.custom} selected={false} width={142} accent={accent} onPress={() => navigate("/support/custom")} />}
      </Stack>
      {footer}
    </Stack>
  </Screen>;
}

function cupFeel(dollars: number) {
  const cups = dollars / 5;
  if (cups < .3) return "about a sip";
  if (cups < .9) return `about ${Math.round(cups * 5)}/5 of a cup`;
  if (cups < 1.1) return "about a cup";
  return `about ${Math.round(cups * 10) / 10} cups`;
}

/** The top three as a podium: first in the middle, raised and ringed in orange. Name and message only. */
function Podium({ entries, accent }: { entries: BoardEntry[]; accent: string }) {
  const gap = 10, w = (supportWidth - gap * 2) / 3.15, wide = w * 1.15, height = 236;
  const places = [[1, 0, w, 40], [0, w + gap, wide, 0], [2, w + gap + wide + gap, w, 52]] as const;
  return <Canvas width={supportWidth} height={height}>
    {places.flatMap(([index, x, width, drop]) => {
      const entry = entries[index];
      if (!entry) return [];
      const first = index === 0, y = drop + 14, h = height - y - 2;
      return [
        <RoundedFill key={`f${index}`} x={x} y={y} width={width} height={h} radius={24} fill={first ? "#1E120B" : "#110E0C"} />,
        <RoundedOutline key={`o${index}`} x={x} y={y} width={width} height={h} radius={24} stroke={first ? accent : "#2A2623"} strokeWidth={first ? 2 : 1} />,
        <RoundedFill key={`r${index}`} x={x + width / 2 - 22} y={y + 16} width={44} height={44} radius={22} fill={first ? accent : "#1C1714"} />,
        <CanvasText key={`n${index}`} x={x + width / 2 - 22} y={y + 16} width={44} height={44} size={24} align="center" colour={first ? INK : CREAM}>{`${index + 1}`}</CanvasText>,
        <CanvasText key={`t${index}`} x={x + 8} y={y + 70} width={width - 16} height={30} size={24} align="center" colour={CREAM}>{entry.name || "Anonymous"}</CanvasText>,
        <CanvasText key={`m${index}`} x={x + 10} y={y + 104} width={width - 20} height={h - 112} size={18} align="center" colour={MUTED}>{entry.message ?? ""}</CanvasText>,
      ];
    })}
  </Canvas>;
}

function RankLine({ rank, accent }: { rank: number; accent: string }) {
  return <Canvas width={supportWidth} height={58}>
    <RoundedFill x={1} y={1} width={supportWidth - 2} height={56} radius={18} fill="#110E0C" />
    <RoundedOutline x={1} y={1} width={supportWidth - 2} height={56} radius={18} stroke={accent} strokeWidth={1} />
    <CanvasText x={20} y={0} width={supportWidth - 40} height={58} size={24} colour={CREAM}>{TEXT.putsYouAt(rank)}</CanvasText>
  </Canvas>;
}

function StepBar({ steps, at, accent }: { steps: number; at: number; accent: string }) {
  const width = 300;
  return <Canvas width={width} height={40}>
    <RoundedFill x={0} y={17} width={width} height={6} radius={3} fill="#2A2623" />
    <RoundedFill x={0} y={17} width={width * at / (steps - 1)} height={6} radius={3} fill={accent} />
    <RoundedFill x={width * at / (steps - 1) - 14 + (at === 0 ? 14 : at === steps - 1 ? -14 : 0)} y={6} width={28} height={28} radius={14} fill={CREAM} />
  </Canvas>;
}

function Choice({ label, width, accent, selected, onPress }: {
  label: string; width: number; accent: string; selected: boolean; onPress: () => void;
}) {
  const box = { x: 2, y: 2, width: width - 4, height: 56, radius: 26 };
  return <Stack accessibilityLabel={selected ? `${label}, selected` : label} onPress={onPress}>
    <Canvas width={width} height={60}>
      <RoundedFill {...box} fill={selected ? "#2A170D" : "#110E0C"} />
      <RoundedOutline {...box} stroke={selected ? accent : "#2A2623"} strokeWidth={2} />
      <CanvasText x={0} y={0} width={width} height={60} align="center" size={width < 160 ? 19 : 22}
        colour={selected ? accent : CREAM}>{label}</CanvasText>
    </Canvas>
  </Stack>;
}

function Checkout({ label, onPress, disabled = false }: { label: string; onPress: () => void; disabled?: boolean }) {
  const accent = useAccent();
  return <Stack accessibilityLabel={label} disabled={disabled} onPress={onPress}>
    <Canvas width={supportWidth} height={68}>
      <RoundedFill x={1} y={1} width={supportWidth - 2} height={66} radius={33} fill={disabled ? "#2A2623" : accent} />
      <CanvasText x={12} y={0} width={supportWidth - 24} height={68} align="center" size={25}
        colour={disabled ? MUTED : INK}>{label}</CanvasText>
    </Canvas>
  </Stack>;
}

/** After paying: the opt-in, with the message limit for the new rank, or the invite to write more after moving up a tier. */
function JoinSupporters({ rank, moveUp, join, skip }: {
  rank: number | null; moveUp: number | null; join: (anonymous: boolean, name: string, message: string) => Promise<void>; skip: () => void;
}) {
  const [name, setName] = useState("");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);
  const limit = messageLimit(rank);
  async function send(anonymous: boolean) {
    setSaving(true);
    try { await join(anonymous, name.trim(), message.trim()); } finally { setSaving(false); }
  }
  return <Screen title={TEXT.join}>
    <Stack gap={14}>
      <Note>{TEXT.joinLine}</Note>
      {moveUp !== null && <Text size={22} tone="accent">{TEXT.moveUp(moveUp)}</Text>}
      <Note>{TEXT.limit(rankTier(rank), limit)}</Note>
      <TextInput label={TEXT.name} value={name} onChange={value => setName(Array.from(value).slice(0, 40).join(""))} />
      {limit > 0 && <TextInput label={`${TEXT.message} · ${Array.from(message).length}/${limit}`} value={message}
        onChange={value => setMessage(Array.from(value).slice(0, limit).join(""))} />}
      <Note>{TEXT.privacy}</Note>
      <Checkout label={TEXT.addMe} disabled={saving} onPress={() => void send(false)} />
      <Button disabled={saving} onPress={() => void send(true)}>{TEXT.anonymous}</Button>
      {moveUp !== null && <Button onPress={skip}>{TEXT.done}</Button>}
    </Stack>
  </Screen>;
}

/** The thank-you, full screen: the warm rise to the supporter's total, then the words settle on top. */
function Thanks({ cups, reduceMotion, seeBoard, done }: { cups: number; reduceMotion?: boolean; seeBoard: () => void; done: () => void }) {
  const [settled, setSettled] = useState(!!reduceMotion);
  // Over the orange the words are dark; over black, cream.
  const over = cups >= .6;
  const height = 640;
  return <Screen background={cups >= 1 && settled ? "#FFD9AE" : "#000000"} centered>
    <Stack align="center" gap={14} accessibilityLabel={`${TEXT.thanks} ${TEXT.thanksLine}`}>
      <RisingThanks cups={cups} width={supportWidth} height={height} reduceMotion={reduceMotion} done={() => setSettled(true)}>
        {settled && [
          <CanvasText key="thanks" x={20} y={150} width={supportWidth - 40} height={70} size={56} align="center" colour={over ? INK : CREAM}>{TEXT.thanks}</CanvasText>,
          <CanvasText key="line" x={30} y={232} width={supportWidth - 60} height={90} size={27} align="center" colour={over ? INK : CREAM}>{TEXT.thanksLine}</CanvasText>,
        ]}
      </RisingThanks>
      {settled && <Checkout label={TEXT.seeBoard} onPress={seeBoard} />}
      {settled && <Button onPress={done}>{TEXT.done}</Button>}
    </Stack>
  </Screen>;
}

/** Everyone who opted in, ranked: names and messages only, never amounts. A supporter sees the supporters' title. */
function SupportBoard({ board, title, preview, remove }: { board: Board; title: string; preview: boolean; remove: () => void }) {
  const [reported, setReported] = useState<string[]>([]);
  const entries = board.entries ?? [];
  const accent = useAccent();
  return <Screen title={title}>
    <Stack gap={12}>
      {preview && <Note>{TEXT.sample}</Note>}
      {!board.available && <Note>{TEXT.unavailable}</Note>}
      {board.available && entries.length === 0 && <Note>{TEXT.empty}</Note>}
      {entries.length > 0 && <Podium entries={entries} accent={accent} />}
      {entries.map(entry => <Stack key={entry.id} gap={2}>
        {entry.rank! > 3 && <Row title={`${entry.rank}   ${entry.name || "Anonymous"}${entry.you ? "  · you" : ""}`} subtitle={entry.message || undefined} />}
        {!entry.you && (reported.includes(entry.id)
          ? <Note>{TEXT.reported}</Note>
          : <Text size={17} tone="muted" onPress={() => {
            setReported([...reported, entry.id]);
            if (!preview) void reportEntry(entry.id).catch(() => {});
          }}>{`${TEXT.report} ${entry.rank! <= 3 ? `#${entry.rank}` : ""}`.trim()}</Text>)}
      </Stack>)}
      <Note>{TEXT.privacy}</Note>
      {board.joined && <Button onPress={remove}>{TEXT.removeMe}</Button>}
    </Stack>
  </Screen>;
}

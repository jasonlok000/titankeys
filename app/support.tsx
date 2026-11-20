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

// Every line is F's, from docs/support-text.md (claude/f-support-text-3). PRE lines are coffee only; the orange juice is only in
// SUPPORTER lines: the thank-you and the supporter's own view of the board.
const TEXT = {
  // PRE: the support page.
  lines: [
    "Donating gets you nothing. Well, a spot on the board.",
    "What it really buys: time to fix bugs, add languages and build new things.",
    "No ads and no paywall. Ever.",
  ],
  // PRE: the board.
  fund: "The coffee fund",
  fundLine: "Everyone who chipped in and wanted to be here, by rank.",
  empty: "Nobody here yet. Any amount puts you at #1.",
  anonymous: "Anonymous",
  report: "Report this",
  reported: "Thanks. We'll take a look.",
  privacy: "The board shows names, messages and rank only. Remove yourself any time.",
  offline: "You're offline, so the board can't load.",
  // SUPPORTER: their own view of the board.
  supporters: "The orange juice fund",
  supportersLine: "Everyone who kept me in orange juice, by rank. Cheers.",
  removeMe: "Remove me",
  // PRE: the amounts.
  oneTime: "One-time", monthly: "Monthly",
  custom: "Custom", customLine: "Your call.",
  monthlyNote: "A month. Cancel any time in Google Play.",
  howMuch: "How much coffee?",
  wholePot: "That's a whole pot. I'll need a bigger table.",
  putsYouAt: (rank: number, from: number | null) => from && from !== rank ? `This puts you at #${rank}, up from #${from}` : `This puts you at #${rank}`,
  chipIn: "Chip in",
  // SUPPORTER: the thank-you, the opt-in after it, and moving up.
  thanks: "Thank you!", thanksLine: "Fun fact: I don't actually drink coffee. I'm more of an orange juice person. But this means a lot.",
  join: "Join the board?",
  name: "Your name", message: "A message (optional)", addMe: "Add me", keepAnonymous: "Keep me anonymous",
  limit: (tier: number | null) => tier === 3 ? "Up to 120 characters. You've earned the room." : tier === 10 ? "Up to 80 characters."
    : tier === 100 ? "Up to 40 characters." : "Your name only, for now. Move up for a message.",
  moveUp: (tier: number) => `You're in the top ${tier} now. Want to add a longer message?`,
  writeMore: "Write more", later: "Later",
  sample: "Sample board and prices. No charge.",
};

// The amounts, as Play's products: one-time and monthly have the same three, and Custom is one-time only (N x $1).
const tiers = [
  { id: "sip", label: "A sip", line: "A little nudge." },
  { id: "small", label: "A bigger sip", line: "Keeps the fixes coming." },
  { id: "full", label: "Half a coffee", line: "A proper boost." },
] as const;
/** Custom's dollars: the $1 unit product, bought N times (Google Play asks how many, from 1 to 50 here). */
const CUSTOM_MOST = 50;

export default function SupportPage() {
  return <SupportFlow />;
}

/** The support screens, one flow so a receipt from Play can take over whichever is open. */
export function SupportFlow({ scene = "page" }: { scene?: "page" | "custom" | "board" }) {
  const [state, setState] = useState<SupportState>();
  const [board, setBoard] = useState<Board>({ available: false });
  const [status, setStatus] = useState("");
  const [pending, setPending] = useState(false);
  const [monthly, setMonthly] = useState(true);
  const [thanked, setThanked] = useState(false);
  const [tier, setTier] = useState(0);
  const [dollars, setDollars] = useState(5);
  const [joinRank, setJoinRank] = useState<number | null>(null);
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
    void (async () => {
      let data = await readBoard(0, 20);
      const pages = scene === "board" ? 5 : 1;
      for (let page = 1; page < pages && data.available && data.nextOffset; page++) {
        const more = await readBoard(data.nextOffset, 20);
        if (!more.available) break;
        data = { ...more, entries: [...(data.entries ?? []), ...(more.entries ?? [])] };
      }
      if (active) setBoard(data);
    })().catch(() => {});
    return () => { active = false; stop(); };
  }, [scene]);
  const shownBoard = preview ? sampleBoard() : board;

  const products = state?.products ?? [];
  const custom = scene === "custom";
  const customDollars = dollars;
  const product = custom
    ? products.find(each => each.id === tipUnit)
    : products.find(each => each.id === `support_${monthly ? "monthly" : "tip"}_${tiers[tier].id}`);
  const addCents = custom ? (product?.baseCents ?? 0) * dollars : product?.baseCents ?? 0;

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
  useEffect(() => {
    if (!receipt || preview) return;
    let active = true;
    void previewRank(0).then(result => { if (active && result.available) setJoinRank(result.rank ?? null); }).catch(() => {});
    return () => { active = false; };
  }, [receipt?.id, preview]);
  if (receipt) {
    const finish = () => {
      setJoinDone(false);
      setThanked(false);
      if (testReceipt) setTestReceipt(undefined);
      else void seeSupportReceipt(receipt.id).then(supportState).then(setState).catch(() => {});
    };
    const nowRank = preview ? sampleRank(receipt.totalCents - receipt.previousCents) : joinRank ?? shownBoard.myRank ?? null;
    const joined = shownBoard.joined ?? state?.boardJoined;
    const movedUp = !!joined && rankTier(nowRank) !== null && (rankBefore === null || (rankTier(nowRank) ?? 999) < (rankTier(rankBefore) ?? 999));
    const offer = !joinDone && shownBoard.available && shownBoard.postingReady === true && (!joined || movedUp);
    if (!thanked || !offer) return <Thanks cups={receipt.totalCents / 500} reduceMotion={state?.reduceMotion} moveUp={offer && movedUp ? rankTier(nowRank) : null}
      join={offer ? () => setThanked(true) : null} board={() => { finish(); navigate("/support/board"); }} later={finish} />;
    if (offer) return <JoinSupporters rank={nowRank}
      join={async (anonymous, name, message) => {
        if (preview) sampleJoin(anonymous ? "" : name, message, receipt.totalCents - receipt.previousCents);
        else {
          try {
            const result = await joinBoard(anonymous, name, message);
            if (result.available) setBoard(previous => ({ ...previous, ...result }));
          } catch { /* The thank-you still comes. */ }
        }
        finish();
        navigate("/support/board");
      }} />;
  }

  const hasSupport = !!state?.totalCents || preview;
  if (scene === "board") return <SupportBoard board={shownBoard} title={hasSupport ? TEXT.supporters : TEXT.fund}
    line={hasSupport ? TEXT.supportersLine : TEXT.fundLine}
    preview={preview} remove={() => void removeBoard().then(setBoard).catch(() => setStatus(TEXT.offline))} />;

  const billingStatus = state?.purchaseResult === "unavailable" ? supportUnavailable :
    state?.purchaseResult === "pending" ? supportPending : "";
  const price = custom ? (product ? `$${dollars}` : "") : product?.price ?? "";
  const checkout = pending ? "Opening Google Play…" : price ? `${TEXT.chipIn} · ${price}` : state ? supportUnavailable : "Checking prices…";
  const footer = <>
    {rank !== null && <RankLine text={TEXT.putsYouAt(rank, shownBoard.joined ? shownBoard.myRank ?? null : null)} accent={accent} />}
    <Checkout label={checkout} disabled={pending || !product} onPress={() => { if (product) void buy(product); }} />
    {monthly && !custom && product && <Note align="center">{TEXT.monthlyNote}</Note>}
    {preview && <Note align="center">{TEXT.sample}</Note>}
    {(status || billingStatus) && <Note align="center">{status || billingStatus}</Note>}
  </>;

  if (custom) return <Screen title={TEXT.howMuch}>
    <Stack align="center" gap={16}>
      <Note>{`${TEXT.oneTime}. ${TEXT.customLine}`}</Note>
      <Canvas width={supportWidth} height={150}>
        {(() => {
          const count = Math.min(6, Math.max(1, Math.round(customDollars / 5)));
          return Array.from({ length: count }, (_, i) => cupParts(supportWidth / 2 - count * 30 + i * 60, 30, 2.6, `cup-${i}`, accent)).flat();
        })()}
      </Canvas>
      <Text size={44} align="center">{`$${customDollars} · ${cupWords(customDollars)}`}</Text>
      {customDollars / 5 > 5 && <Text size={22} tone="accent" align="center">{TEXT.wholePot}</Text>}
      <Stack axis="horizontal" align="center" gap={16}>
        <Button disabled={dollars <= 1} onPress={() => setDollars(Math.max(1, dollars - 1))}>Less</Button>
        <StepBar steps={CUSTOM_MOST} at={dollars - 1} accent={accent} />
        <Button disabled={dollars >= CUSTOM_MOST} onPress={() => setDollars(Math.min(CUSTOM_MOST, dollars + 1))}>More</Button>
      </Stack>
      {footer}
    </Stack>
  </Screen>;

  const entries = shownBoard.entries ?? [];
  return <Screen title={supportTitle}>
    <Stack gap={14}>
      {TEXT.lines.map(line => <Text key={line} size={24}>{line}</Text>)}
      <Text size={18} tone="accent" href="/support/board">{TEXT.fund.toUpperCase()}</Text>
      <Note>{TEXT.fundLine}</Note>
      {!shownBoard.available && <Note>{TEXT.offline}</Note>}
      {shownBoard.available && entries.length === 0 && <Note>{TEXT.empty}</Note>}
      {entries.length > 0 && <Podium entries={entries} accent={accent} />}
      {entries.slice(3, 6).map(entry => <Row key={entry.id} title={`${entry.rank}   ${entry.name || TEXT.anonymous}`} subtitle={entry.message || undefined} />)}
      {entries.length > 3 && <Text size={20} tone="accent" href="/support/board">{`${TEXT.fund} ›`}</Text>}
      <Stack axis="horizontal" align="center" justify="center" gap={10}>
        <Choice label={TEXT.monthly} selected={monthly} width={292} accent={accent} onPress={() => setMonthly(true)} />
        <Choice label={TEXT.oneTime} selected={!monthly} width={292} accent={accent} onPress={() => setMonthly(false)} />
      </Stack>
      <Stack axis="horizontal" align="center" justify="center" gap={8}>
        {tiers.map((option, i) => <Choice key={option.id} label={option.label} selected={i === tier}
          width={monthly ? 192 : 142} accent={accent} onPress={() => setTier(i)} />)}
        {!monthly && <Choice label={TEXT.custom} selected={false} width={142} accent={accent} onPress={() => navigate("/support/custom")} />}
      </Stack>
      <Note align="center">{tiers[tier].line}</Note>
      {footer}
    </Stack>
  </Screen>;
}

/** F's readout: "about {cups} cups", in friendly fifths ("1 2/5 cups"), and "about 1 cup" for one. */
function cupWords(dollars: number) {
  const fifths = Math.round(dollars), whole = Math.floor(fifths / 5), part = fifths % 5;
  if (whole === 1 && part === 0) return "about 1 cup";
  return `about ${[whole || "", part ? `${part}/5` : ""].filter(Boolean).join(" ")} cups`;
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
        <CanvasText key={`t${index}`} x={x + 8} y={y + 70} width={width - 16} height={30} size={24} align="center" colour={CREAM}>{entry.name || TEXT.anonymous}</CanvasText>,
        <CanvasText key={`m${index}`} x={x + 10} y={y + 104} width={width - 20} height={h - 112} size={18} align="center" colour={MUTED}>{entry.message ?? ""}</CanvasText>,
      ];
    })}
  </Canvas>;
}

function RankLine({ text, accent }: { text: string; accent: string }) {
  return <Canvas width={supportWidth} height={58}>
    <RoundedFill x={1} y={1} width={supportWidth - 2} height={56} radius={18} fill="#110E0C" />
    <RoundedOutline x={1} y={1} width={supportWidth - 2} height={56} radius={18} stroke={accent} strokeWidth={1} />
    <CanvasText x={20} y={0} width={supportWidth - 40} height={58} size={24} colour={CREAM}>{text}</CanvasText>
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

/** After the thank-you: the opt-in, with the message limit for the supporter's rank. */
function JoinSupporters({ rank, join }: {
  rank: number | null; join: (anonymous: boolean, name: string, message: string) => Promise<void>;
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
      <TextInput label={TEXT.name} value={name} onChange={value => setName(Array.from(value).slice(0, 40).join(""))} />
      {limit > 0 && <TextInput label={`${TEXT.message} · ${Array.from(message).length}/${limit}`} value={message}
        onChange={value => setMessage(Array.from(value).slice(0, limit).join(""))} />}
      <Note>{TEXT.limit(rankTier(rank))}</Note>
      <Note>{TEXT.privacy}</Note>
      <Checkout label={TEXT.addMe} disabled={saving || !name.trim()} onPress={() => void send(false)} />
      <Button disabled={saving} onPress={() => void send(true)}>{TEXT.keepAnonymous}</Button>
    </Stack>
  </Screen>;
}

/**
 * The thank-you, full screen: the warm rise to the supporter's total, then the words settle on top. Then the way on: the move-up
 * invite (Write more · Later), or Join the board?, or the supporter's own board.
 */
function Thanks({ cups, reduceMotion, moveUp, join, board, later }: {
  cups: number; reduceMotion?: boolean; moveUp: number | null; join: (() => void) | null; board: () => void; later: () => void;
}) {
  const [settled, setSettled] = useState(!!reduceMotion);
  // Over the orange the words are dark; over black, cream.
  const over = cups >= .6;
  return <Screen background={cups >= 1 && settled ? "#FFD9AE" : "#000000"} centered>
    <Stack align="center" gap={14} accessibilityLabel={`${TEXT.thanks} ${TEXT.thanksLine}`}>
      <RisingThanks cups={cups} width={supportWidth} height={600} reduceMotion={reduceMotion} done={() => setSettled(true)}>
        {settled && [
          <CanvasText key="thanks" x={20} y={150} width={supportWidth - 40} height={70} size={56} align="center" colour={over ? INK : CREAM}>{TEXT.thanks}</CanvasText>,
          <CanvasText key="line" x={30} y={232} width={supportWidth - 60} height={130} size={27} align="center" colour={over ? INK : CREAM}>{TEXT.thanksLine}</CanvasText>,
        ]}
      </RisingThanks>
      {settled && moveUp !== null && <Text size={22} align="center">{TEXT.moveUp(moveUp)}</Text>}
      {settled && <Checkout label={moveUp !== null ? TEXT.writeMore : join ? TEXT.join : TEXT.supporters} onPress={join ?? board} />}
      {settled && <Button onPress={later}>{TEXT.later}</Button>}
    </Stack>
  </Screen>;
}

/** Everyone who opted in, ranked: names and messages only, never amounts. A supporter sees the supporters' title. */
function SupportBoard({ board, title, line, preview, remove }: { board: Board; title: string; line: string; preview: boolean; remove: () => void }) {
  const [reported, setReported] = useState<string[]>([]);
  const entries = board.entries ?? [];
  const accent = useAccent();
  return <Screen title={title}>
    <Stack gap={12}>
      <Note>{line}</Note>
      {preview && <Note>{TEXT.sample}</Note>}
      {!board.available && <Note>{TEXT.offline}</Note>}
      {board.available && entries.length === 0 && <Note>{TEXT.empty}</Note>}
      {entries.length > 0 && <Podium entries={entries} accent={accent} />}
      {entries.map(entry => <Stack key={entry.id} gap={2}>
        {entry.rank! > 3 && <Row title={`${entry.rank}   ${entry.name || TEXT.anonymous}`} subtitle={entry.message || undefined} />}
        {!entry.you && (reported.includes(entry.id)
          ? <Note>{TEXT.reported}</Note>
          : <Text size={17} tone="muted" onPress={() => {
            setReported([...reported, entry.id]);
            if (!preview) void reportEntry(entry.id).catch(() => {});
          }}>{entry.rank! <= 3 ? `${TEXT.report} · #${entry.rank}` : TEXT.report}</Text>)}
      </Stack>)}
      <Note>{TEXT.privacy}</Note>
      {board.joined && <Button onPress={remove}>{TEXT.removeMe}</Button>}
    </Stack>
  </Screen>;
}

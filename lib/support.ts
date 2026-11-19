import { callNative, onNativeMessage } from "ink/native";

export const supportTitle = "Support TitanKeys";
export const supportUnavailable = "Google Play support isn't ready yet.";
export const supportPending = "Google Play is still processing this payment.";
export const tipUnit = "support_tip_unit";
export const customPrices = [3, 5, 10, 20, 50];
export const cupCents = 500;

export type Product = { id: string; price: string; baseCents: number };
export type Receipt = { id: string; previousCents: number; totalCents: number; productId: string; quantity: number };
export type SupportState = {
  products: Product[];
  totalCents: number;
  billing: "ready" | "unavailable";
  receipt: Receipt | null;
  reduceMotion?: boolean;
  boardJoined?: boolean;
  preview?: boolean;
  purchaseResult?: "launched" | "pending" | "cancelled" | "unavailable" | null;
};
/** One supporter on the board, ranked by the server; the message arrives already cut to its rank's limit. Never an amount. */
export type BoardEntry = { id: string; rank?: number; name?: string; message?: string; you?: boolean };
export type Board = { available: boolean; postingReady?: boolean; entries?: BoardEntry[]; joined?: boolean; myRank?: number | null };
/** Where adding [addCents] would put this supporter; unavailable hides "This puts you at #N". */
export type RankPreview = { available: boolean; rank?: number };

const invoke = (operation: string, payload: object = {}) => callNative("titankeys", operation, payload);
async function read<T>(operation: string, payload?: object): Promise<T> {
  return JSON.parse(await invoke(operation, payload));
}

export const supportState = () => read<SupportState>("supportState");
export const refreshSupport = () => invoke("supportRefresh");
export const buySupport = (productId: string) => read<SupportState["purchaseResult"]>("supportPurchase", { productId });
export const seeSupportReceipt = (id: string) => invoke("supportReceiptSeen", { id });
export const onSupportChanged = (callback: () => void) => onNativeMessage("titankeys-support", callback);
export const readBoard = () => read<Board>("supportBoardRead");
export const joinBoard = (anonymous: boolean, name: string, message: string) =>
  read<Board>("supportBoardJoin", { anonymous, name, message });
export const removeBoard = () => read<Board>("supportBoardRemove");
export const previewRank = (addCents: number) => read<RankPreview>("supportPreviewRank", { addCents });
export const reportEntry = (id: string) => read<{ available: boolean; ok?: boolean }>("supportReportEntry", { id });

/** How long a message may be at a rank (owner, support flow v4): top 3, 120; top 10, 80; top 100, 40; beyond, name only. */
export const MESSAGE_LIMITS = [[3, 120], [10, 80], [100, 40]] as const;
export function messageLimit(rank: number | null | undefined) {
  if (!rank) return MESSAGE_LIMITS[MESSAGE_LIMITS.length - 1][1];
  return MESSAGE_LIMITS.find(([top]) => rank <= top)?.[1] ?? 0;
}
/** The tier's top for a rank (3, 10 or 100), or null beyond 100. */
export function rankTier(rank: number | null | undefined) {
  return rank ? MESSAGE_LIMITS.find(([top]) => rank <= top)?.[0] ?? null : null;
}

/**
 * Debug's sample board (checkout preview): labelled samples, hidden totals in cents that only order them and answer the preview
 * rank. The sample supporter has given 150 cents before, at rank 14.
 */
const SAMPLE: [string, string, number][] = [
  ["Jordan", "Finally a keyboard that gets the Titan. Swiping on real keys still feels like a magic trick.", 6200],
  ["", "", 4500], ["Priya", "Swipe typing on a hardware keyboard?? Wild.", 3300], ["Tomás", "Obrigado from Lisbon", 2100],
  ["Sam K.", "", 1100], ["", "Keep going.", 800], ["Lukas", "Danke!", 600], ["Ana", "the guide is so good", 500],
  ["R.", "", 400], ["Kenji", "Cantonese next, then Japanese?", 300], ["Olu", "", 300], ["", "", 200],
  ["Marta", "Typing in Polish without Gboard.", 200], ["Dev", "", 100], ["Chris", "my thumbs thank you", 100],
  ["", "", 100], ["Felix", "Hi from Berlin", 50],
];
const SAMPLE_MINE = 150;
let sampleJoined: { name: string; message: string; cents: number } | null = null;
export function sampleBoard(addCents = 0): Board {
  const rows = SAMPLE.map(([name, message, cents], i) => ({ id: `sample-${i}`, name, message, cents, you: false }));
  if (sampleJoined) rows.push({ id: "sample-you", ...sampleJoined, cents: sampleJoined.cents + addCents, you: true });
  rows.sort((a, b) => b.cents - a.cents);
  const entries = rows.map((row, i) => {
    const limit = messageLimit(i + 1);
    return { id: row.id, rank: i + 1, name: row.name, message: limit ? row.message.slice(0, limit) : "", you: row.you };
  });
  return { available: true, postingReady: true, joined: !!sampleJoined, entries,
    myRank: sampleJoined ? entries.find(entry => entry.you)!.rank : sampleRank(0) };
}
export function sampleRank(addCents: number) {
  const mine = (sampleJoined?.cents ?? SAMPLE_MINE) + addCents;
  return SAMPLE.filter(([, , cents]) => cents > mine).length + 1;
}
export function sampleJoin(name: string, message: string, addCents: number) {
  sampleJoined = { name, message, cents: (sampleJoined?.cents ?? SAMPLE_MINE) + addCents };
  return sampleBoard();
}

export function cupQuantity(dollars: number) {
  const whole = Math.floor(dollars / 5);
  const fifths = dollars % 5;
  return fifths ? [whole || "", `${fifths}/5`].filter(Boolean).join(" ") : `${whole}`;
}

export function drinkAmount(receipt: Receipt) {
  if (receipt.productId.endsWith("_sip")) return "this sip";
  if (receipt.productId.endsWith("_small")) return "this fifth of a cup";
  if (receipt.productId.endsWith("_full")) return "these two fifths of a cup";
  const amount = (receipt.totalCents - receipt.previousCents) / cupCents;
  return `about ${amount.toFixed(1)} ${amount === 1 ? "cup" : "cups"}`;
}

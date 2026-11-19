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
/** A place on the board: rank, public id, name and the message as much as the place shows. Never an amount. */
// rank is optional and name and message read as strings, so the current screen's samples still type-check; the server sends
// null for a missing name or a message the place doesn't show.
export type BoardEntry = { rank?: number; id: string; name?: string; message?: string };
/** How much of a message a place shows: top 3 120 characters, top 10 80, top 100 40, beyond that the name only. */
export type MessageTier = "top3" | "top10" | "top100" | "name";
export type Board = {
  available: boolean; postingReady?: boolean; entries?: BoardEntry[]; total?: number; nextOffset?: number | null; joined?: boolean;
  /** After posting: the new place, its tier and how long a message it allows. */
  rank?: number | null; tier?: MessageTier; messageLimit?: number;
};
export type RankPreview = { available: boolean; rank?: number; tier?: MessageTier; messageLimit?: number };

const invoke = (operation: string, payload: object = {}) => callNative("titankeys", operation, payload);
async function read<T>(operation: string, payload?: object): Promise<T> {
  return JSON.parse(await invoke(operation, payload));
}

export const supportState = () => read<SupportState>("supportState");
export const refreshSupport = () => invoke("supportRefresh");
export const buySupport = (productId: string) => read<SupportState["purchaseResult"]>("supportPurchase", { productId });
export const seeSupportReceipt = (id: string) => invoke("supportReceiptSeen", { id });
export const onSupportChanged = (callback: () => void) => onNativeMessage("titankeys-support", callback);
export const readBoard = (offset = 0, limit = 20) => read<Board>("supportBoardRead", { offset, limit });
/** "This puts you at #N": the place [addCents] more would reach. Rate-limited on the server. */
export const previewRank = (addCents: number) => read<RankPreview>("supportRankPreview", { addCents });
export const reportEntry = (id: string, reason = "") => read<{ available: boolean }>("supportReport", { id, reason });
export const joinBoard = (anonymous: boolean, name: string, message: string) =>
  read<Board>("supportBoardJoin", { anonymous, name, message });
export const removeBoard = () => read<Board>("supportBoardRemove");

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

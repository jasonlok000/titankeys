import { callNative, onNativeMessage } from "ink/native";

export const supportTitle = "Support TitanKeys";
export const supportUnavailable = "Google Play support isn't ready yet.";
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
  purchaseResult?: "launched" | "pending" | "cancelled" | "unavailable";
};
export type BoardEntry = { id: string; name?: string; message?: string; cups: number };
export type Board = { available: boolean; entries?: BoardEntry[]; joined?: boolean };

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

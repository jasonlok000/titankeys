// Opens sealed contribution batches with the owner's private key, for training. Run it only on the offline computer
// that holds the key. It writes one JSON line per batch to standard output.
// Usage: bun tools/contributions/open.ts <contributions-x25519.pem> <batch.tkc>...
// The sealed form is made by android/kotlin/com/atlas/connected/Seal.kt: "TKC1", the batch's X25519 public key (32 bytes),
// a 12-byte nonce, then the gzipped JSON, encrypted with AES-256-GCM. The key comes from HKDF-SHA256 over the shared
// secret, with both public keys as salt.
import { gunzipSync } from "node:zlib";

const INFO = new TextEncoder().encode("titankeys contributions v1");
const MAGIC = "TKC1";
const HEADER = 4 + 32 + 12;

export function pem(text: string): Uint8Array {
  return new Uint8Array(Buffer.from(text.replace(/-----[^-]+-----/g, "").replace(/\s+/g, ""), "base64"));
}

/** Opens one sealed batch and returns its JSON text. */
export async function openBatch(sealed: Uint8Array, pkcs8: Uint8Array): Promise<string> {
  if (sealed.length <= HEADER + 16 || new TextDecoder().decode(sealed.subarray(0, 4)) !== MAGIC) throw new Error("Not a sealed batch");
  const ephemeralRaw = sealed.slice(4, 36);
  const privateKey = await crypto.subtle.importKey("pkcs8", pkcs8, { name: "X25519" }, true, ["deriveBits"]);
  const jwk = await crypto.subtle.exportKey("jwk", privateKey);
  const recipientRaw = new Uint8Array(Buffer.from(jwk.x!, "base64url"));
  const ephemeral = await crypto.subtle.importKey("raw", ephemeralRaw, { name: "X25519" }, false, []);
  const secret = await crypto.subtle.deriveBits({ name: "X25519", public: ephemeral }, privateKey, 256);
  const hkdf = await crypto.subtle.importKey("raw", secret, "HKDF", false, ["deriveKey"]);
  const salt = new Uint8Array([...ephemeralRaw, ...recipientRaw]);
  const key = await crypto.subtle.deriveKey({ name: "HKDF", hash: "SHA-256", salt, info: INFO }, hkdf,
    { name: "AES-GCM", length: 256 }, false, ["decrypt"]);
  const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: sealed.slice(36, HEADER), additionalData: sealed.slice(0, 36), tagLength: 128 },
    key, sealed.slice(HEADER));
  return gunzipSync(new Uint8Array(plain)).toString("utf8");
}

if (import.meta.main) {
  const [keyPath, ...files] = process.argv.slice(2);
  if (!keyPath || files.length === 0) {
    console.error("Usage: bun tools/contributions/open.ts <contributions-x25519.pem> <batch.tkc>...");
    process.exit(2);
  }
  const key = pem(await Bun.file(keyPath).text());
  let failed = 0;
  for (const file of files) {
    try {
      process.stdout.write(JSON.stringify(JSON.parse(await openBatch(new Uint8Array(await Bun.file(file).arrayBuffer()), key))) + "\n");
    } catch (error) {
      failed++;
      console.error(`${file}: ${(error as Error).message}`);
    }
  }
  process.exit(failed ? 1 : 0);
}

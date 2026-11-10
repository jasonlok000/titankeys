// The TitanKeys contributions server: a write-only Cloudflare Worker. It stores sealed batches of swipe examples in R2,
// and deletes all of a contributor's batches when the app asks. It can never read a batch: they are sealed on the phone
// with a public key whose private key is kept offline. It has no way to list or download batches; the owner reads the
// bucket with their own Cloudflare login. It logs nothing and keeps no internet addresses.

const MAGIC = [0x54, 0x4b, 0x43, 0x31]; // "TKC1"
const HEADER_BYTES = 4 + 32 + 12;
const TAG_BYTES = 16;
export const MAX_BATCH_BYTES = 1024 * 1024;
const FOLDER = /^[0-9a-f]{64}$/;
/** A Worker may make 1000 storage requests at most; each round makes two. */
const MAX_DELETE_ROUNDS = 400;
const CONTRIBUTOR = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

const answer = (status, body = null) => new Response(body === null ? null : JSON.stringify(body), {
  status,
  headers: body === null ? {} : { "Content-Type": "application/json" },
});

/** The folder for a contributor: a hash of the ID, so that the stored names never show the ID itself. */
export async function folder(contributor) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(contributor));
  return [...new Uint8Array(digest)].map(byte => byte.toString(16).padStart(2, "0")).join("");
}

async function store(request, env) {
  const contributor = request.headers.get("X-Contributor") ?? "";
  if (!FOLDER.test(contributor)) return answer(400, { error: "bad contributor" });
  const length = Number(request.headers.get("Content-Length") ?? "0");
  if (length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  const body = new Uint8Array(await request.arrayBuffer());
  if (body.length > MAX_BATCH_BYTES) return answer(413, { error: "too large" });
  if (body.length <= HEADER_BYTES + TAG_BYTES || MAGIC.some((byte, i) => body[i] !== byte)) return answer(400, { error: "not a sealed batch" });
  const name = `batches/${contributor}/${new Date().toISOString().slice(0, 10)}-${crypto.randomUUID()}.tkc`;
  await env.BATCHES.put(name, body, { httpMetadata: { contentType: "application/octet-stream" } });
  return answer(204);
}

async function remove(request, env) {
  let contributor = "";
  try {
    const text = await request.text();
    if (text.length > 1024) return answer(413, { error: "too large" });
    contributor = JSON.parse(text).contributor ?? "";
  } catch {
    return answer(400, { error: "bad request" });
  }
  if (typeof contributor !== "string" || !CONTRIBUTOR.test(contributor)) return answer(400, { error: "bad contributor" });
  const prefix = `batches/${await folder(contributor)}/`;
  // Each round deletes what it listed, so the next round lists from the start again: a cursor could skip names.
  let deleted = 0;
  for (let round = 0; round < MAX_DELETE_ROUNDS; round++) {
    const listed = await env.BATCHES.list({ prefix, limit: 1000 });
    const names = listed.objects.map(object => object.key);
    if (names.length === 0) return answer(200, { deleted });
    await env.BATCHES.delete(names);
    deleted += names.length;
  }
  // Very many batches: the app asks again, and the next request deletes the rest.
  return answer(503, { deleted });
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    if (request.method === "POST" && pathname === "/v1/batch") return store(request, env);
    if (request.method === "POST" && pathname === "/v1/delete") return remove(request, env);
    return answer(404, { error: "not found" });
  },
};

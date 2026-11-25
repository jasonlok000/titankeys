// Website visit counter (owner, 2026-11-25). Counts one view per page per browser tab, and taps on the join and download
// buttons, by where the visit came from: the referring site's name, an ?ref= label, or "direct". No cookie and no
// identifier; the server keeps only daily totals. See the privacy page, "This website".
(() => {
  if (!/(^|\.)everyones-niche\.com$/.test(location.hostname)) return;
  const api = "https://titankeys-support.everyones-niche.workers.dev/v1/visit";
  const store = (key, value) => { try { if (value === undefined) return sessionStorage.getItem(key); sessionStorage.setItem(key, value); } catch {} return null; };
  let source = store("visit-source");
  if (!source) {
    const query = new URLSearchParams(location.search);
    let referrer = "";
    try { referrer = new URL(document.referrer).hostname; } catch {}
    if (referrer === location.hostname) referrer = "";
    source = query.get("ref") || query.get("utm_source") || (query.has("fbclid") ? "facebook" : "") || referrer || "direct";
    store("visit-source", source);
  }
  const send = event => {
    const url = `${api}?e=${event}&p=${encodeURIComponent(location.pathname)}&s=${encodeURIComponent(source.slice(0, 64))}`;
    try { navigator.sendBeacon(url) || fetch(url, { method: "POST", mode: "no-cors", keepalive: true }); } catch {}
  };
  if (!store("visit-" + location.pathname)) { store("visit-" + location.pathname, "1"); send("view"); }
  document.addEventListener("click", event => {
    const link = event.target.closest && event.target.closest("a[data-visit]");
    if (link) send(link.dataset.visit);
  });
})();

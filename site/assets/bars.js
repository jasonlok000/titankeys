// The live bar on a Titan 2 Elite: TitanKeys' bar drawn at its real size along the screen's bottom edge, playing what each part of
// the page shows, as the keyboard draws it. One SVG unit is one of the screen's 1076 pixels across, so the bar lands 1:1 at any
// display size or zoom. Measured from the phone (StripFit, Strip, GestureDrawing): 88 px tall, words 38 px with their line 61 px
// down at 226, 538 and 850; a swipe dims them and draws its path over the keys' columns (104 px apart from 18); a word drag draws
// words as bars with the selection's orange box and handle. Haptics are Chrome's vibration, which works once the page was tapped.
(() => {
  const root = document.getElementById("phone-bar");
  if (!root || !document.documentElement.classList.contains("t2e")) return;

  const NS = "http://www.w3.org/2000/svg", ORANGE = "#EF804E", CREAM = "#FFFFFF", DIM = "#8A8A8A";
  const LINE = 61, SIZE = 38, BASE = LINE + SIZE * 0.546 / 2, XS = [226, 538, 850];
  const el = (tag, attrs = {}, parent) => {
    const e = document.createElementNS(NS, tag);
    for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
    if (parent) parent.appendChild(e);
    return e;
  };
  const wait = ms => new Promise(r => setTimeout(r, ms));
  let tapped = false;
  addEventListener("pointerdown", () => { tapped = true; }, { once: true });
  const buzz = ms => { if (tapped && navigator.vibrate) navigator.vibrate(ms); };

  // Two layers crossfade: each scene draws into a fresh one and fades it in over the last.
  const layers = [0, 1].map(() => { const d = document.createElement("div"); d.className = "bar-layer"; root.appendChild(d); return d; });
  let front = 0, current = "", run = 0;

  const svg = (h = 88) => el("svg", { viewBox: `0 0 1076 ${h}`, width: "100%", preserveAspectRatio: "xMidYMax meet", "aria-hidden": "true" });
  const bg = (s, h = 88) => el("rect", { width: 1076, height: h, fill: "#000" }, s);
  const word = (s, x, text, { bold = false, fill = CREAM, size = SIZE } = {}) => {
    const t = el("text", { x, y: BASE, "text-anchor": "middle", "font-family": "Inter, system-ui, sans-serif", "font-size": size,
      "font-weight": bold ? 700 : 500, fill }, s);
    t.textContent = text;
    return t;
  };
  const words = (s, list, fill = CREAM) => list.map((w, i) => word(s, XS[i], w, { bold: i === 1, fill }));
  const setWords = (ts, list) => ts.forEach((t, i) => { t.textContent = list[i]; });

  // The key map behind a swipe: Q..P, A..L, Z..M on ten columns; rows 18.75 px apart about the line.
  const KEYS = {};
  ["qwertyuiop", "asdfghjkl"].forEach((r, row) => [...r].forEach((k, c) => (KEYS[k] = [c, row])));
  [..."zxcvbnm"].forEach((k, c) => (KEYS[k] = [c + 1, 2]));
  const keyPoint = k => [18 + 52 + 104 * KEYS[k][0], 56 + (KEYS[k][1] - 1) * 18.75];

  const scenes = {
    async hero(s, alive) {
      bg(s);
      const ts = words(s, ["This", "The", "That"]);
      ts.forEach(t => t.style.opacity = 0);
      for (const t of ts) { if (!alive()) return; t.style.transition = "opacity .4s"; t.style.opacity = 1; await wait(120); }
    },

    async swipe(s, alive) {
      bg(s);
      const ts = words(s, ["I", "the", "a"]);
      while (alive()) {
        setWords(ts, ["I", "the", "a"]); ts.forEach(t => { t.style.transition = "fill .25s"; t.setAttribute("fill", CREAM); });
        await wait(700); if (!alive()) return;
        // The swipe starts: the words dim under its cover, and the path draws key to key.
        ts.forEach(t => t.setAttribute("fill", DIM));
        // A finger's path, not a ruler's: a smooth curve through the keys, a little off each key's middle, as the trail on the phone.
        const off = [[6, 2], [-4, -2], [0, -3], [4, 1], [-2, 5]];
        const pts = [..."swipe"].map((k, i) => { const p = keyPoint(k); return [p[0] + off[i][0], p[1] + off[i][1]]; });
        const smooth = [], at = i => pts[Math.max(0, Math.min(pts.length - 1, i))];
        for (let i = 0; i < pts.length - 1; i++) for (let n = 0; n < 16; n++) {
          const t = n / 16, t2 = t * t, t3 = t2 * t, [p0, p1, p2, p3] = [at(i - 1), at(i), at(i + 1), at(i + 2)];
          smooth.push([0, 1].map(k => .5 * (2 * p1[k] + (p2[k] - p0[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (3 * p1[k] - p0[k] - 3 * p2[k] + p3[k]) * t3)));
        }
        smooth.push(pts[pts.length - 1]);
        const d = smooth.map((p, i) => (i ? "L" : "M") + p[0].toFixed(1) + "," + p[1].toFixed(1)).join(" ");
        const path = el("path", { d, fill: "none", stroke: ORANGE, "stroke-width": 4.2, "stroke-linecap": "round", "stroke-linejoin": "round" }, s);
        const len = path.getTotalLength();
        path.style.strokeDasharray = len; path.style.strokeDashoffset = len;
        const dot = el("circle", { r: 6.5, fill: ORANGE, cx: pts[0][0], cy: pts[0][1] }, s);
        const start = performance.now(), dur = 1100;
        let ticked = 0;
        await new Promise(done => {
          const step = now => {
            if (!alive()) return done();
            const t = Math.min(1, (now - start) / dur), at = path.getPointAtLength(len * t);
            path.style.strokeDashoffset = len * (1 - t);
            dot.setAttribute("cx", at.x); dot.setAttribute("cy", at.y);
            const letter = Math.floor(t * (pts.length - 1) + .001);
            if (letter > ticked) { ticked = letter; buzz(6); }
            t < 1 ? requestAnimationFrame(step) : done();
          };
          requestAnimationFrame(step);
        });
        if (!alive()) return;
        // The lift: the guesses come in, the trail fades.
        buzz(12);
        setWords(ts, ["spite", "swipe", "spire"]);
        ts.forEach(t => t.setAttribute("fill", CREAM));
        path.style.transition = dot.style.transition = "opacity .6s"; path.style.opacity = dot.style.opacity = 0;
        await wait(1900);
        path.remove(); dot.remove();
      }
    },

    async fixes(s, alive) {
      bg(s);
      const ts = words(s, ["I", "the", "a"]);
      const steps = [["to", "t", "the"], ["ten", "te", "tea"], ["ten", "the", "Tehran"]];
      while (alive()) {
        setWords(ts, ["I", "and", "the"]); await wait(800);
        for (const st of steps) { if (!alive()) return; buzz(5); setWords(ts, st); await wait(420); }
        await wait(500); if (!alive()) return;
        // Space: the middle word goes in, a quick flash in the accent, then the next guesses.
        buzz(14);
        ts[1].style.transition = "fill .15s"; ts[1].setAttribute("fill", ORANGE);
        await wait(260); ts[1].setAttribute("fill", CREAM);
        setWords(ts, ["is", "and", "was"]);
        await wait(1500);
      }
    },

    async cursor(s, alive) {
      bg(s);
      // As the owner's phone drew it (screenshot, 2026-11-21): the words as short bars, "Grab" on the left, the box growing over
      // "these" and then "words", its handle at the moving end, and the underglow brighter on the side the drag leans to.
      const defs = el("defs", {}, s), glow = el("linearGradient", { id: "uglow", x1: 0, y1: 0, x2: 1, y2: 0 }, defs);
      [[0, 0], [.15, .25], [.5, .3], [.85, .55], [1, 0]].forEach(([o, a]) => el("stop", { offset: o, "stop-color": ORANGE, "stop-opacity": a }, glow));
      const fade = el("linearGradient", { id: "ufade", x1: 0, y1: 1, x2: 0, y2: 0 }, defs);
      el("stop", { offset: 0, "stop-color": "#fff" }, fade); el("stop", { offset: 1, "stop-color": "#fff", "stop-opacity": 0 }, fade);
      const mask = el("mask", { id: "umask" }, defs); el("rect", { x: 0, y: 64, width: 1076, height: 24, fill: "url(#ufade)" }, mask);
      const under = el("rect", { x: 0, y: 64, width: 1076, height: 24, fill: "url(#uglow)", mask: "url(#umask)", opacity: 0 }, s);
      const half = 10.4;
      const words = [[313, 380], [435, 513], [563, 641], [691, 768], [818, 895]];
      const box = el("rect", { y: LINE - 24.7, height: 49.4, rx: 18, fill: ORANGE, opacity: 0, x: 411, width: 0 }, s);
      const bars = words.map(([l, r]) => el("rect", { x: l, y: LINE - half, width: r - l, height: half * 2, rx: half, fill: "#FFF", opacity: .28 }, s));
      const handle = el("g", { opacity: 0 }, s);
      el("rect", { x: -2.3, y: LINE - 32, width: 4.6, height: 64, rx: 2.3, fill: ORANGE }, handle);
      el("circle", { cx: 0, cy: LINE - 37, r: 8.4, fill: ORANGE }, handle);
      while (alive()) {
        bars.forEach(b => { b.setAttribute("fill", "#FFF"); b.setAttribute("opacity", .28); });
        box.style.transition = handle.style.transition = under.style.transition = "none";
        box.setAttribute("opacity", 0); box.setAttribute("width", 0); handle.setAttribute("opacity", 0); under.setAttribute("opacity", 0);
        await wait(700); if (!alive()) return;
        // Three taps on Space, the third held: the glow comes up along the bottom.
        for (let i = 0; i < 3; i++) { buzz(6); await wait(130); }
        under.style.transition = "opacity .3s"; under.setAttribute("opacity", 1);
        await wait(250);
        for (const last of [1, 2]) {
          if (!alive()) return;
          const r = words[last][1] + 24;
          box.style.transition = "width .22s cubic-bezier(.2,.7,.2,1), opacity .15s";
          box.setAttribute("width", r - 411); box.setAttribute("opacity", 1);
          handle.style.transition = "transform .22s cubic-bezier(.2,.7,.2,1), opacity .15s";
          handle.setAttribute("transform", `translate(${r + 8} 0)`); handle.setAttribute("opacity", 1);
          for (let i = 1; i <= last; i++) { bars[i].setAttribute("fill", "#000"); bars[i].setAttribute("opacity", .85); }
          buzz(9);
          await wait(600);
        }
        await wait(1500);
      }
    },

    async languages(s, alive) {
      bg(s);
      const items = ["EN", "中", "PT", "עב"], centres = [122, 330, 538, 746];
      const pill = el("rect", { y: LINE - 25, height: 50, rx: 25, width: 200, x: centres[0] - 100, fill: ORANGE, "fill-opacity": .24, opacity: 0 }, s);
      const ts = items.map((t, i) => word(s, centres[i], t, { fill: i === 0 ? ORANGE : CREAM, size: SIZE }));
      const back = el("path", { d: "M20,11H7.8l5.6,-5.6L12,4l-8,8l8,8l1.4,-1.4L7.8,13H20Z", fill: "#FFF",
        transform: `translate(${1006 - 20.6} ${LINE - 20.6}) scale(1.72)` }, s);
      while (alive()) {
        for (let i = 0; i < items.length; i++) {
          if (!alive()) return;
          pill.style.transition = "x .3s cubic-bezier(.2,.7,.2,1), opacity .2s";
          pill.setAttribute("x", centres[i] - 100); pill.setAttribute("opacity", 1);
          ts.forEach((t, j) => t.setAttribute("fill", j === i || j === 0 ? ORANGE : CREAM));
          buzz(7);
          await wait(800);
        }
        await wait(500);
      }
    },

    async symbols(s, alive) {
      // The real Sym panel, as the trailer caught it, rising from the bottom edge as the panel does.
      s.setAttribute("viewBox", "0 0 1076 280");
      el("image", { href: "assets/bars/symbols.png", width: 1076, height: 280 }, s);
      s.style.transform = "translateY(100%)";
      await wait(30); if (!alive()) return;
      s.style.transition = "transform .45s cubic-bezier(.2,.7,.2,1)"; s.style.transform = "none";
      buzz(12);
    },

    async download(s, alive) {
      bg(s);
      const t = word(s, 538, "Download now", { bold: true });
      const u = el("rect", { x: 538, y: BASE + 10, width: 0, height: 3, fill: ORANGE }, s);
      await wait(200); if (!alive()) return;
      u.style.transition = "x .5s, width .5s"; u.setAttribute("x", 418); u.setAttribute("width", 240);
    },
  };

  const show = name => {
    if (name === current || !scenes[name]) return;
    current = name;
    const id = ++run, alive = () => id === run;
    const next = layers[1 - front], old = layers[front];
    next.replaceChildren();
    const s = svg(name === "symbols" ? 280 : 88);
    next.appendChild(s);
    next.classList.add("on"); old.classList.remove("on");
    front = 1 - front;
    scenes[name](s, alive);
  };

  const sections = [...document.querySelectorAll("[data-bar]")];
  const seen = new Map();
  const io = new IntersectionObserver(entries => {
    for (const e of entries) seen.set(e.target, e.intersectionRect.height);
    const best = [...seen].sort((a, b) => b[1] - a[1])[0];
    if (best && best[1] > 0) show(best[0].dataset.bar);
  }, { threshold: [0, .1, .2, .3, .4, .5, .6, .7, .8, .9, 1] });
  sections.forEach(s => io.observe(s));
  show("hero");
})();

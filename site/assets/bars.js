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
  // Chrome lets a page vibrate only after a tap on it (scrolling doesn't count), and the Titan's motor needs about 20 ms to be felt:
  // the 5-10 ms ticks of before were too short, so each is stretched. A chip asks for the one tap until it has been given.
  const active = () => !navigator.userActivation || navigator.userActivation.hasBeenActive;
  // The app's own haptic families (Haptics.kt: on/off pulses at the default strength, which is all Chrome's vibration does too,
  // so these are the very same patterns), played where the app plays them: Texture for a swipe's keys, Space taps and marks;
  // Change for an autocorrect and a held Sym; Lock when a selection starts; Boundary for each word it takes and each panel page.
  const H = { texture: [17], boundary: [30], change: [12, 35, 22, 35, 12], lock: [30, 50, 30], confirm: [15, 40, 35] };
  const buzz = name => { if (navigator.vibrate && active()) navigator.vibrate(H[name]); };
  if (navigator.vibrate && !active()) {
    const chip = document.createElement("button");
    chip.type = "button"; chip.className = "haptic-chip"; chip.textContent = "Tap to feel it";
    document.body.appendChild(chip);
    const done = () => { navigator.vibrate(H.confirm); chip.classList.add("gone"); setTimeout(() => chip.remove(), 400); removeEventListener("pointerup", done); };
    addEventListener("pointerup", done);
  }

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
        // The app's swipe ticks (Haptics.kt, SwipeTicks): the first once the finger has gone 0.6 of a key from where it landed, then
        // one every 0.7 of a key travelled, at most every 40 ms, so every key the path crosses is felt, not only its letters.
        // Distances are in keys: 104 px across, 18.75 px between rows.
        const keys = (a, b) => Math.hypot((b.x - a.x) / 104, (b.y - a.y) / 18.75);
        const origin = { x: pts[0][0], y: pts[0][1] };
        let last = origin, travel = 0, lastTick = -1;
        await new Promise(done => {
          const step = now => {
            if (!alive()) return done();
            const t = Math.min(1, (now - start) / dur), at = path.getPointAtLength(len * t);
            path.style.strokeDashoffset = len * (1 - t);
            dot.setAttribute("cx", at.x); dot.setAttribute("cy", at.y);
            travel += keys(last, at); last = at;
            const first = lastTick < 0;
            if (first ? keys(origin, at) >= .6 : travel >= .7 && now - lastTick >= 40) { lastTick = now; travel = 0; buzz("texture"); }
            t < 1 ? requestAnimationFrame(step) : done();
          };
          requestAnimationFrame(step);
        });
        if (!alive()) return;
        // The lift: the guesses come in, the trail fades.
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
        // The app plays nothing for a real key press, the key clicks by itself; on the page there is no key, so a light tick
        // stands in for each press (owner, 2026-11-22: "the typo thng is ok cuz thy cant feel key press").
        for (const st of steps) { if (!alive()) return; buzz("texture"); setWords(ts, st); await wait(420); }
        await wait(500); if (!alive()) return;
        // Space: the middle word goes in, a quick flash in the accent, then the next guesses.
        buzz("change");
        ts[1].style.transition = "fill .15s"; ts[1].setAttribute("fill", ORANGE);
        await wait(260); ts[1].setAttribute("fill", CREAM);
        setWords(ts, ["is", "and", "was"]);
        await wait(1500);
      }
    },

    async cursor(s, alive) {
      bg(s);
      // Both Space gestures, with the bar's usual words between (owner, 2026-11-22: "show both cursor mode and select mode, and
      // normal suggestions in between"). Two taps, the second held: cursor mode, the line of text on the bar with the caret
      // stepping a letter at a time (a Texture tick for each letter, a Boundary for each space, Steer's Confirm as it starts).
      // Three taps, the third held: select mode, as the owner's phone drew it (2026-11-21), the words as short bars, the box
      // growing over "these" and then "words", its handle at the moving end (Select's Lock, a Boundary for each word taken).
      const defs = el("defs", {}, s), glow = el("linearGradient", { id: "uglow", x1: 0, y1: 0, x2: 1, y2: 0 }, defs);
      [[0, 0], [.15, .25], [.5, .3], [.85, .55], [1, 0]].forEach(([o, a]) => el("stop", { offset: o, "stop-color": ORANGE, "stop-opacity": a }, glow));
      const fade = el("linearGradient", { id: "ufade", x1: 0, y1: 1, x2: 0, y2: 0 }, defs);
      el("stop", { offset: 0, "stop-color": "#fff" }, fade); el("stop", { offset: 1, "stop-color": "#fff", "stop-opacity": 0 }, fade);
      const mask = el("mask", { id: "umask" }, defs); el("rect", { x: 0, y: 64, width: 1076, height: 24, fill: "url(#ufade)" }, mask);
      const under = el("rect", { x: 0, y: 64, width: 1076, height: 24, fill: "url(#uglow)", mask: "url(#umask)", opacity: 0 }, s);
      // Space's tap glow (GestureDrawing.glow): a soft hump rising from the bottom edge under the middle word, wider and stronger
      // with each tap (17%, 39%, 71% of the bar; strength .35, .55, .8), growing in 70 ms and fading over 250, a thin line along
      // the bottom on the third tap.
      const hump = el("linearGradient", { id: "uhump", x1: 0, y1: 0, x2: 1, y2: 0 }, defs);
      for (let i = 0; i <= 20; i++) { const u = i / 10 - 1; el("stop", { offset: i / 20, "stop-color": ORANGE, "stop-opacity": (1 - u * u).toFixed(3) }, hump); }
      const tmask = el("mask", { id: "tmask" }, defs); el("rect", { x: 0, y: 69, width: 1076, height: 19, fill: "url(#ufade)" }, tmask);
      const tap = el("rect", { y: 69, height: 19, x: 538, width: 0, fill: "url(#uhump)", mask: "url(#tmask)", opacity: 0 }, s);
      const pulse = el("rect", { y: 86, height: 2, x: 0, width: 1076, fill: ORANGE, opacity: 0 }, s);
      const span = 1076 - 2 * 19, widths = [.17, .39, .71], alphas = [.35, .55, .8];

      // The bar's usual words.
      const usual = el("g", {}, s);
      const ts = words(usual, ["I", "the", "a"]);

      // Cursor mode: the text's line on the bar, the word at the caret in letters and the rest as a dot a letter, the caret orange.
      const LINE_WORDS = ["Move", "the", "cursor", "back", "here"], DOT = 15, GAP = 30;
      const steer = el("g", { opacity: 0 }, s);
      const total = LINE_WORDS.reduce((n, w) => n + w.length * DOT, 0) + GAP * (LINE_WORDS.length - 1);
      let x0 = 538 - total / 2;
      const placed = LINE_WORDS.map(w => {
        const g = el("g", {}, steer), left = x0;
        const dots = [...w].map((_, i) => el("circle", { cx: left + DOT * (i + .5), cy: LINE, r: 3.4, fill: DIM }, g));
        const text = word(g, left + w.length * DOT / 2, w, { size: 30 }); text.setAttribute("opacity", 0);
        x0 += w.length * DOT + GAP;
        return { w, left, dots, text };
      });
      const caret = el("rect", { x: 0, y: LINE - 20, width: 3.5, height: 40, rx: 1.75, fill: ORANGE }, steer);
      const showWord = active => placed.forEach((p, i) => {
        p.text.setAttribute("opacity", i === active ? 1 : 0);
        p.dots.forEach(d => d.setAttribute("opacity", i === active ? 0 : 1));
      });

      // Select mode: the words as short bars, the box and its handle.
      const select = el("g", { opacity: 0 }, s);
      const half = 10.4;
      const bars = [[313, 380], [435, 513], [563, 641], [691, 768], [818, 895]];
      const box = el("rect", { y: LINE - 24.7, height: 49.4, rx: 18, fill: ORANGE, opacity: 0, x: 411, width: 0 }, select);
      const barEls = bars.map(([l, r]) => el("rect", { x: l, y: LINE - half, width: r - l, height: half * 2, rx: half, fill: "#FFF", opacity: .28 }, select));
      const handle = el("g", { opacity: 0 }, select);
      el("rect", { x: -2.3, y: LINE - 32, width: 4.6, height: 64, rx: 2.3, fill: ORANGE }, handle);
      el("circle", { cx: 0, cy: LINE - 37, r: 8.4, fill: ORANGE }, handle);

      const swap = (show, ...hide) => {
        for (const g of [usual, steer, select]) { g.style.transition = "opacity .2s"; }
        show.setAttribute("opacity", 1); hide.forEach(g => g.setAttribute("opacity", 0));
      };
      // Taps on Space: each grows the glow under the middle word; the last is held, and the glow spreads along the bottom.
      const taps = async count => {
        tap.style.transition = "x .07s ease-out, width .07s ease-out, opacity .07s ease-out";
        for (let i = 0; i < count; i++) {
          if (!alive()) return false;
          const w = span * widths[i];
          tap.setAttribute("x", 538 - w / 2); tap.setAttribute("width", w); tap.setAttribute("opacity", alphas[i]);
          buzz("texture");
          if (i === count - 1) { pulse.style.transition = "none"; pulse.setAttribute("opacity", .25); await wait(20); pulse.style.transition = "opacity .3s"; pulse.setAttribute("opacity", 0); }
          await wait(i === count - 1 ? 160 : 190);
          if (i < count - 1) { tap.style.transition = "opacity .25s ease-in"; tap.setAttribute("opacity", alphas[i] * .7); await wait(60);
            tap.style.transition = "x .07s ease-out, width .07s ease-out, opacity .07s ease-out"; }
        }
        tap.style.transition = "x .12s ease-out, width .12s ease-out, opacity .2s";
        tap.setAttribute("x", 19); tap.setAttribute("width", span);
        under.style.transition = "opacity .12s"; under.setAttribute("opacity", 1);
        await wait(120); tap.setAttribute("opacity", 0);
        return alive();
      };
      const lift = async () => {
        under.style.transition = "opacity .25s"; under.setAttribute("opacity", 0);
        tap.style.transition = "none"; tap.setAttribute("opacity", 0); tap.setAttribute("width", 0); tap.setAttribute("x", 538);
      };

      while (alive()) {
        // The usual words.
        swap(usual, steer, select); setWords(ts, ["I", "the", "a"]);
        await wait(1200); if (!alive()) return;

        // Cursor mode: two taps, the second held; the caret walks left from the end, a letter at a time.
        if (!(await taps(2))) return;
        buzz("confirm");
        const stops = [];
        placed.forEach((p, i) => { for (let k = 0; k <= p.w.length; k++) stops.push({ i, k }); });
        let at = stops.length - 1;
        const put = ({ i, k }) => { caret.setAttribute("x", placed[i].left + k * DOT - 1.75); showWord(i); };
        caret.style.transition = "none"; put(stops[at]);
        swap(steer, usual, select);
        await wait(250);
        caret.style.transition = "x .08s ease-out";
        const target = stops.findIndex(st => st.i === 2 && st.k === 0);
        while (at > target) {
          if (!alive()) return;
          const from = stops[at], to = stops[at - 1];
          at--;
          // From the start of a word to the end of the one before is the space between them: a Boundary, not a letter.
          put(to); buzz(to.i !== from.i ? "boundary" : "texture");
          await wait(110);
        }
        await wait(700); if (!alive()) return;
        await lift();
        swap(usual, steer, select); setWords(ts, ["and", "the", "to"]);
        await wait(1300); if (!alive()) return;

        // Select mode: three taps, the third held; the box grows over a word, then the next.
        barEls.forEach(b => { b.setAttribute("fill", "#FFF"); b.setAttribute("opacity", .28); });
        box.style.transition = handle.style.transition = "none";
        box.setAttribute("opacity", 0); box.setAttribute("width", 0); handle.setAttribute("opacity", 0);
        if (!(await taps(3))) return;
        buzz("lock");
        swap(select, usual, steer);
        await wait(130);
        for (const last of [1, 2]) {
          if (!alive()) return;
          const r = bars[last][1] + 24;
          box.style.transition = "width .22s cubic-bezier(.2,.7,.2,1), opacity .15s";
          box.setAttribute("width", r - 411); box.setAttribute("opacity", 1);
          handle.style.transition = "transform .22s cubic-bezier(.2,.7,.2,1), opacity .15s";
          handle.setAttribute("transform", `translate(${r + 8} 0)`); handle.setAttribute("opacity", 1);
          for (let i = 1; i <= last; i++) { barEls[i].setAttribute("fill", "#000"); barEls[i].setAttribute("opacity", .85); }
          buzz("boundary");
          await wait(600);
        }
        await wait(900); if (!alive()) return;
        await lift();
      }
    },

    async languages(s, alive) {
      bg(s);
      // The switcher as the app draws four languages (owner, 2026-11-22, #101): Q–W, E–R, T–Y and U–I, two keys each (keys 104 px
      // apart from 18), every pill drawn in by the same amount so the one over Q clears the screen's corner; Back on P. Holding
      // Sym plays Change as it opens (Haptic.HeldSymbol), and the pill moving to another language a Texture tick (MenuMark).
      const items = ["EN", "中", "PT", "עב"], centres = [122, 330, 538, 746], PW = 150;
      const pill = el("rect", { y: LINE - 25, height: 50, rx: 25, width: PW, x: centres[0] - PW / 2, fill: ORANGE, "fill-opacity": .24, opacity: 0 }, s);
      const ts = items.map((t, i) => word(s, centres[i], t, { fill: i === 0 ? ORANGE : CREAM, size: SIZE }));
      const back = el("path", { d: "M20,11H7.8l5.6,-5.6L12,4l-8,8l8,8l1.4,-1.4L7.8,13H20Z", fill: "#FFF",
        transform: `translate(${1024 - 20.6} ${LINE - 20.6}) scale(1.72)` }, s);
      await wait(150); if (!alive()) return;
      buzz("change");
      await wait(500);
      while (alive()) {
        for (let i = 0; i < items.length; i++) {
          if (!alive()) return;
          pill.style.transition = "x .3s cubic-bezier(.2,.7,.2,1), opacity .2s";
          pill.setAttribute("x", centres[i] - PW / 2); pill.setAttribute("opacity", 1);
          ts.forEach((t, j) => t.setAttribute("fill", j === i || j === 0 ? ORANGE : CREAM));
          buzz("texture");
          await wait(800);
        }
        await wait(500);
      }
    },

    async symbols(s, alive) {
      // The real Sym panel, as the trailer shows it: it rises from the bottom edge on its symbols page, turns to an emoji page, and a
      // resting finger drags the orange highlight from emoji to emoji, a tick for each, the emoji staying bright on the orange.
      s.setAttribute("viewBox", "0 0 1076 280");
      const defs = el("defs", {}, s);
      const keep = el("filter", { id: "keepemoji", "color-interpolation-filters": "sRGB" }, defs);
      // Black goes see-through, so the emoji alone are laid back over the highlight.
      el("feColorMatrix", { type: "matrix", values: "1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  3 3 3 0 -0.15" }, keep);
      const symbols = el("image", { href: "assets/bars/symbols.png", width: 1076, height: 280 }, s);
      const emoji = el("image", { href: "assets/bars/symbols-emoji1.png", width: 1076, height: 280, opacity: 0 }, s);
      const clip = el("clipPath", { id: "markclip" }, defs);
      const clipRect = el("rect", { width: 100, height: 60, rx: 14 }, clip);
      const mark = el("rect", { width: 100, height: 60, rx: 14, fill: ORANGE, opacity: 0 }, s);
      const lit = el("image", { href: "assets/bars/symbols-emoji1.png", width: 1076, height: 280, filter: "url(#keepemoji)", "clip-path": "url(#markclip)", opacity: 0 }, s);
      [symbols, emoji, mark, lit].forEach(e => { e.style.transition = "opacity .25s"; });
      s.style.transform = "translateY(100%)";
      await wait(40); if (!alive()) return;
      s.style.transition = "transform .45s cubic-bezier(.2,.7,.2,1)"; s.style.transform = "none";
      // The emoji page's grid, in the trailer's frame: columns 108 px apart from 160, rows at 50, 118 and 184.
      const cols = [160, 268, 376, 484, 592, 700, 808, 916], rows = [50, 118, 184];
      const route = [[0, 1], [1, 1], [2, 1], [3, 1], [3, 0], [4, 0], [5, 0], [5, 1], [6, 1], [6, 2], [5, 2], [4, 2]];
      const place = ([c, r]) => {
        const x = cols[c] - 50, y = rows[r] - 30;
        for (const e of [mark, clipRect]) { e.setAttribute("x", x); e.setAttribute("y", y); }
      };
      // The page turns once; after that the highlight keeps travelling over the emoji, there and back, so nothing on the panel
      // flashes between loops (owner: "the bottom left of the screen flashes", the page tabs switching each loop).
      await wait(1300); if (!alive()) return;
      buzz("boundary");
      emoji.setAttribute("opacity", 1); symbols.setAttribute("opacity", 0);
      await wait(500); if (!alive()) return;
      place(route[0]);
      await wait(20);
      mark.setAttribute("opacity", 1); lit.setAttribute("opacity", 1);
      const path = route.concat(route.slice(1, -1).reverse());
      for (let i = 0; alive(); i = (i + 1) % path.length) {
        place(path[i]); buzz("texture");
        await wait(i === route.length - 1 ? 700 : 190);
      }
    },

    async download(s, alive) {
      bg(s);
      const t = word(s, 538, "Download now", { bold: true });
      const u = el("rect", { x: 538, y: BASE + 10, width: 0, height: 3, fill: ORANGE }, s);
      await wait(200); if (!alive()) return;
      u.style.transition = "x .5s, width .5s"; u.setAttribute("x", 418); u.setAttribute("width", 240);
    },
  };

  // "Download now" on the bar is a real button: a tap on it goes where the page's Download button goes.
  root.addEventListener("click", () => {
    if (current !== "download") return;
    const link = document.querySelector(".hero .actions .button");
    if (link) location.href = link.href;
  });
  const show = name => {
    if (name === current || !scenes[name]) return;
    current = name;
    root.style.pointerEvents = name === "download" ? "auto" : "none";
    root.style.cursor = name === "download" ? "pointer" : "";
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

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
  // so these are the very same patterns): Texture for a swipe's keys, letters and marks; Change for an autocorrect; Lock when a
  // selection starts; Boundary for each word it takes and each panel page.
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
        let ticked = 0;
        await new Promise(done => {
          const step = now => {
            if (!alive()) return done();
            const t = Math.min(1, (now - start) / dur), at = path.getPointAtLength(len * t);
            path.style.strokeDashoffset = len * (1 - t);
            dot.setAttribute("cx", at.x); dot.setAttribute("cy", at.y);
            const letter = Math.floor(t * (pts.length - 1) + .001);
            if (letter > ticked) { ticked = letter; buzz("texture"); }
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
        for (let i = 0; i < 3; i++) { buzz("texture"); await wait(130); }
        buzz("lock");
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
          buzz("boundary");
          await wait(600);
        }
        await wait(1500);
      }
    },

    async languages(s, alive) {
      bg(s);
      // The switcher over W to I, clear of the screen's corner (owner, 2026-11-21: "can we make the language menu from w to i
      // instead? no q"; over Q, EN was cut by the corner). Four languages share W's left edge (122) to I's right (850) evenly;
      // Back stays on P.
      const items = ["EN", "中", "PT", "עב"], centres = [213, 395, 577, 759], PW = 150;
      const pill = el("rect", { y: LINE - 25, height: 50, rx: 25, width: PW, x: centres[0] - PW / 2, fill: ORANGE, "fill-opacity": .24, opacity: 0 }, s);
      const ts = items.map((t, i) => word(s, centres[i], t, { fill: i === 0 ? ORANGE : CREAM, size: SIZE }));
      const back = el("path", { d: "M20,11H7.8l5.6,-5.6L12,4l-8,8l8,8l1.4,-1.4L7.8,13H20Z", fill: "#FFF",
        transform: `translate(${1024 - 20.6} ${LINE - 20.6}) scale(1.72)` }, s);
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

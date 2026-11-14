// Generates app/src/main/java/com/rainy/clawdpet/wallpaper/PetSprites.kt from the three sprite templates in the desktop
// app's renderer/index.html (tpl-clawd / tpl-cat / tpl-sheep), so the wallpaper draws exactly the same rectangles instead of
// a hand-copied list. Run it again whenever those templates change:
//
//   node scripts/generate-sprites.js
//
// What is taken from each template (everything else, the laptop, headphones, DJ table, ball, cup, surfboard, butterfly and the
// think bubble, belongs to tricks the wallpaper does not have):
//   - the body pieces in draw order: fixed rects, legs (with their hip pivot), the cat's tail tip, Clawd's arms
//   - the three eye sets (normal, happy, scared) and where they sit in the draw order
//   - Clawd's hanging legs (shown while it is held), the head zone rect, and the wrappers around the hearts and parachute
// The parachute and heart rectangles are shared by all three templates.

const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const html = fs.readFileSync(path.join(root, 'renderer', 'index.html'), 'utf8');
const outFile = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'rainy', 'clawdpet', 'wallpaper', 'PetSprites.kt');

const MODELS = ['clawd', 'cat', 'sheep'];
const SKIP_IDS = new Set([
  'parachute', 'think-bubble', 'butterfly', 'laptop', 'laptop-hold', 'headphones', 'dj-table', 'notes', 'cup', 'ball', 'board',
  'sea-back', 'sea-front', 'sit-legs', 'torso-shade', 'hearts', 'hang-legs', 'eyes-n', 'eyes-h', 'eyes-x', 'head-zone',
]);

function templateOf(model) {
  const m = html.match(new RegExp(`<script type="text/svg-template" id="tpl-${model}">([\\s\\S]*?)</script>`));
  if (!m) throw new Error(`template tpl-${model} not found`);
  return m[1].replace(/<!--[\s\S]*?-->/g, '');
}

function attrsOf(text) {
  const attrs = {};
  for (const m of text.matchAll(/([\w:-]+)(?:="([^"]*)")?/g)) attrs[m[1]] = m[2] === undefined ? '' : m[2];
  return attrs;
}

// Returns every <rect> in document order with its attributes, effective fill and the chain of enclosing elements.
function rectsOf(markup) {
  const out = [];
  const stack = [];
  let uid = 0;
  for (const t of markup.matchAll(/<(\/?)([a-zA-Z][\w:-]*)((?:\s+[\w:-]+(?:="[^"]*")?)*)\s*(\/?)>/g)) {
    const [, close, name, rawAttrs, selfClose] = t;
    if (close) { stack.pop(); continue; }
    const a = attrsOf(rawAttrs);
    const frame = { uid: ++uid, name, id: a.id || '', classes: (a.class || '').split(/\s+/).filter(Boolean), style: a.style || '', transform: a.transform || '', fill: a.fill || '' };
    if (name === 'rect') {
      const fill = a.fill || [...stack].reverse().map((f) => f.fill).find(Boolean) || '#000000';
      out.push({ x: +a.x, y: +a.y, w: +a.width, h: +a.height, fill, classes: frame.classes, id: frame.id, chain: stack.slice() });
    }
    if (!selfClose) stack.push(frame);
  }
  return out;
}

const insideId = (r, id) => r.chain.some((f) => f.id === id);
const skipped = (r) => r.chain.some((f) => SKIP_IDS.has(f.id)) || SKIP_IDS.has(r.id) || r.chain.some((f) => f.name === 'defs' || f.name === 'clipPath');

// translate(a b) / scale(a b) found on the elements ABOVE the element with the given id.
function wrapperOf(markup, id) {
  const first = rectsOf(markup).find((r) => insideId(r, id));
  const result = { dx: 0, dy: 0, sx: 1, sy: 1 };
  if (!first) return result;
  const idx = first.chain.findIndex((f) => f.id === id);
  for (const f of first.chain.slice(0, idx)) {
    const tr = f.transform.match(/translate\(\s*(-?[\d.]+)[ ,]+(-?[\d.]+)\s*\)/);
    const sc = f.transform.match(/scale\(\s*(-?[\d.]+)[ ,]+(-?[\d.]+)\s*\)/);
    if (tr) { result.dx += +tr[1]; result.dy += +tr[2]; }
    if (sc) { result.sx *= +sc[1]; result.sy *= +sc[2]; }
  }
  return result;
}

const f = (n) => `${Number.isInteger(n) ? n : +n.toFixed(4)}f`;
const rk = (r) => `r(${f(r.x)}, ${f(r.y)}, ${f(r.w)}, ${f(r.h)}, "${r.fill.toUpperCase()}")`;
const list = (rs, indent) => `listOf(\n${rs.map((r) => `${indent}    ${rk(r)},`).join('\n')}\n${indent})`;

function buildModel(model) {
  const markup = templateOf(model);
  const rects = rectsOf(markup);
  const layers = [];
  let fixed = [];
  const flush = () => { if (fixed.length) layers.push(`Layer.Fixed(${list(fixed, '            ')})`); fixed = []; };
  const legGroups = new Map();
  let eyesPlaced = false;
  const eyes = { n: [], h: [], x: [] };
  const hang = [];
  let headZone = null;

  for (const r of rects) {
    if (r.id === 'head-zone') { headZone = r; continue; }
    if (insideId(r, 'eyes-n')) { eyes.n.push(r); if (!eyesPlaced) { flush(); layers.push('Layer.Eyes'); eyesPlaced = true; } continue; }
    if (insideId(r, 'eyes-h')) { eyes.h.push(r); continue; }
    if (insideId(r, 'eyes-x')) { eyes.x.push(r); continue; }
    if (insideId(r, 'hang-legs')) { hang.push(r); continue; }
    if (skipped(r)) continue;

    const legFrame = r.chain.slice().reverse().find((fr) => fr.classes.includes('leg'));
    if (legFrame || r.classes.includes('leg')) {
      const key = legFrame ? legFrame.uid : `rect-${r.x}-${r.y}`;
      let g = legGroups.get(key);
      if (!g) {
        flush();
        const isA = (legFrame ? legFrame.classes : r.classes).includes('leg-a');
        const origin = legFrame && legFrame.style.match(/transform-origin:\s*(-?[\d.]+)px\s+(-?[\d.]+)px/);
        g = { rects: [], isA, px: origin ? +origin[1] : r.x + r.w / 2, py: origin ? +origin[2] : r.y };
        legGroups.set(key, g);
        layers.push(g);
      }
      g.rects.push(r);
      continue;
    }
    if (r.classes.includes('tail')) { flush(); layers.push({ tail: [r] }); continue; }
    if (r.classes.includes('arm')) { flush(); layers.push({ arm: r, left: r.classes.includes('arm-l') }); continue; }
    fixed.push(r);
  }
  flush();

  const out = layers.map((l) => {
    if (typeof l === 'string') return l;
    if (l.rects) return `Layer.Leg(${list(l.rects, '            ')}, ${f(l.px)}, ${f(l.py)}, ${l.isA})`;
    if (l.tail) return `Layer.Tail(${list(l.tail, '            ')})`;
    // pivots from base.css: .arm-l { transform-origin: 10px 20px }, .arm-r { 70px 20px }
    return `Layer.Arm(${rk(l.arm)}, ${l.left ? '10f' : '70f'}, 20f, ${l.left})`;
  });

  // hanging legs come as (upper, foot) pairs in the markup
  const hangPairs = [];
  for (let i = 0; i + 1 < hang.length; i += 2) hangPairs.push(`HangLeg(${rk(hang[i])}, ${rk(hang[i + 1])}, ${hang[i + 1].classes.includes('hf-a')})`);

  const hearts = wrapperOf(markup, 'hearts');
  const chute = wrapperOf(markup, 'parachute');
  if (!headZone) throw new Error(`no head-zone in tpl-${model}`);

  return `    val ${model} = ModelSprite(
        id = "${model}",
        layers = listOf(
${out.map((l) => `            ${l},`).join('\n')}
        ),
        eyesNormal = ${list(eyes.n, '        ')},
        eyesHappy = ${list(eyes.h, '        ')},
        eyesScared = ${list(eyes.x, '        ')},
        hangLegs = ${hangPairs.length ? `listOf(\n${hangPairs.map((p) => `            ${p},`).join('\n')}\n        )` : 'emptyList()'},
        headZone = Zone(${f(headZone.x)}, ${f(headZone.y)}, ${f(headZone.w)}, ${f(headZone.h)}),
        heartsDx = ${f(hearts.dx)}, heartsDy = ${f(hearts.dy)},
        chuteDy = ${f(chute.dy)}, chuteScaleY = ${f(chute.sy)},
    )`;
}

// Shared parachute and hearts (identical in every template; read from Clawd's).
const shared = rectsOf(templateOf('clawd'));
const chuteRects = shared.filter((r) => insideId(r, 'parachute'));
const heartRects = (n) => shared.filter((r) => insideId(r, 'hearts') && r.chain.some((fr) => fr.classes.includes(`heart-${n}`)));

const kotlin = `// AUTO-GENERATED by scripts/generate-sprites.js from ../renderer/index.html: do not edit by hand.
// Edit the sprite templates there, then re-run:
//   node scripts/generate-sprites.js
package com.rainy.clawdpet.wallpaper

import android.graphics.Color

internal object PetSprites {
    private fun r(x: Float, y: Float, w: Float, h: Float, color: String) = SpriteRect(x, y, w, h, Color.parseColor(color))

${MODELS.map(buildModel).join('\n\n')}

    val heart1 = ${list(heartRects(1), '    ')}

    val heart2 = ${list(heartRects(2), '    ')}

    /** The shared parachute, in the local space of its wrapper (see ModelSprite.chuteDy / chuteScaleY). */
    val chute = ${list(chuteRects, '    ')}

    fun forModel(id: String?): ModelSprite = when (id) {
        "cat" -> cat
        "sheep" -> sheep
        else -> clawd
    }
}
`;

fs.writeFileSync(outFile, kotlin);
console.log(`Wrote ${path.relative(process.cwd(), outFile)}: ${kotlin.split('\n').length} lines, ${chuteRects.length} chute rects`);

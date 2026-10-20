// Renders the «пульк» drop sound variants as WAV files to listen to: node tools/drop-sounds.js <out dir>
// A drop into water sounds like a small air bubble ringing: a soft tone whose pitch rises as the bubble rises.
const fs = require('fs'), path = require('path');
const out = process.argv[2] || '.';
const RATE = 22050;

/** Same synthesis as Feedback.drop() in the app: bubbles = [start s, from Hz, to Hz, rise s, decay s, level]. */
function render(bubbles, dur, vol, soft) {
  const n = Math.round(dur * RATE), buf = new Float64Array(n);
  for (const [t0, f0, f1, rise, decay, level] of bubbles) {
    let phase = 0;
    for (let i = Math.round(t0 * RATE); i < n; i++) {
      const t = i / RATE - t0;
      const f = f0 + (f1 - f0) * (1 - Math.exp(-t / rise));
      phase += 2 * Math.PI * f / RATE;
      const env = (1 - Math.exp(-t / 0.006)) * Math.exp(-t / decay);   // soft start, no click
      buf[i] += Math.sin(phase) * env * level;
    }
  }
  // a gentle low-pass: rounds off the top, makes it «wet» rather than «beepy»
  let y = 0; for (let i = 0; i < n; i++) { y += soft * (buf[i] - y); buf[i] = y; }
  const fade = Math.round(0.02 * RATE); for (let i = 0; i < fade; i++) buf[n - 1 - i] *= i / fade;
  return Int16Array.from(buf, v => Math.max(-32768, Math.min(32767, Math.round(v * vol * 32767))));
}

function wav(samples) {
  const h = Buffer.alloc(44), data = Buffer.from(samples.buffer);
  h.write('RIFF', 0); h.writeUInt32LE(36 + data.length, 4); h.write('WAVE', 8); h.write('fmt ', 12);
  h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(1, 22); h.writeUInt32LE(RATE, 24);
  h.writeUInt32LE(RATE * 2, 28); h.writeUInt16LE(2, 32); h.writeUInt16LE(16, 34); h.write('data', 36); h.writeUInt32LE(data.length, 40);
  return Buffer.concat([h, data]);
}

const VARIANTS = {
  // A: one round bubble and a small one after it — «буль»
  'pulk-A-bul': [[[0, 300, 820, 0.035, 0.065, 1], [0.075, 620, 1050, 0.02, 0.035, 0.35]], 0.26, 0.42, 0.35],
  // B: deeper and slower, like a drop in a full glass
  'pulk-B-glass': [[[0, 220, 560, 0.05, 0.09, 1], [0.1, 450, 700, 0.03, 0.05, 0.3]], 0.32, 0.45, 0.28],
  // C: lighter and quicker, a tiny drip
  'pulk-C-drip': [[[0, 420, 1150, 0.025, 0.045, 1], [0.05, 900, 1400, 0.015, 0.025, 0.25]], 0.18, 0.38, 0.4],
};
fs.mkdirSync(out, { recursive: true });
for (const [name, [b, dur, vol, soft]] of Object.entries(VARIANTS)) { fs.writeFileSync(path.join(out, name + '.wav'), wav(render(b, dur, vol, soft))); console.log(name); }

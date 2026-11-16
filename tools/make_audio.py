"""Generates all game audio procedurally (original; no samples, no licensed material).
Run: python3 tools/make_audio.py   ->  app/src/main/res/raw/*.wav"""
import math, random, struct, wave, os
SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

def write(name, samples, peak=0.85):
    m = max(abs(s) for s in samples) or 1.0
    data = b"".join(struct.pack("<h", int(max(-1, min(1, s / m * peak)) * 32767)) for s in samples)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data)

def env(i, n, attack=0.005, decay=6.0):
    t = i / SR
    return min(1.0, t / attack) * math.exp(-decay * t)

def lowpass(x, a):
    y, p = [], 0.0
    for v in x: p += a * (v - p); y.append(p)
    return y

rnd = random.Random(7)

# tile placement: a wooden "tock" (low thump + short filtered noise click)
n = int(SR * 0.22)
noise = lowpass([rnd.uniform(-1, 1) for _ in range(n)], 0.25)
write("sfx_place", [0.9 * math.sin(2 * math.pi * (190 - 60 * i / n) * i / SR) * env(i, n, 0.002, 18) + 0.5 * noise[i] * env(i, n, 0.001, 45) for i in range(n)])

# stone movement: soft gliding tone with a gentle rise and fall
n = int(SR * 0.35)
write("sfx_move", [math.sin(2 * math.pi * (330 + 140 * math.sin(math.pi * i / n)) * i / SR) * math.sin(math.pi * i / n) ** 2 * 0.6
                   + 0.15 * math.sin(2 * math.pi * 660 * i / SR) * math.sin(math.pi * i / n) ** 2 for i in range(n)])

# elimination: falling tone with slow tremolo
n = int(SR * 0.8)
write("sfx_eliminate", [math.sin(2 * math.pi * (520 * math.exp(-2.2 * i / n) + 60) * i / SR) * (0.75 + 0.25 * math.sin(2 * math.pi * 14 * i / SR)) * env(i, n, 0.01, 3.2) for i in range(n)])

# victory: rising major arpeggio with a bell-like decay
notes = [(261.63, 0.00), (329.63, 0.16), (392.00, 0.32), (523.25, 0.48), (659.25, 0.70)]
n = int(SR * 2.2); buf = [0.0] * n
for f, start in notes:
    s0 = int(start * SR)
    for i in range(n - s0):
        t = i / SR
        buf[s0 + i] += (math.sin(2 * math.pi * f * t) + 0.35 * math.sin(2 * math.pi * 2 * f * t) + 0.12 * math.sin(2 * math.pi * 3 * f * t)) * min(1, t / 0.004) * math.exp(-2.4 * t)
write("sfx_victory", buf)

# background music: 16 s seamless ambient loop (Am - F - C - G), soft pads
chords = [[110.0, 220.0, 261.63, 329.63], [87.31, 174.61, 261.63, 349.23], [130.81, 196.0, 261.63, 329.63], [98.0, 196.0, 246.94, 293.66]]
seg = 4.0; n = int(SR * seg * len(chords)); buf = [0.0] * n
for ci, ch in enumerate(chords):
    for f in ch:
        for i in range(int(SR * (seg + 1.5))):
            t = i / SR
            e = min(1.0, t / 1.6) * (1.0 if t < seg else max(0.0, 1 - (t - seg) / 1.5))
            idx = (ci * int(SR * seg) + i) % n  # wrap so the tail of the last chord fades into the first: seamless loop
            buf[idx] += math.sin(2 * math.pi * f * t + 0.3 * math.sin(2 * math.pi * 0.2 * t)) * e * 0.22
write("music_loop", buf, peak=0.45)
print("done")

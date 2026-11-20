"""Create missing Fault assets only; existing art/audio is never overwritten."""
from pathlib import Path
import math
import random
import struct
import subprocess
import tempfile
import wave
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sprite = ROOT / 'sprites/blocks/turrets/fault.png'
if not sprite.exists():
    image = Image.new('RGBA', (64, 64))
    d = ImageDraw.Draw(image)
    # Engine-facing orientation is up. Split forward arms leave a recessed center.
    d.polygon([(15,51),(13,30),(19,16),(21,5),(28,3),(30,26),(34,26),(36,3),(43,5),(45,16),(51,30),(49,51),(40,59),(24,59)], fill='#242b32')
    d.polygon([(17,49),(16,31),(22,18),(24,7),(27,6),(28,30),(36,30),(38,6),(41,7),(43,18),(48,31),(46,49),(39,55),(25,55)], fill='#697b80')
    d.polygon([(17,34),(23,26),(23,12),(26,8),(26,34),(31,39),(31,51),(25,54),(19,48)], fill='#a1b0ad')
    d.polygon([(47,34),(41,26),(41,12),(38,8),(38,34),(33,39),(33,51),(39,54),(45,48)], fill='#53636c')
    d.polygon([(24,36),(32,29),(40,36),(39,46),(32,53),(25,46)], fill='#353e49')
    d.polygon([(27,37),(32,33),(37,37),(36,44),(32,48),(28,44)], fill='#ffab68')
    d.polygon([(29,38),(32,35),(35,38),(34,42),(32,44),(30,42)], fill='#ffe3bd')
    for x in [23,38]:
        d.rectangle((x,10,x+2,23), fill='#ffab68')
        d.rectangle((x,10,x+2,13), fill='#fff0d5')
    d.line([(18,36),(22,42),(22,49)], fill='#414f59', width=2)
    d.line([(46,36),(42,42),(42,49)], fill='#a1b0ad', width=2)
    sprite.parent.mkdir(parents=True, exist_ok=True)
    image.save(sprite)
    print('Created', sprite.relative_to(ROOT))

rate = 44100
for name, duration in [('charge', 0.4), ('fire', 0.65)]:
    target = ROOT / f'sounds/cryon-fault-{name}.ogg'
    if target.exists():
        continue
    rng = random.Random(831 if name == 'charge' else 832)
    samples = []
    phase = 0.0
    low_noise = 0.0
    for i in range(int(rate * duration)):
        t = i / rate
        p = t / duration
        noise = rng.uniform(-1, 1)
        low_noise = 0.84 * low_noise + 0.16 * noise
        if name == 'charge':
            phase += 2 * math.pi * (155 + 600 * p * p) / rate
            envelope = min(t / 0.025, 1) * min((duration - t) / 0.018, 1) * (0.2 + 0.8 * p)
            value = envelope * (0.43 * math.sin(phase) + 0.2 * math.sin(phase * 1.51) + 0.2 * low_noise)
        else:
            phase += 2 * math.pi * (65 + 420 * math.exp(-t * 19)) / rate
            attack = min(t / 0.002, 1)
            body = math.exp(-t * 8) * (0.45 * math.sin(phase) + 0.22 * math.sin(phase * 2.07))
            crack = noise * math.exp(-t * 45) * 0.32
            tear = low_noise * math.exp(-t * 10) * (0.4 + 0.3 * math.sin(t * 135))
            echo = 0 if t < 0.12 else 0.12 * math.sin(phase * 1.33) * math.exp(-(t - 0.12) * 10)
            value = attack * (body + crack + tear + echo) * min((duration - t) / 0.035, 1)
        samples.append(value)
    gain = 0.82 / max(abs(x) for x in samples)
    with tempfile.TemporaryDirectory(prefix='cryon-fault-audio-') as temp:
        wav = Path(temp) / 'source.wav'
        with wave.open(str(wav), 'wb') as out:
            out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate)
            out.writeframes(b''.join(struct.pack('<h', round(x * gain * 32767)) for x in samples))
        subprocess.run(['ffmpeg', '-v', 'error', '-n', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '5', str(target)], check=True)
    print('Created', target.relative_to(ROOT))

# Revised firing cue: sharp upper-register laser chirp, distinct from the charge hum.
# Versioned asset preserves previous files and remains safe to rerun.
target = ROOT / 'sounds/cryon-fault-fire-high.ogg'
if not target.exists():
    duration = 0.34
    samples = []
    phase = phase2 = 0.0
    rng = random.Random(19021)
    for i in range(int(rate * duration)):
        t = i / rate
        if t < 0.024:
            frequency = 1650 + 1550 * (t / 0.024) ** 0.6
        else:
            frequency = 780 + 2420 * math.exp(-(t - 0.024) * 17)
        phase += 2 * math.pi * frequency / rate
        phase2 += 2 * math.pi * (frequency * 1.012 + 15 * math.sin(t * 210)) / rate
        attack = min(t / 0.0018, 1)
        envelope = attack * math.exp(-max(t - 0.018, 0) * 11) * min((duration - t) / 0.04, 1)
        carrier = 0.53 * math.sin(phase) + 0.22 * math.sin(phase2) + 0.09 * math.sin(phase * 2)
        snap = rng.uniform(-1, 1) * math.exp(-t * 150) * 0.14
        # Short bright release, without the old low-frequency explosive body.
        value = envelope * carrier + attack * snap
        samples.append(value)
    gain = 0.87 / max(abs(x) for x in samples)
    with tempfile.TemporaryDirectory(prefix='cryon-fault-high-') as temp:
        wav = Path(temp) / 'source.wav'
        with wave.open(str(wav), 'wb') as out:
            out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate)
            out.writeframes(b''.join(struct.pack('<h', round(x * gain * 32767)) for x in samples))
        subprocess.run(['ffmpeg', '-v', 'error', '-n', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '6', str(target)], check=True)
    print('Created', target.relative_to(ROOT))

# Electrical breakdown: broadband arc noise and irregular restrikes, with no pitched sweep.
target = ROOT / 'sounds/cryon-fault-fire-arc.ogg'
if not target.exists():
    import numpy as np
    duration = 0.42
    count = int(rate * duration)
    rng = np.random.default_rng(58217)
    times = np.arange(count) / rate
    raw = rng.standard_normal(count)
    frequencies = np.fft.rfftfreq(count, 1 / rate)
    # Remove rumble; retain the coarse midrange and sizzling electrical presence.
    response = (1 - np.exp(-(frequencies / 280) ** 4)) * np.exp(-(frequencies / 10000) ** 4)
    response *= 0.65 + 1.15 * np.exp(-((frequencies - 2900) / 2300) ** 2)
    noise = np.fft.irfft(np.fft.rfft(raw) * response, n=count)
    noise /= np.std(noise)
    envelope = 0.23 * np.exp(-times * 11)
    for start, strength, decay in [(0, 1, 100), (0.013, .7, 140), (.032, .8, 120),
                                    (.057, .62, 110), (.092, .55, 100), (.138, .43, 100),
                                    (.192, .3, 95), (.256, .16, 100)]:
        age = np.maximum(times - start, 0)
        envelope += (times >= start) * strength * np.minimum(age / .0006, 1) * np.exp(-age * decay)
    # Ragged arc chatter: randomly interrupted noise, rather than a musical oscillator.
    hold = rng.uniform(.25, 1, (count + 23) // 24).repeat(24)[:count]
    values = np.tanh(noise * envelope * hold * 2.2)
    values *= np.minimum(times / .0005, 1) * np.minimum((duration - times) / .045, 1)
    values *= .88 / np.max(np.abs(values))
    with tempfile.TemporaryDirectory(prefix='cryon-fault-arc-') as temp:
        wav = Path(temp) / 'source.wav'
        with wave.open(str(wav), 'wb') as out:
            out.setnchannels(1); out.setsampwidth(2); out.setframerate(rate)
            out.writeframes((values * 32767).round().astype('<i2').tobytes())
        subprocess.run(['ffmpeg', '-v', 'error', '-n', '-i', str(wav), '-c:a', 'libvorbis', '-q:a', '6', str(target)], check=True)
    print('Created', target.relative_to(ROOT))

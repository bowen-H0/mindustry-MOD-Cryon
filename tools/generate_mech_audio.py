"""Original layered mech SFX. Create missing clips only; preserve existing audio."""
from pathlib import Path
import io
import argparse
import subprocess
import wave
import numpy as np

RATE = 44100
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'sounds'
OUT.mkdir(exist_ok=True)
rng = np.random.default_rng(611081)
clips = []
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--replace-armour', action='store_true', help='Explicitly replace only the blade-armour clip.')
args = parser.parse_args()

def noise(t, low=0, high=7000):
    raw = rng.normal(0, 1, len(t))
    freq = np.fft.rfftfreq(len(t), 1 / RATE)
    shape = np.exp(-(freq / high) ** 4)
    if low:
        shape *= 1 - np.exp(-(freq / low) ** 4)
    value = np.fft.irfft(np.fft.rfft(raw) * shape, n=len(t))
    return value / max(np.std(value), 1e-8)

def grid(seconds): return np.arange(int(seconds * RATE)) / RATE
def chirp(t, start, end, seconds):
    return np.sin(2 * np.pi * (start * t + (end - start) * t * t / (2 * seconds)))

def save(name, value, peak=.72):
    path = OUT / ('cryon-mech-' + name + '.ogg')
    replacing = name == 'blade-armour' and args.replace_armour
    if path.exists() and not replacing:
        print('Preserved', path.name)
        return
    value = np.tanh(value)
    value -= np.mean(value)
    ramp = min(int(.003 * RATE), len(value) // 4)
    value[:ramp] *= np.linspace(0, 1, ramp)
    tail = min(int(.015 * RATE), len(value) // 3)
    value[-tail:] *= np.linspace(1, 0, tail)
    value *= peak / max(np.max(np.abs(value)), 1e-8)
    assert np.isfinite(value).all() and np.max(np.abs(value)) < .9
    wav = io.BytesIO()
    with wave.open(wav, 'wb') as stream:
        stream.setnchannels(1); stream.setsampwidth(2); stream.setframerate(RATE)
        stream.writeframes((value * 32767).astype('<i2').tobytes())
    encoded = subprocess.run(['ffmpeg', '-v', 'error', '-i', 'pipe:0', '-c:a', 'libvorbis',
                              '-q:a', '5', '-f', 'ogg', 'pipe:1'], input=wav.getvalue(),
                             stdout=subprocess.PIPE, check=True).stdout
    with path.open('wb' if replacing else 'xb') as output:
        output.write(encoded)
    clips.append((name, len(value) / RATE, float(np.sqrt(np.mean(value ** 2)))))

# A restrained servo/chest latch before the actual cutting sound.
t = grid(.12)
save('blade-ready', (.22 * chirp(t, 190, 530, .12) + .11 * noise(t, 800, 2600))
     * np.exp(-t * 24) + .13 * noise(t, 1800, 6500) * np.exp(-t * 100), .55)

for name, seconds, low, high, start, end in [
    ('slash-light', .23, 800, 6500, 1150, 420),
    ('slash-reverse', .25, 650, 5800, 850, 340),
    ('slash-heavy', .33, 250, 4300, 650, 160),
]:
    t = grid(seconds)
    envelope = (1 - np.exp(-t * 120)) * np.exp(-t * (14 if name != 'slash-heavy' else 10))
    air = noise(t, low, high)
    motor = chirp(t, start, end, seconds) + .25 * chirp(t, start * 2, end * 2, seconds)
    body = np.sin(2 * np.pi * 90 * t) * np.exp(-t * 22)
    save(name, (.32 * air + .16 * motor) * envelope + .12 * body)

t = grid(.25)
save('blade-hit', .35 * noise(t, 400, 6500) * np.exp(-t * 55)
     + .35 * chirp(t, 155, 60, .25) * np.exp(-t * 24)
     + .10 * np.sin(2 * np.pi * 1450 * t) * np.exp(-t * 35))
t = grid(.12)
metal = sum(g * np.sin(2 * np.pi * f * t) * np.exp(-t * decay)
            for f, g, decay in [(1830, .42, 85), (3697, .28, 110), (6131, .14, 140)])
save('blade-armour', metal + .55 * noise(t, 1800, 10500) * np.exp(-t * 105)
     + .20 * noise(t, 500, 4200) * np.exp(-t * 80)
     + .28 * chirp(t, 165, 65, .12) * np.exp(-t * 95), .88)
t = grid(.31)
ping = sum(g * np.sin(2 * np.pi * f * t) * np.exp(-t * decay)
           for f, g, decay in [(1720, .25, 19), (2863, .16, 26), (4409, .08, 34)])
save('blade-parry', ping + .40 * noise(t, 1800, 10000) * np.exp(-t * 100)
     + .12 * chirp(t, 4200, 1200, .31) * np.exp(-t * 35), .78)

for name, seconds, fundamental, smooth in [('step-light', .14, 130, 3500), ('step-heavy', .21, 78, 2200)]:
    t = grid(seconds)
    save(name, .32 * np.sin(2 * np.pi * fundamental * t) * np.exp(-t * 36)
         + .16 * noise(t, 160, smooth) * np.exp(-t * 48)
         + .10 * np.sin(2 * np.pi * 640 * t) * np.exp(-t * 65), .56)

t = grid(.085)
save('dash-charge', (.27 * chirp(t, 260, 2100, .085) + .12 * noise(t, 1200, 5500))
     * np.sin(np.pi * t / .085) ** .65, .57)
t = grid(.27)
envelope = (1 - np.exp(-t * 180)) * np.exp(-t * 14)
save('dash-burst', (.35 * noise(t, 300, 6000) + .18 * chirp(t, 1250, 120, .27)) * envelope
     + .24 * chirp(t, 120, 40, .27) * np.exp(-t * 24), .74)
t = grid(.20)
save('dash-brake', .35 * noise(t, 550, 4700) * np.exp(-t * 38)
     + .24 * chirp(t, 145, 48, .20) * np.exp(-t * 30)
     + .10 * np.sin(2 * np.pi * 930 * t) * np.exp(-t * 40), .65)

t = grid(.68)
save('collapse', .32 * noise(t, 150, 4200) * np.exp(-t * 9)
     + .32 * chirp(t, 110, 26, .68) * np.exp(-t * 8)
     + .12 * np.sin(2 * np.pi * 570 * t) * np.exp(-t * 14), .76)
# T5's broad, dry steel-on-steel slam. No delayed copies or reverb tail.
t = grid(.20)
metal = sum(g * np.sin(2 * np.pi * f * t) * np.exp(-t * decay)
            for f, g, decay in [(1130, .48, 55), (2431, .34, 70), (4937, .22, 100), (7313, .12, 140)])
save('slaughter-impact', metal + .65 * noise(t, 1600, 12000) * np.exp(-t * 100)
     + .45 * chirp(t, 185, 45, .20) * np.exp(-t * 55)
     + .17 * noise(t, 120, 1400) * np.exp(-t * 70), .88)
for name, seconds, rms in clips:
    print(f'{name}: {seconds:.3f}s, RMS {rms:.3f}')
print(f'Created {len(clips)} original mech sound clips.')

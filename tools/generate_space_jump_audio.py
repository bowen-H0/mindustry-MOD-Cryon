"""Generate original low-frequency fold sounds; requires numpy and ffmpeg."""
from pathlib import Path
import subprocess
import tempfile
import wave
import numpy as np

rate = 44100
out = Path(__file__).resolve().parents[1] / 'sounds'
out.mkdir(exist_ok=True)

def save(name, signal):
    signal = signal / max(1., np.max(np.abs(signal)) / .68)
    with tempfile.TemporaryDirectory() as directory:
        wav = Path(directory) / 'sound.wav'
        with wave.open(str(wav), 'wb') as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(rate)
            f.writeframes((signal * 32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', str(wav),
                        '-c:a', 'libvorbis', '-q:a', '5', str(out / (name + '.ogg'))], check=True)

# Integer periods and zero-crossing boundaries make a seamless, restrained two-second bed.
t = np.arange(rate * 2) / rate
bed = (.24*np.sin(2*np.pi*48*t) + .13*np.sin(2*np.pi*72*t)
       + .065*np.sin(2*np.pi*144*t)) * (.85 + .15*np.cos(2*np.pi*1.5*t))
save('cryon-fold-charge', bed)

# Smooth low-frequency Doppler release, filtered air and a short spatial tail.
t = np.arange(int(rate * 1.15)) / rate
rng = np.random.default_rng(7421)
noise = rng.normal(0, 1, len(t))
spectrum = np.fft.rfft(noise)
freq = np.fft.rfftfreq(len(t), 1/rate)
air = np.fft.irfft(spectrum * np.exp(-(freq/650)**2), n=len(t))
air /= max(np.max(np.abs(air)), 1e-6)
phase = 2*np.pi*(43*t + 105*.1*(1-np.exp(-t/.1)))
attack = 1-np.exp(-t/.006)
envelope = attack * np.exp(-t/0.24) * np.minimum(1, (1.15-t)/.12)
release = (.4*np.sin(phase) + .1*np.sin(phase*1.5) + .22*air) * envelope
for delay, gain in [(0.09, .17), (0.17, .1), (0.28, .06)]:
    shift = int(delay*rate)
    release[shift:] += release[:-shift].copy()*gain
save('cryon-fold-release', release)

"""Original deterministic synthesised sound design for the Eon dreadnought."""
import numpy as np
import wave, subprocess
from pathlib import Path
rate=44100
out=Path('sounds'); out.mkdir(exist_ok=True)
rng=np.random.default_rng(7301)
def noise(n, smooth=24):
    raw=rng.normal(0,1,n)
    return np.convolve(raw,np.ones(smooth)/smooth,mode='same')*np.sqrt(smooth)
def save(name,x,peak=.86):
    x=np.tanh(x)
    x=x/max(np.max(np.abs(x)),1e-6)*peak
    path=Path('/tmp')/(name+'.wav')
    with wave.open(str(path),'wb') as w:
        w.setnchannels(1);w.setsampwidth(2);w.setframerate(rate);w.writeframes((x*32767).astype('<i2').tobytes())
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(path),'-c:a','libvorbis','-q:a','5',str(out/(name+'.ogg'))],check=True)
t=np.arange(rate*4)/rate
engine=(.4*np.sin(2*np.pi*48*t)+.22*np.sin(2*np.pi*72*t)+.12*np.sin(2*np.pi*144*t))*(.85+.15*np.cos(2*np.pi*.25*t))
engine+=.055*np.sin(2*np.pi*312*t+.2*np.sin(2*np.pi*.5*t))
save('cryon-eon-engine',engine, .58)
t=np.arange(int(rate*1.5))/rate;p=t/1.5
charge=(.35*np.sin(2*np.pi*(48*t+44*t*t))+.16*np.sin(2*np.pi*(180*t+140*t*t))+.10*noise(len(t),80))
charge*=np.sin(np.pi*np.minimum(p*1.03,1))**.4*(.25+p)
save('cryon-eon-charge',charge)
for name,duration,decay,freq in [('cannon',1.3,4.8,72),('impact',2.0,3.5,58),('detonation',3.2,2.0,38)]:
    t=np.arange(int(rate*duration))/rate
    phase=2*np.pi*(freq*t+12*(1-np.exp(-t*7)))
    bass=np.sin(phase)*np.exp(-t*decay)
    body=noise(len(t),40 if name=='cannon' else 120)*np.exp(-t*(decay*.65))
    crack=noise(len(t),2)*np.exp(-t*35)
    tail=.14*np.sin(2*np.pi*125*t)*np.exp(-t*1.7)
    x=(bass*.9+body*.45+crack*.45+tail)*np.minimum(t*250,1)
    x*=np.minimum((duration-t)*8,1)
    save('cryon-eon-'+name,x)
print('Generated five original Eon sound assets.')

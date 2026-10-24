"""Render the shipped fold shader in EGL, checking compositing and exporting real shader previews."""
from pathlib import Path
import moderngl
import numpy as np
from PIL import Image, ImageDraw

root=Path(__file__).resolve().parents[1]
ctx=moderngl.create_standalone_context(backend='egl')
source=(root/'shaders/space-fold.frag').read_text()
fragment='#version 330\nout vec4 fragColor;\n'+source.replace('varying vec2','in vec2').replace('texture2D(', 'texture(').replace('gl_FragColor','fragColor')
program=ctx.program(vertex_shader='''#version 330
in vec2 pos;out vec2 v_texCoords;
void main(){v_texCoords=pos*.5+.5;gl_Position=vec4(pos,0,1);}
''',fragment_shader=fragment)
width,height=440,320
y,x=np.mgrid[:height,:width]
rng=np.random.default_rng(27)
terrain=(np.sin(x/31)+np.cos(y/29)+np.sin((x+y)/47))*.012
scene=np.zeros((height,width,4),dtype='f4')
scene[:,:,:3]=np.array([.10,.15,.18])+terrain[:,:,None]
scene[:,:,:3]+=((x%32<1)|(y%32<1))[:,:,None]*.045
scene[:,:,:3]+=rng.uniform(0,.009,(height,width,1))
# Regular map structures make lens displacement and foreground occlusion visible.
for bx,by in [(40,40),(180,40),(300,80),(65,210),(240,200),(365,240)]:
 scene[by:by+38,bx:bx+45,:3]=[.25,.28,.3]
 scene[by+5:by+33,bx+5:bx+40,:3]=[.12,.19,.22]
 scene[by+10:by+13,bx+10:bx+35,:3]=[.24,.49,.52]
scene[:,:,3]=1
texture=ctx.texture((width,height),4,scene.tobytes(),dtype='f4')
texture.filter=(moderngl.LINEAR,moderngl.LINEAR)
texture.repeat_x=texture.repeat_y=False
texture.use(0)
output=ctx.texture((width,height),4,dtype='f4')
fbo=ctx.framebuffer([output]);fbo.use()
program['u_scene']=0;program['u_offset']=(0,0);program['u_texsize']=(width,height)
positions=np.zeros((16,4),dtype='f4');states=np.zeros((16,4),dtype='f4')
positions[:]=[width/2,height/2,64,0.12]
program['u_fold'].write(positions.tobytes())
vao=ctx.simple_vertex_array(program,ctx.buffer(np.array([-1,-1,1,-1,-1,1,1,1],dtype='f4').tobytes()),'pos')
def render(tier,aperture,power,time,phase=0,count=1):
 fbo.use();texture.use(0)
 states[:]=[aperture,power,tier,phase]
 program['u_state'].write(states.tobytes());program['u_count']=count;program['u_time']=time
 vao.render(moderngl.TRIANGLE_STRIP)
 data=np.frombuffer(fbo.read(components=4,dtype='f4'),dtype='f4').reshape(height,width,4).copy()
 assert np.isfinite(data).all(), 'non-finite shader output'
 assert data[:,:,:3].min()>=-.0001 and data[:,:,:3].max()<=1.00001, 'exposure outside display range'
 assert np.allclose(data[:8,:8,:3],scene[:8,:8,:3],atol=.0001), 'background outside effect changed'
 return data
blank=render(1,0,0,1,count=0)
assert np.allclose(blank,scene,atol=.00001), 'empty pass does not preserve source'
board=Image.new('RGB',(width*4,height*5+30*5),(7,12,20));draw=ImageDraw.Draw(board)
names=['NEEDLE / DOPPLER','PRISM / FACET REFRACTION','GYROSCOPE / ORBITAL SHEAR','LUMINOUS SQUARE','LUMINOUS SQUARE / COLLAPSE']
outputs=[]
for row in range(5):
 for col,(aperture,power,time,phase) in enumerate([(.25,.35,1,0),(.9,1,2.5,0),(.95,1,3,1),(.65,.8,3.8,2)]):
  data=render(row+1,aperture,power,time,phase)
  if col==3: outputs.append(data)
  im=Image.fromarray(np.uint8(np.clip(data[::-1,:,:3],0,1)*255))
  top=row*(height+30);board.paste(im,(col*width,top+30))
  draw.text((col*width+8,top+7),f'{row+1}: {names[row]} / '+['CHARGE','ALIGNED / OPEN','DEPARTURE','ARRIVAL'][col],fill=(175,210,235))
for i in range(5):
 for j in range(i):
  assert np.abs(outputs[i]-outputs[j]).sum()>100, 'visual families are indistinguishable'
assembled=render(5,0,1,2,phase=2)
assert np.abs(assembled[height//2,width//2,:3]-scene[height//2,width//2,:3]).sum()>.05, 'arrival cube does not occlude hull'
collapsed=render(5,1,0,4,phase=2)
assert np.allclose(collapsed,scene,atol=.0001), 'collapsed cube does not fully reveal source'
assert np.abs(render(5,.4,1,2,phase=2)-render(5,.9,1,4,phase=2)).sum()>100, 'voxel collapse is static'
assert np.allclose(render(5,.9,1,2,phase=2),render(5,.9,1,4,phase=2)), 'plain square contains animated decoration'
for tier in [4,5]:
 for phase in [0,1]:
  assert np.allclose(render(tier,.9,1,2,phase=phase),scene,atol=.0001), 'square visible at source'
bright_square=render(5,0,1,2,phase=2)
assert bright_square[height//2,width//2,:3].min()>.7, 'square glows only along its border'
eroded=render(5,.68,1,3,phase=2)
change=np.abs(eroded[:,:,:3]-scene[:,:,:3]).max(axis=2)
qx=(x-width/2)/64; qy=(y-height/2)/64
assert (change[(np.abs(qx)>.85)&(np.abs(qx)<.98)&(np.abs(qy)<.85)]>.1).any(), 'square shrank instead of retaining outer fragments'
assert (change[(np.abs(qx)<.6)&(np.abs(qy)<.6)]<.005).any(), 'dissolve did not open interior holes'
render(5,.65,1,2,phase=2,count=16)
# Bright aurora regression: no additive replay or clipped white slabs.
bright=scene.copy();bright[:,:,:3]=np.minimum(1,bright[:,:,:3]+[.48,.60,.64])
texture.write(bright.tobytes())
saved=scene;scene=bright;render(5,.65,1,2,phase=2,count=16);scene=saved;texture.write(scene.tobytes())
report=root/'build/reports';report.mkdir(parents=True,exist_ok=True)
board.save(report/'space-fold-shader-preview.png')
# Use the mod's actual large-aircraft hull to verify occlusion and reveal, not a stand-in icon.
plain=scene.copy()
hull=Image.open(root/'sprites/units/lagrange.png').convert('RGBA')
hull.thumbnail((112,112),Image.Resampling.LANCZOS)
hull_pixels=np.asarray(hull,dtype='f4')[::-1]/255
with_hull=plain.copy()
hh,hw=hull_pixels.shape[:2];left=(width-hw)//2;bottom=(height-hh)//2
alpha=hull_pixels[:,:,3:4]
with_hull[bottom:bottom+hh,left:left+hw,:3]=hull_pixels[:,:,:3]*alpha+with_hull[bottom:bottom+hh,left:left+hw,:3]*(1-alpha)
scene=plain;texture.write(scene.tobytes());hidden_plain=render(5,0,1,2,phase=2)
scene=with_hull;texture.write(scene.tobytes());hidden_hull=render(5,0,1,2,phase=2)
assert np.allclose(hidden_plain,hidden_hull,atol=.0001), 'hull leaks through the assembled digital cube'
revealed=render(5,1,0,4,phase=2)
assert np.allclose(revealed,with_hull,atol=.0001), 'collapse did not reveal the original hull'
# Same structure-extraction shader as virus space, applied to a native RGBA capture.
points_source=(root/'shaders/space-jump-points.frag').read_text()
points_source='#version 330\nout vec4 fragColor;\n'+points_source.replace('varying vec2','in vec2').replace('texture2D(', 'texture(').replace('gl_FragColor','fragColor')
point_program=ctx.program(vertex_shader='''#version 330
in vec2 pos;out vec2 v_texCoords;
void main(){v_texCoords=pos*.5+.5;gl_Position=vec4(pos,0,1);}
''',fragment_shader=points_source)
capture=np.zeros_like(scene)
capture[bottom:bottom+hh,left:left+hw,:3]=hull_pixels[:,:,:3]*alpha
capture[bottom:bottom+hh,left:left+hw,3]=hull_pixels[:,:,3]
point_texture=ctx.texture((width,height),4,capture.tobytes(),dtype='f4');point_texture.filter=(moderngl.LINEAR,moderngl.LINEAR)
point_output=ctx.texture((width,height),4,dtype='f4');point_fbo=ctx.framebuffer([point_output])
point_program['u_texture']=0;point_program['u_pixel']=(1/width,1/height)
point_program['u_origin']=(0,0);point_program['u_worldsize']=(width,height);point_program['u_tint']=(.72,.55,1)
point_vao=ctx.simple_vertex_array(point_program,ctx.buffer(np.array([-1,-1,1,-1,-1,1,1,1],dtype='f4').tobytes()),'pos')
def render_points(progress):
 point_fbo.use();point_texture.use(0);point_program['u_progress']=max(0,min(1,(progress-.55)/.45))
 point_vao.render(moderngl.TRIANGLE_STRIP)
 data=np.frombuffer(point_fbo.read(components=4,dtype='f4'),dtype='f4').reshape(height,width,4).copy()
 data[:,:,:3]+=plain[:,:,:3]*(1-data[:,:,3:4]);data[:,:,3]=1
 return data
source_frames=[render_points(p) for p in [.55,.7,.85,1]]
counts=[int((np.abs(f[:,:,:3]-plain[:,:,:3]).max(axis=2)>.03).sum()) for f in source_frames]
assert counts[0]>counts[1]>counts[2]>counts[3], 'native structure points do not progressively disappear'
assert np.allclose(source_frames[-1],plain,atol=.0001), 'native hull remains visible at teleport'
assert np.allclose(source_frames[0],with_hull,atol=.0001), 'native appearance changed before conversion'
# Parts outside the static body icon are captured too; this was the old mask coverage regression.
extended=capture.copy();extended[130:150,330:370,:]=[.7,.5,.2,1]
point_texture.write(extended.tobytes());weapon_points=render_points(.72)
assert np.abs(weapon_points[128:152,328:372,:3]-plain[128:152,328:372,:3]).sum()>1, 'rotated/extended weapon parts missed by point shader'
point_texture.write(capture.tobytes())
source_board=Image.new('RGB',(width*4,height))
for i,frame in enumerate(source_frames):
 source_board.paste(Image.fromarray(np.uint8(np.clip(frame[::-1,:,:3],0,1)*255)),(i*width,0))
source_board.save(report/'space-fold-source-dots.png')
frames=[]
for i in range(52):
 t=i/51
 if t<.65:
  scene=with_hull;texture.write(scene.tobytes());left_frame=render_points(t/.65)
 else: left_frame=plain
 if t<.65: right_frame=plain
 else:
  scene=with_hull;texture.write(scene.tobytes());p=min(1,(t-.65)/.35)
  right_frame=render(5,p,1 if p<1 else 0,2+t*3,2)
 joined=np.concatenate([left_frame,right_frame],axis=1)
 frame=Image.fromarray(np.uint8(np.clip(joined[::-1,:,:3],0,1)*255))
 caption=ImageDraw.Draw(frame);caption.text((12,10),'SOURCE / DIGITIZE',fill=(170,220,255));caption.text((width+12,10),'TARGET / CUBE COLLAPSE',fill=(170,220,255))
 frames.append(frame)
frames[0].save(report/'space-fold-digital.gif',save_all=True,append_images=frames[1:],duration=67,loop=0)
print('PASS: shipped shader compiles; lower three families retained, digital cube assembly/collapse, complete hull occlusion/reveal, virus-style native structure dots including parts outside body icon, 16 overlaps, bright aurora and untouched background; PNG/GIF exported')

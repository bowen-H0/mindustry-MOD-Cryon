"""Render the actual lens fragment shader using a headless EGL context."""
from pathlib import Path
import moderngl
import numpy as np
from PIL import Image

root=Path(__file__).resolve().parents[1]
ctx=moderngl.create_standalone_context(backend='egl')
fragment=(root/'shaders/gravitational-lensing.frag').read_text()
fragment='#version 330\nout vec4 fragColor;\n'+fragment.replace('varying vec2','in vec2').replace('texture2D(', 'texture(').replace('gl_FragColor','fragColor')
program=ctx.program(vertex_shader='''#version 330
in vec2 pos;
out vec2 v_texCoords;
void main(){v_texCoords=pos*0.5+0.5;gl_Position=vec4(pos,0,1);}
''',fragment_shader=fragment)
size=256
y,x=np.mgrid[:size,:size]/size
scene=np.zeros((size,size,4),dtype='f4')
scene[:,:,:3]=[.045,.065,.1]
# Bright bands model the sampled additive aurora without saturating the source.
band=np.exp(-((y-(.5+.12*np.sin(x*8)))/.18)**2)
scene[:,:,:3]+=band[:,:,None]*np.array([.58,.79,.85],dtype='f4')
scene[:,:,3]=1
tex=ctx.texture((size,size),4,scene.tobytes(),dtype='f4')
tex.filter=(moderngl.LINEAR,moderngl.LINEAR)
tex.use(1)
program['u_scene']=1
program['u_texsize']=(size,size)
program['u_offset']=(0,0)
program['u_time']=17
shields=np.zeros((8,4),dtype='f4')
shields[:]=[128,128,64,0]
program['u_bh'].write(shields.tobytes())
vao=ctx.simple_vertex_array(program,ctx.buffer(np.array([-1,-1,1,-1,-1,1,1,1],dtype='f4').tobytes()),'pos')
source=ctx.framebuffer([tex])
output=ctx.texture((size,size),4,dtype='f4')
fbo=ctx.framebuffer([output])
images=[]
for count in [1,8]:
 program['u_count']=count
 ctx.copy_framebuffer(fbo,source);fbo.use()
 ctx.enable(moderngl.BLEND)
 ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE_MINUS_SRC_ALPHA,moderngl.ONE,moderngl.ONE_MINUS_SRC_ALPHA)
 vao.render(moderngl.TRIANGLE_STRIP)
 pixels=np.frombuffer(fbo.read(components=4,dtype='f4'),dtype='f4').reshape(size,size,4)
 assert np.isfinite(pixels).all()
 assert pixels[:,:,:3].max()<=1.00001, 'rim clips in bright background / overlapping shields'
 assert np.allclose(pixels[128,128,:3],scene[128,128,:3],atol=.0001), 'clear interior doubles scene brightness'
 assert np.allclose(pixels[:16,:16,:3],scene[:16,:16,:3],atol=.0001), 'outside shield changed'
 images.append(np.uint8(np.clip(pixels[:,:,:3],0,1)*255)[::-1])
# Confirm that the test background actually reproduces the inherited additive overexposure.
ctx.copy_framebuffer(fbo,source);fbo.use()
ctx.blend_func=(moderngl.SRC_ALPHA,moderngl.ONE,moderngl.ONE,moderngl.ONE_MINUS_SRC_ALPHA)
vao.render(moderngl.TRIANGLE_STRIP)
bad=np.frombuffer(fbo.read(components=4,dtype='f4'),dtype='f4').reshape(size,size,4)
assert bad[128,128,:3].max()>1, 'test did not reproduce original overexposure'
report=root/'build/reports/reflect-shield-aurora.png'
report.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(np.concatenate(images,axis=1)).save(report)
print('PASS: actual lens shader compiles and renders in EGL; aurora interior unchanged, rim bounded, eight overlapping shields bounded; additive regression reproduced')

#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_center;
uniform float u_extent;
uniform float u_time;
uniform float u_strength;
varying vec2 v_world;
varying vec2 v_texCoords;
varying vec4 v_color;
float hash(vec2 p){return fract(sin(dot(p,vec2(43.27,117.51)))*18427.731);}
void main(){
    vec2 local=v_world-u_center;
    float edge=u_extent-max(abs(local.x),abs(local.y));
    float mask=smoothstep(0.0,1.5,edge);
    float border=1.0-smoothstep(1.5,4.0,edge);
    vec2 grid=abs(mod(local+32.0,64.0)-32.0);
    float lattice=1.0-smoothstep(0.4,1.1,min(grid.x,grid.y));
    vec2 cell=floor((local+u_extent)/210.0);
    vec2 center=(cell+0.5)*210.0-u_extent;
    float seed=hash(cell);
    float phase=fract(u_time*0.003+seed);
    float pulse=smoothstep(0.0,0.08,phase)*(1.0-smoothstep(0.45,1.0,phase));
    float expansion=phase*phase*(3.0-2.0*phase);
    float halfSize=mix(6.0,46.0,expansion);
    vec2 cp=abs(local-center);
    float outline=abs(max(cp.x,cp.y)-halfSize);
    float squares=(1.0-smoothstep(0.65,1.5,outline))*pulse*step(0.75,seed);
    float breath=0.5+0.5*sin(u_time*0.018);
    float alpha=(0.038+lattice*0.035+border*(0.4+breath*0.12)+squares*0.5)*mask*u_strength;
    gl_FragColor=vec4(vec3(0.63,0.72,1.0),alpha)*v_color*texture2D(u_texture,v_texCoords);
}

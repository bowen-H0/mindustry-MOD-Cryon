#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_pixel;
uniform vec2 u_origin;
uniform vec2 u_worldsize;
uniform vec3 u_tint;
uniform float u_progress;
varying vec2 v_texCoords;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){
    vec2 world=u_origin+v_texCoords*u_worldsize;
    float worldPixel=max(u_worldsize.x*u_pixel.x,u_worldsize.y*u_pixel.y);
    float spacing=worldPixel*3.0;
    vec2 pointWorld=(floor(world/spacing)+0.5)*spacing;
    vec2 uv=(pointWorld-u_origin)/u_worldsize;
    vec2 lo=u_pixel*0.5,hi=vec2(1.0)-lo;
    float probe=max(worldPixel*1.5,0.5);
    vec2 dx=vec2(probe/u_worldsize.x,0.0),dy=vec2(0.0,probe/u_worldsize.y);
    vec4 center=texture2D(u_texture,clamp(uv,lo,hi));
    vec4 left=texture2D(u_texture,clamp(uv-dx,lo,hi));
    vec4 right=texture2D(u_texture,clamp(uv+dx,lo,hi));
    vec4 down=texture2D(u_texture,clamp(uv-dy,lo,hi));
    vec4 up=texture2D(u_texture,clamp(uv+dy,lo,hi));
    float contrast=max(max(length(center-left),length(center-right)),max(length(center-down),length(center-up)));
    float coverage=max(center.a,max(max(left.a,right.a),max(down.a,up.a)));
    float structure=smoothstep(0.04,0.30,contrast);
    float point=1.0-smoothstep(0.65,1.15,length(world-pointWorld)/worldPixel);
    float convert=smoothstep(0.0,0.32,u_progress);
    float vanish=clamp((u_progress-0.32)/0.68,0.0,1.0);
    float live=step(vanish,hash(floor(world/spacing)))*(1.0-smoothstep(0.97,1.0,u_progress));
    float alpha=coverage*structure*point*live;
    vec4 native=texture2D(u_texture,v_texCoords);
    gl_FragColor=mix(native,vec4(u_tint*alpha,alpha),convert);
}

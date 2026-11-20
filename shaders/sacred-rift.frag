#define HIGHP
uniform vec2 u_origin;
uniform vec2 u_direction;
uniform vec2 u_view;
uniform float u_time;
uniform float u_open;
uniform float u_front;
varying vec2 v_world;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);}
void main(){
    vec2 side=vec2(-u_direction.y,u_direction.x);
    vec2 delta=v_world-u_origin;
    vec2 p=vec2(dot(delta,side)/104.,dot(delta,u_direction)/48.);
    vec2 view=vec2(dot(u_view,side),dot(u_view,u_direction));
    p/=max(.001,u_open);
    float angle=atan(p.y,p.x);
    float jagged=.032*sin(angle*11.+u_time*2.)+.018*sin(angle*23.-u_time*4.);
    float r=length(p), edge=r-(1.+jagged);
    float rim=exp(-abs(edge)*45.);
    float halo=exp(-abs(edge)*8.);
    vec3 violet=vec3(.52,.22,.96);
    if(u_front>.5){
        // The near lip floats in front of the creature. Only its rim is opaque.
        float nearSide=smoothstep(-.2,.35,p.y);
        float cover=(rim*.9+halo*.11)*nearSide;
        gl_FragColor=vec4(violet*1.1+vec3(.65,.65,.8)*rim,cover*u_open);
        return;
    }
    float inside=1.-smoothstep(-.02,.02,edge);
    vec3 tunnel=vec3(.008,.004,.025);
    // Seven receding shells have independent parallax: a tunnel, not coplanar concentric rings.
    for(int i=7;i>=1;i--){
        float depth=float(i), scale=1./(1.+depth*.34);
        vec2 center=-view*depth*.065+vec2(sin(u_time*.27+depth*.55)*.025,depth*.014);
        vec2 q=(p-center)/scale;
        float radius=length(q), a=atan(q.y,q.x);
        float texture=noise(q*4.+vec2(depth*1.7,u_time*.18));
        float boundary=abs(radius-(1.+.06*sin(a*7.+depth+u_time)));
        float shell=exp(-boundary*26.)*(.12+.08*(8.-depth));
        float swirl=pow(max(0.,sin(a*3.-log(max(.05,radius))*5.-u_time*.9+depth)),12.);
        tunnel+=violet*(shell+swirl*.08)*(.6+texture*.7);
    }
    float rays=pow(max(0.,sin(angle*19.+r*8.-u_time*2.)),22.)*smoothstep(.3,1.,r);
    vec3 glow=violet*(rim*2.2+halo*.24+rays*.3)+vec3(.75,.78,1.)*rim*rim;
    float alpha=max(inside,clamp(halo*.4,0.,.75))*u_open;
    gl_FragColor=vec4(mix(glow,tunnel+glow,inside),alpha);
}

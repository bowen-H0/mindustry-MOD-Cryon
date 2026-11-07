#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_center;
uniform float u_radius;
uniform float u_time;
uniform float u_intensity;
uniform float u_seed;
varying vec2 v_world;
varying vec2 v_texCoords;
varying vec4 v_color;
float hash(vec2 p){ return fract(sin(dot(p,vec2(37.31,91.73)))*15437.273); }
float noise(vec2 p){
    vec2 cell=floor(p), f=fract(p);
    f=f*f*(3.0-2.0*f);
    return mix(mix(hash(cell),hash(cell+vec2(1.0,0.0)),f.x),
        mix(hash(cell+vec2(0.0,1.0)),hash(cell+vec2(1.0,1.0)),f.x),f.y);
}
void main(){
    vec2 relative=v_world-u_center;
    float radial=length(relative)/u_radius;
    vec2 flow=v_world/95.0+vec2(u_time*0.0025,-u_time*0.0015)+u_seed*0.17;
    float broad=noise(flow);
    float detail=noise(flow*2.6+vec2(-u_time*0.003, u_time*0.002));
    float cloud=broad*0.65+detail*0.35;
    float edge=1.0-smoothstep(0.70+cloud*0.12,1.0,radial);
    float wisps=smoothstep(0.35,0.75,cloud);
    float ripple=pow(0.5+0.5*sin(radial*30.0-u_time*0.025+cloud*9.0),8.0);
    vec3 color=mix(vec3(0.12,0.48,0.26),vec3(0.35,0.94,0.52),wisps);
    float alpha=(0.045+wisps*0.22+ripple*0.035)*edge*u_intensity;
    gl_FragColor=vec4(color,alpha)*v_color*texture2D(u_texture,v_texCoords);
}

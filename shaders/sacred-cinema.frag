#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform vec2 u_focus;
uniform vec2 u_motion;
uniform float u_strength;
uniform float u_time;
uniform float u_impact;
varying vec2 v_texCoords;
vec3 sampleAt(vec2 uv){return texture2D(u_texture,clamp(uv,vec2(.001),vec2(.999))).rgb;}
void main(){
    vec2 uv=v_texCoords,delta=uv-u_focus;
    float aspect=u_resolution.x/u_resolution.y;
    float radius=length(delta*vec2(aspect,1.));
    float wave=sin(radius*44.-u_time*5.)*exp(-radius*3.5)*u_strength;
    vec2 bend=normalize(delta+vec2(.0001))*wave*.0035;
    vec2 lens=uv+bend;
    float peripheral=smoothstep(.12,.65,radius)*u_strength;
    vec2 radial=delta*(.014*u_strength+.045*u_impact);
    vec3 color=sampleAt(lens)*.4;
    color+=sampleAt(lens-radial*.33)*.2;
    color+=sampleAt(lens-radial*.66)*.2;
    color+=sampleAt(lens-radial)*.2;
    // Focus-plane approximation for the 2D scene, leaving the breach and emerging legs sharp.
    vec2 blur=vec2(2.4)/u_resolution*peripheral;
    vec3 defocus=(sampleAt(lens+vec2(blur.x,blur.y))+sampleAt(lens-vec2(blur.x,blur.y))
        +sampleAt(lens+vec2(blur.x,-blur.y))+sampleAt(lens+vec2(-blur.x,blur.y)))*.25;
    color=mix(color,defocus,.32*peripheral);
    // Actual previous-camera displacement drives directional motion blur, reset on camera cuts.
    color=mix(color,(color+sampleAt(lens-u_motion*.33)+sampleAt(lens-u_motion*.66))*.3333,.36*u_strength);
    vec2 chroma=delta*(.0028*u_strength+.004*u_impact);
    color.r=mix(color.r,sampleAt(lens+chroma).r,.75*u_strength);
    color.b=mix(color.b,sampleAt(lens-chroma).b,.75*u_strength);
    // Soft highlight bloom is independent of the player's global bloom toggle.
    vec2 spread=vec2(8.)/u_resolution;
    vec3 halo=max(vec3(0.),sampleAt(lens+vec2(spread.x,0.))-.62)
        +max(vec3(0.),sampleAt(lens-vec2(spread.x,0.))-.62)
        +max(vec3(0.),sampleAt(lens+vec2(0.,spread.y))-.62)
        +max(vec3(0.),sampleAt(lens-vec2(0.,spread.y))-.62);
    color+=halo*.32*u_strength;
    float gray=dot(color,vec3(.2126,.7152,.0722));
    color=mix(color,vec3(gray),.2*u_strength);
    color*=mix(vec3(1.),vec3(.88,.81,1.13),u_strength);
    color+=vec3(.035,.018,.065)*u_impact;
    vec2 centered=uv-.5;
    float vignette=smoothstep(.15,.72,length(centered*vec2(1.,.85)));
    color*=1.-vignette*.72*u_strength;
    // Short exposure lift at emergence, with no alternating full-screen strobe.
    color+=vec3(.07,.04,.10)*u_impact;
    float bars=smoothstep(.0,.025,uv.y)*smoothstep(.0,.025,1.-uv.y);
    color*=mix(1.,bars,u_strength*.75);
    gl_FragColor=vec4(color,1.);
}

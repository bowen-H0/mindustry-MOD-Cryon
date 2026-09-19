#define HIGHP

#define INNER_CLEAR 0.55
#define OUTER_REACH 1.45
#define LENS_STRENGTH 0.13
#define DISPERSION 0.18
#define RIM_WIDTH 0.028
#define RIM_GLOW 0.55
#define EDGE_DARKEN 0.18
#define ARC_GLOW 0.30
#define RIM_COLOR vec3(0.55, 0.80, 1.0)
#define ARC_COLOR vec3(0.85, 0.95, 1.0)

uniform sampler2D u_texture;
uniform sampler2D u_scene;
uniform vec2 u_texsize;
uniform vec2 u_offset;
uniform float u_time;
uniform int u_count;
uniform vec4 u_bh[8];
varying vec2 v_texCoords;

void main(){
    vec2 T = v_texCoords.xy;
    vec2 p = u_offset + T * u_texsize;

    vec2 sampR = p;
    vec2 sampG = p;
    vec2 sampB = p;
    float rim = 0.0;
    float arc = 0.0;
    float darken = 0.0;
    float alpha = 0.0;

    for(int i = 0; i < 8; i++){
        if(i >= u_count) break;

        vec2 c = u_bh[i].xy;
        float R = max(u_bh[i].z, 0.001);
        float health = 1.0 - u_bh[i].w;

        vec2 d = p - c;
        float r = length(d);
        float t = r / R;
        if(t >= OUTER_REACH) continue;

        alpha = max(alpha, 1.0 - smoothstep(OUTER_REACH * 0.9, OUTER_REACH, t));

        if(r < 0.001) continue;

        vec2 dir = d / r;
        float ang = atan(d.y, d.x);

        float rise = smoothstep(INNER_CLEAR, 1.0, t);
        float fall = 1.0 - smoothstep(1.0, OUTER_REACH, t);
        float bump = rise * fall;

        float wob = 1.0 + sin(ang * 4.0 + u_time * 0.8) * 0.06;
        float disp = R * LENS_STRENGTH * bump * wob;

        sampR -= dir * disp * (1.0 + DISPERSION);
        sampG -= dir * disp;
        sampB -= dir * disp * (1.0 - DISPERSION);

        float x = (t - 1.0) / RIM_WIDTH;
        float line = exp(-min(x * x, 30.0));
        rim += line * (0.4 + 0.6 * health);

        float sweep = pow(0.5 + 0.5 * sin(ang * 2.0 - u_time * 0.9), 4.0);
        arc += line * sweep * health;

        darken = max(darken, smoothstep(0.7, 1.0, t) * (1.0 - smoothstep(1.0, 1.1, t)));
    }

    if(alpha <= 0.001){
        gl_FragColor = vec4(0.0);
        return;
    }

    vec2 uvR = clamp((sampR - u_offset) / u_texsize, 0.0, 1.0);
    vec2 uvG = clamp((sampG - u_offset) / u_texsize, 0.0, 1.0);
    vec2 uvB = clamp((sampB - u_offset) / u_texsize, 0.0, 1.0);

    vec3 col;
    col.r = texture2D(u_scene, uvR).r;
    col.g = texture2D(u_scene, uvG).g;
    col.b = texture2D(u_scene, uvB).b;

    col *= 1.0 - EDGE_DARKEN * darken;
    col += RIM_COLOR * rim * RIM_GLOW;
    col += ARC_COLOR * arc * ARC_GLOW;

    gl_FragColor = vec4(col, alpha);
}
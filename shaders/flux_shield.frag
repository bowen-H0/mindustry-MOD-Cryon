#define HIGHP
#define ALPHA 0.18
#define step 2.0

#define LATTICE_SCALE 14.0
#define LINE_WIDTH 0.10
#define PULSE_SPEED 1.2
#define PULSE_AMOUNT 0.06
#define LINE_GLOW 0.25

uniform sampler2D u_texture;
uniform vec2 u_texsize;
uniform vec2 u_invsize;
uniform float u_time;
uniform float u_dp;
uniform vec2 u_offset;
varying vec2 v_texCoords;

float hash21(vec2 p){
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float latticeLines(vec2 coords, float scale){
    vec2 p = coords / scale;
    float a = abs(fract(p.x) - 0.5);
    float b = abs(fract(p.x * 0.5 + p.y * 0.8660254) - 0.5);
    float c = abs(fract(-p.x * 0.5 + p.y * 0.8660254) - 0.5);
    float d = min(min(a, b), c);
    return 1.0 - smoothstep(0.0, LINE_WIDTH, d);
}

void main(){
    vec2 T = v_texCoords.xy;
    vec2 coords = (T * u_texsize) + u_offset;

    vec4 color = texture2D(u_texture, T);
    vec2 v = u_invsize;

    vec4 maxed = max(max(max(
        texture2D(u_texture, T + vec2(0, step) * v),
        texture2D(u_texture, T + vec2(0, -step) * v)),
        texture2D(u_texture, T + vec2(step, 0) * v)),
        texture2D(u_texture, T + vec2(-step, 0) * v));

    if(texture2D(u_texture, T).a < 0.9 && maxed.a > 0.9){
        gl_FragColor = vec4(maxed.rgb, maxed.a * 100.0);
    } else {
        if(color.a > 0.0){
            vec2 cellCoords = coords / u_dp;

            float lines = latticeLines(cellCoords, LATTICE_SCALE);

            vec2 cellId = floor(cellCoords / LATTICE_SCALE);
            float phase = hash21(cellId) * 6.28318;
            float cellPulse = sin(u_time * PULSE_SPEED + phase) * 0.5 + 0.5;

            float globalPulse = sin(u_time * PULSE_SPEED * 0.5) * PULSE_AMOUNT;

            float brighten = 1.0 + globalPulse + lines * LINE_GLOW * (0.7 + cellPulse * 0.3);
            color.rgb *= brighten;
            color.a = ALPHA + lines * 0.05 + globalPulse * 0.02;
        }
        gl_FragColor = color;
    }
}
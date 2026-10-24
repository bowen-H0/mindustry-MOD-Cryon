#define HIGHP
uniform sampler2D u_scene;
uniform vec2 u_offset;
uniform vec2 u_texsize;
uniform float u_time;
uniform int u_count;
uniform vec4 u_fold[16];
uniform vec4 u_state[16];
varying vec2 v_texCoords;

float hash(vec2 p){
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}
float noise(vec2 p){
    vec2 a = floor(p), b = fract(p);
    b = b * b * (3.0 - 2.0 * b);
    return mix(mix(hash(a), hash(a + vec2(1.0, 0.0)), b.x),
        mix(hash(a + vec2(0.0, 1.0)), hash(a + vec2(1.0, 1.0)), b.x), b.y);
}
float fbm(vec2 p){
    float a = 0.0;
    a += noise(p) * 0.56;
    p = mat2(1.6, 1.2, -1.2, 1.6) * p + 4.7;
    a += noise(p) * 0.28;
    p = mat2(1.6, 1.2, -1.2, 1.6) * p + 2.3;
    return a + noise(p) * 0.14;
}
float segment(vec2 p, vec2 a, vec2 b){
    vec2 d = b - a;
    return length(p - a - d * clamp(dot(p - a, d) / dot(d, d), 0.0, 1.0));
}
vec3 scene(vec2 world){
    return texture2D(u_scene, clamp((world - u_offset) / u_texsize, 0.0, 1.0)).rgb;
}
vec3 boundedGlow(vec3 base, vec3 light){
    return base + (vec3(1.0) - clamp(base, 0.0, 1.0)) * (vec3(1.0) - exp(-light));
}
// A flat luminous square: no digits, grid, projected sides or voxel decoration.
vec3 digitalFold(vec3 base, vec2 q, vec4 state, float clock){
    float phase = state.w, p = clamp(state.x, 0.0, 1.0);
    if(phase < 1.5) return base; // Large hulls vanish at the source; the square exists only at arrival.
    vec3 tint = state.z > 4.5 ? vec3(0.72, 0.55, 1.0) : vec3(0.30, 0.84, 1.0);
    float collapse = phase > 1.5 ? clamp((p - 0.38) / 0.62, 0.0, 1.0) : (phase > 0.5 ? p : 0.0);
    float boundary = max(abs(q.x), abs(q.y)) - 1.0;
    // A fixed spatial erosion mask opens holes across the original surface; no scaling.
    float grain = fbm(q * 9.0 + vec2(3.1, 7.4)) * 0.72 + noise(q * 39.0) * 0.28;
    float threshold = mix(-0.06, 1.06, collapse);
    float remaining = smoothstep(threshold - 0.018, threshold + 0.018, grain);
    float dissolveEdge = (1.0 - smoothstep(0.0, 0.045, abs(grain - threshold)))
        * step(0.001, collapse) * (1.0 - step(0.999, collapse));
    float mask = 1.0 - smoothstep(0.0, 0.012, boundary);
    float visible = (1.0 - smoothstep(0.85, 1.0, collapse));
    float opacity = (phase < 0.5 ? smoothstep(0.82, 0.99, p) : visible) * remaining;
    vec3 surface = mix(tint, vec3(0.94, 0.98, 1.0), 0.72);
    vec3 result = mix(base, surface, mask * opacity);
    float edge = exp(-abs(boundary) * 60.0);
    float halo = exp(-max(boundary, 0.0) * 8.0) * (1.0 - smoothstep(0.0, 0.8, max(boundary, 0.0)));
    float power = (phase < 0.5 ? p : visible) * state.y;
    return boundedGlow(result, tint * ((mask * 0.85 + edge * 0.55 + halo * 0.55) * remaining + dissolveEdge * mask * 1.3) * power);
}

void main(){
    vec2 world = u_offset + v_texCoords * u_texsize;
    vec3 result = texture2D(u_scene, v_texCoords).rgb;
    for(int i = 0; i < 16; i++){
        if(i >= u_count) break;
        vec4 fold = u_fold[i], state = u_state[i];
        float radius = max(1.0, fold.z);
        vec2 d = (world - fold.xy) / radius;
        if(dot(d,d) > 12.0) continue;
        float c = cos(fold.w), s = sin(fold.w);
        vec2 q = vec2(c * d.x + s * d.y, -s * d.x + c * d.y);
        float open = clamp(state.x, 0.0, 1.0);
        float power = clamp(state.y, 0.0, 1.0);
        float tier = state.z;
        float clock = u_time + float(i) * 0.37;
        if(tier >= 3.5){
            result = digitalFold(result, d, state, clock);
            continue;
        }
        vec3 tint = vec3(0.24, 0.72, 1.0);
        float boundary = 0.0, interior = 0.0, influence = 0.0, fracture = 0.0;
        vec2 bend = vec2(0.0);
        if(tier < 1.5){
            // Needle: a pinched lens and a horizontal Doppler slit, no portal disc.
            float slit = length(q * vec2(0.38, 4.0));
            boundary = slit - (0.12 + open * 0.35);
            influence = exp(-abs(boundary) * 9.0) * power;
            interior = exp(-dot(q * vec2(1.0, 5.0), q * vec2(1.0, 5.0)) * 6.0) * open;
            bend = vec2(q.x * 0.12, -q.y * 0.2) * influence;
        }else if(tier < 2.5){
            // Prism: three planar faces refract the map in different directions.
            tint = vec3(0.15, 1.0, 0.65);
            float tri = max(q.y, max(dot(q, vec2(0.866, -0.5)), dot(q, vec2(-0.866, -0.5))));
            boundary = tri - (0.2 + open * 0.43);
            influence = exp(-abs(boundary) * 8.0) * power;
            interior = (1.0 - smoothstep(-0.03, 0.03, boundary)) * open * 0.3;
            vec2 facet = q.y > 0.0 ? vec2(0.0, 1.0) : vec2(sign(q.x) * 0.866, -0.5);
            bend = facet * 0.16 * influence;
        }else if(tier < 3.5){
            // Gyroscope: a spherical gravitational caustic and differential orbital shear.
            tint = vec3(0.62, 0.35, 1.0);
            float r = length(q), angle = atan(q.y, q.x);
            boundary = r - (0.28 + open * 0.48);
            influence = exp(-abs(boundary) * 7.0) * power;
            interior = (1.0 - smoothstep(-0.08, 0.08, boundary)) * open * 0.5;
            bend = vec2(-q.y, q.x) * 0.16 * influence + q * 0.14 * influence;
            fracture = pow(max(0.0, sin(angle * 3.0 + clock * 2.0 + r * 14.0)), 12.0) * influence * 0.25;
        }

        if(influence < 0.001 && interior < 0.001 && fracture < 0.001) continue;
        vec2 bentWorld = fold.xy + radius * (d + vec2(c * bend.x - s * bend.y, s * bend.x + c * bend.y));
        float chroma = influence * radius * (tier >= 4.0 ? 0.018 : 0.008);
        vec2 normal = vec2(c, s) * chroma;
        vec3 refracted = vec3(scene(bentWorld + normal).r, scene(bentWorld).g, scene(bentWorld - normal).b);
        result = mix(result, refracted, clamp(influence * 0.85, 0.0, 1.0));
        result *= 1.0 - interior * 0.25;
        float rim = exp(-abs(boundary) * (tier >= 4.0 ? 95.0 : 65.0)) * power;
        float turbulent = 0.65 + 0.35 * noise(q * 25.0 + clock * 2.0);
        float atmosphere = tier >= 4.0 ? 0.48 : 0.23;
        vec3 light = tint * influence * atmosphere + mix(tint, vec3(0.88, 0.95, 1.0), rim) * rim * turbulent * 1.9;
        light += tint * fracture * 1.7;
        result = boundedGlow(result, light);
    }
    gl_FragColor = vec4(result, 1.0);
}

uniform sampler2D u_texture;
uniform vec4 u_region;
uniform vec2 u_size;
uniform vec2 u_impact;
uniform float u_distanceScale;
uniform float u_intensity;
uniform float u_time;
varying vec2 v_texCoords;
varying vec4 v_color;
void main(){
    vec2 uv = (v_texCoords - u_region.xy) / u_region.zw;
    vec2 p = (uv - 0.5) * u_size * vec2(1.0, -1.0);
    float sdf = (texture2D(u_texture, v_texCoords).r * 2.0 - 1.0) * u_distanceScale;
    // The actual shield boundary is exactly eight world units outside the ship alpha mask.
    float d = sdf + 8.0;
    float fill = smoothstep(-0.65, 0.65, d);
    float rim = exp(-abs(d) / 1.15);
    float glow = exp(-abs(d) / 3.0);
    float facing = dot(p / max(length(p), 0.01), u_impact);
    float sector = smoothstep(0.05, 0.72, facing);
    float depth = exp(-max(d, 0.0) / 19.0);
    float wave = 0.5 + 0.5 * sin(p.y * 0.14 + p.x * 0.06 - u_time * 0.075);
    float ripple = pow(0.5 + 0.5 * sin(length(p) * 0.20 - u_time * 0.12), 8.0);
    float interior = fill * (0.16 + 0.20 * depth + 0.10 * wave + 0.12 * ripple);
    vec3 violet = mix(vec3(0.30, 0.16, 0.68), vec3(0.64, 0.48, 1.0), depth);
    vec3 color = mix(violet, vec3(0.76, 0.90, 1.0), clamp(rim + wave * 0.20, 0.0, 1.0));
    float alpha = (interior + rim * 0.72 + glow * 0.20) * sector * u_intensity;
    gl_FragColor = vec4(color * (1.0 + rim * 0.5), alpha) * v_color;
}

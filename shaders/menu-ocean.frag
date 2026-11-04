#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_resolution;
uniform float u_time;
varying vec2 v_texCoords;

void main(){
    vec2 p = v_texCoords;
    vec2 current = vec2(
        sin(p.y * 24.0 + u_time * 0.31) + sin((p.x + p.y) * 15.0 - u_time * 0.23),
        cos(p.x * 21.0 - u_time * 0.27) + sin(p.y * 18.0 + u_time * 0.19));
    vec2 uv = clamp(p + current * 1.45 / u_resolution, vec2(0.002), vec2(0.998));
    vec3 scene = texture2D(u_texture, uv).rgb * vec3(0.76, 0.94, 1.0);

    // Slowly refracting caustic lines, rather than a bright repeating grid.
    float a = sin(p.x * 39.0 + p.y * 21.0 + u_time * 0.35)
            + sin(p.x * 18.0 - p.y * 33.0 - u_time * 0.27);
    float b = sin(p.x * 26.0 + p.y * 35.0 - u_time * 0.23);
    float caustic = pow(max(0.0, 1.0 - abs(a + b) * 0.65), 15.0);
    float shaft = pow(max(0.0, sin((p.x + p.y * 0.35) * 21.0 + u_time * 0.08)), 12.0);
    shaft *= smoothstep(0.05, 0.95, p.y);

    float fog = 0.12 + 0.10 * (1.0 - p.y);
    vec3 color = mix(scene, vec3(0.025, 0.12, 0.17), fog);
    color += vec3(0.20, 0.40, 0.40) * caustic * 0.12;
    color += vec3(0.16, 0.32, 0.34) * shaft * 0.12;
    float edge = smoothstep(0.25, 0.95, distance(p, vec2(0.60, 0.53)));
    color *= 1.0 - edge * 0.22;
    gl_FragColor = vec4(color, 1.0);
}

#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_pixel;
uniform vec2 u_origin;
uniform vec2 u_worldsize;
uniform vec3 u_tint;
varying vec2 v_texCoords;

void main(){
    vec2 world = u_origin + v_texCoords * u_worldsize;
    float worldPixel = max(u_worldsize.x * u_pixel.x, u_worldsize.y * u_pixel.y);
    float spacing = worldPixel * 3.0;
    vec2 pointWorld = (floor(world / spacing) + 0.5) * spacing;
    vec2 uv = (pointWorld - u_origin) / u_worldsize;
    // Never wrap sprite samples across the edges of the viewport.
    vec2 lo = u_pixel * 0.5;
    vec2 hi = vec2(1.0) - lo;
    float probe = max(worldPixel * 1.5, 0.5);
    vec2 dx = vec2(probe / u_worldsize.x, 0.0);
    vec2 dy = vec2(0.0, probe / u_worldsize.y);
    vec4 center = texture2D(u_texture, clamp(uv, lo, hi));
    vec4 left = texture2D(u_texture, clamp(uv - dx, lo, hi));
    vec4 right = texture2D(u_texture, clamp(uv + dx, lo, hi));
    vec4 down = texture2D(u_texture, clamp(uv - dy, lo, hi));
    vec4 up = texture2D(u_texture, clamp(uv + dy, lo, hi));
    // Include alpha edges so dark sprite parts still have a visible outline.
    float contrast = max(max(length(center - left), length(center - right)),
                         max(length(center - down), length(center - up)));
    float coverage = max(center.a, max(max(left.a, right.a), max(down.a, up.a)));
    float structure = smoothstep(0.04, 0.30, contrast);
    float point = 1.0 - smoothstep(0.65, 1.15, length(world - pointWorld) / worldPixel);
    float alpha = coverage * structure * point;
    // Transparent, premultiplied output; no red bias or background lattice.
    gl_FragColor = vec4(u_tint * alpha, alpha);
}

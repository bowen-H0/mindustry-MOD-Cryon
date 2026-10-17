#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_pixel;
uniform vec2 u_origin;
uniform vec2 u_worldsize;
uniform vec4 u_bounds;
varying vec2 v_texCoords;

void main(){
    vec2 world = u_origin + v_texCoords * u_worldsize;
    if(world.x < u_bounds.x || world.y < u_bounds.y || world.x > u_bounds.z || world.y > u_bounds.w){
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    // Raster sprites have no 3D mesh vertices. Extract their visible structure into
    // isolated screen-space points; never carry the original filled color through.
    // Anchor points to world coordinates so camera movement does not shimmer the lattice.
    float worldPixel = max(u_worldsize.x * u_pixel.x, u_worldsize.y * u_pixel.y);
    // Keep density in framebuffer pixels, including when zoomed in. A fixed
    // world-space minimum leaves increasingly large holes between tiny dots.
    float spacing = worldPixel * 3.0;
    vec2 pointWorld = (floor(world / spacing) + 0.5) * spacing;
    vec2 sampleUV = (pointWorld - u_origin) / u_worldsize;
    // Sprite edges are filtered over a world-space footprint. At close zoom,
    // a pixel-only probe becomes too small to detect their color difference.
    float probe = max(worldPixel * 1.5, 0.5);
    vec2 dx = vec2(probe / u_worldsize.x, 0.0);
    vec2 dy = vec2(0.0, probe / u_worldsize.y);
    vec3 center = texture2D(u_texture, sampleUV).rgb;
    vec3 left = texture2D(u_texture, sampleUV - dx).rgb;
    vec3 right = texture2D(u_texture, sampleUV + dx).rgb;
    vec3 down = texture2D(u_texture, sampleUV - dy).rgb;
    vec3 up = texture2D(u_texture, sampleUV + dy).rgb;
    float contrast = max(max(length(center - left), length(center - right)),
                         max(length(center - down), length(center - up)));
    float structure = smoothstep(0.07, 0.35, contrast);
    float point = 1.0 - smoothstep(0.65, 1.15, length(world - pointWorld) / worldPixel);
    vec3 pointColor = vec3(188.0, 84.0, 82.0) / 255.0; // #bc5452
    // Tile-lattice vertices are also isolated dots, with no connecting edges or faces.
    vec2 cell = mod(world + 4.0 + 32.0, 64.0) - 32.0;
    float gridPoint = 1.0 - smoothstep(worldPixel * 0.55, worldPixel * 1.05, length(cell));
    // Static, muted red: no time-based brightness pulse or white highlight remix.
    // Keep flat terrain patterns subordinate to the separate native combat markers.
    float detailStrength = smoothstep(0.10, 0.50, contrast);
    vec3 color = pointColor * structure * point * mix(0.38, 0.85, detailStrength);
    color = max(color, pointColor * 0.14 * gridPoint);
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}

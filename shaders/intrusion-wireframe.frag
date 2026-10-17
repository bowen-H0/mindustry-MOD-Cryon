#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_pixel;
uniform vec2 u_origin;
uniform vec2 u_worldsize;
uniform vec4 u_bounds;
uniform float u_time;
varying vec2 v_texCoords;

void main(){
    vec2 world = u_origin + v_texCoords * u_worldsize;
    if(world.x < u_bounds.x || world.y < u_bounds.y || world.x > u_bounds.z || world.y > u_bounds.w){
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    // Sample continuously: preserve sprite contours instead of a sparse dot lattice.
    float worldPixel = max(u_worldsize.x * u_pixel.x, u_worldsize.y * u_pixel.y);
    float probe = max(worldPixel * 1.5, 0.5);
    vec2 dx = vec2(probe / u_worldsize.x, 0.0);
    vec2 dy = vec2(0.0, probe / u_worldsize.y);
    vec3 center = texture2D(u_texture, v_texCoords).rgb;
    vec3 left = texture2D(u_texture, v_texCoords - dx).rgb;
    vec3 right = texture2D(u_texture, v_texCoords + dx).rgb;
    vec3 down = texture2D(u_texture, v_texCoords - dy).rgb;
    vec3 up = texture2D(u_texture, v_texCoords + dy).rgb;
    float contrast = max(max(length(center - left), length(center - right)),
                         max(length(center - down), length(center - up)));
    float structure = smoothstep(0.05, 0.32, contrast);
    float luminance = dot(center, vec3(0.299, 0.587, 0.114));

    // Brighter burgundy background and continuous red phosphor contours.
    vec3 background = vec3(0.105, 0.038, 0.052);
    vec3 phosphor = vec3(0.88, 0.36, 0.34);
    vec3 color = background + phosphor * (structure * 0.65 + luminance * 0.18);

    // Horizontal display scanlines and a broad, slow rolling refresh band.
    // Work in framebuffer pixels so zoom does not turn the lines into a dot grid.
    float screenY = v_texCoords.y / u_pixel.y;
    float scanline = 0.90 + 0.10 * sin(screenY * 1.5707963);
    float sweep = 0.5 + 0.5 * cos(v_texCoords.y * 6.2831853 - u_time * 0.9);
    float refresh = pow(sweep, 12.0);
    color = color * scanline + phosphor * refresh * 0.075;
    gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}

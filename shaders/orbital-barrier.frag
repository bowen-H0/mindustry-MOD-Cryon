#define HIGHP
uniform sampler2D u_texture;
uniform vec2 u_start;
uniform vec2 u_end;
uniform float u_width;
uniform float u_time;
uniform float u_impact;
uniform float u_strength;
uniform float u_phase;
varying vec2 v_world;
varying vec2 v_texCoords;
varying vec4 v_color;

// Independent opposing wavefronts and staggered shield cells. World coordinates keep
// the pattern fixed to the battlefield when the camera pans or changes zoom.
void main(){
    vec2 axis = u_end - u_start;
    float span = max(length(axis), 0.01);
    vec2 direction = axis / span;
    vec2 local = v_world - u_start;
    float along = dot(local, direction);
    float across = dot(local, vec2(-direction.y, direction.x));
    float distanceToSegment = length(vec2(max(max(-along, along-span), 0.0), across));
    float radius = u_width * 0.5;
    float mask = 1.0 - smoothstep(radius-1.2, radius, distanceToSegment);
    float edge = smoothstep(radius-3.0, radius-1.4, distanceToSegment);
    float innerEdge = 1.0-smoothstep(0.7,1.8,abs(distanceToSegment-(radius-7.0)));
    float depth = 1.0-smoothstep(0.0,radius,abs(across));
    vec2 grid = vec2(along / 18.0, across / 12.0);
    grid.x += mod(floor(grid.y), 2.0) * 0.5;
    vec2 cell = abs(fract(grid)-0.5);
    float seams = smoothstep(0.40, 0.48, max(cell.x, cell.y));
    float frontA = pow(0.5+0.5*cos(along*0.085-u_time*0.045+across*0.07), 14.0);
    float frontB = pow(0.5+0.5*cos((span-along)*0.085-u_time*0.045-across*0.07), 14.0);
    float interference = frontA * frontB;
    float charge = clamp(u_strength, 0.0, 1.0);
    vec3 gold = mix(vec3(0.984,0.827,0.404), vec3(1.0,0.90,0.65), u_phase*0.3);
    gold *= 0.78+depth*0.22;
    float alpha = 0.14+depth*0.07+seams*0.12+max(frontA,frontB)*0.14+interference*0.13+edge*0.42+innerEdge*0.18;
    alpha += clamp(u_impact,0.0,1.0)*(0.13+frontA*0.12);
    alpha *= 0.45+charge*0.55;
    gl_FragColor = vec4(gold, min(alpha,0.76)*mask) * v_color * texture2D(u_texture,v_texCoords);
}

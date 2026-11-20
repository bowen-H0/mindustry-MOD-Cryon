uniform sampler2D u_texture;
uniform vec2 u_pixel;
uniform vec2 u_plane;
uniform vec2 u_direction;
uniform float u_clip;
uniform float u_light;
varying vec4 v_color;
varying vec2 v_texCoords;
varying vec4 v_mix;
varying vec2 v_world;
float lum(vec3 c){return dot(c,vec3(.25,.5,.25));}
void main(){
    if(u_clip>.5 && dot(v_world-u_plane,u_direction)<0.0)discard;
    vec4 tex=texture2D(u_texture,v_texCoords);
    float a=texture2D(u_texture,v_texCoords+u_pixel*vec2(1.,0.)).a;
    float b=texture2D(u_texture,v_texCoords+u_pixel*vec2(-1.,0.)).a;
    float c=texture2D(u_texture,v_texCoords+u_pixel*vec2(0.,1.)).a;
    float d=texture2D(u_texture,v_texCoords+u_pixel*vec2(0.,-1.)).a;
    float gx=lum(texture2D(u_texture,v_texCoords+u_pixel*vec2(1.,0.)).rgb)-lum(texture2D(u_texture,v_texCoords-u_pixel*vec2(1.,0.)).rgb);
    float gy=lum(texture2D(u_texture,v_texCoords+u_pixel*vec2(0.,1.)).rgb)-lum(texture2D(u_texture,v_texCoords-u_pixel*vec2(0.,1.)).rgb);
    vec3 normal=normalize(vec3(-gx*2.0,-gy*2.0,1.0));
    float shade=.68+.38*max(0.,dot(normal,normalize(vec3(-.6,.7,1.))));
    vec3 rgb=mix(tex.rgb,v_mix.rgb,v_mix.a)*v_color.rgb*shade;
    rgb+=vec3(.42,.22,.67)*max(0.,(b-a)+(d-c))*.32*u_light;
    gl_FragColor=vec4(rgb,tex.a*v_color.a);
}

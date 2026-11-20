attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;
attribute vec4 a_mix_color;
uniform mat4 u_projTrans;
varying vec2 v_world;
void main(){v_world=a_position.xy;gl_Position=u_projTrans*a_position;}

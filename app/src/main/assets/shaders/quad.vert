#version 300 es
// Fullscreen quad. v_uv has v = 0 at the TOP of the image for offscreen passes; u_flipY = 1
// flips it for targets whose row 0 is the bottom of the picture (screen, Media3 output).
layout(location = 0) in vec2 a_pos;
uniform float u_flipY;
out vec2 v_uv;
void main() {
    v_uv = a_pos * 0.5 + 0.5;
    if (u_flipY > 0.5) v_uv.y = 1.0 - v_uv.y;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}

#version 300 es
precision highp float;
// Copies an RGB(A) 2D texture into the pipeline's working texture. u_flipIn = 1 for inputs
// stored bottom-up (Media3 frames), so that the working texture is always top-down.
in vec2 v_uv;
out vec4 o_color;
uniform sampler2D u_tex;
uniform float u_flipIn;
void main() {
    vec2 uv = vec2(v_uv.x, u_flipIn > 0.5 ? 1.0 - v_uv.y : v_uv.y);
    o_color = vec4(texture(u_tex, uv).rgb, 1.0);
}

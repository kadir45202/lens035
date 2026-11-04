#version 300 es
precision highp float;
// Draws the finished image into the view: letterboxed, zoomed and panned.
in vec2 v_uv;
out vec4 o_color;
uniform sampler2D u_tex;
uniform vec4 u_view; // xy = scale, zw = offset: texUV = (viewUV - offset) / scale
uniform vec3 u_bg;
void main() {
    vec2 t = (v_uv - u_view.zw) / u_view.xy;
    if (t.x < 0.0 || t.y < 0.0 || t.x > 1.0 || t.y > 1.0) {
        o_color = vec4(u_bg, 1.0);
    } else {
        o_color = vec4(texture(u_tex, t).rgb, 1.0);
    }
}

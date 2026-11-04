#version 300 es
precision highp float;
// Separable 9-tap Gaussian (sigma ~2 texels) using linear-filtering offsets.
in vec2 v_uv;
out vec4 o_color;
uniform sampler2D u_tex;
uniform vec2 u_dir; // texel size * direction
void main() {
    vec4 c = texture(u_tex, v_uv) * 0.2270270270;
    c += texture(u_tex, v_uv + u_dir * 1.3846153846) * 0.3162162162;
    c += texture(u_tex, v_uv - u_dir * 1.3846153846) * 0.3162162162;
    c += texture(u_tex, v_uv + u_dir * 3.2307692308) * 0.0702702703;
    c += texture(u_tex, v_uv - u_dir * 3.2307692308) * 0.0702702703;
    o_color = c;
}

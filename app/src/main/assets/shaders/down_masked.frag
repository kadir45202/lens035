#version 300 es
precision highp float;
// First level of the background-only blur pyramid for portrait blur: 2x downsample of the graded
// image premultiplied by the background weight (1 - person). Later levels reuse down/blur, which
// keep the premultiplication, and the effects pass divides rgb by alpha.
in vec2 v_uv;
out vec4 o_color;
uniform sampler2D u_tex;
uniform sampler2D u_mask;
uniform float u_hasMask;
uniform vec2 u_texel; // texel size of the SOURCE level

vec4 tap(vec2 uv) {
    vec3 c = texture(u_tex, uv).rgb;
    float person = u_hasMask > 0.5 ? smoothstep(0.3, 0.7, texture(u_mask, uv).r) : 0.0;
    float w = 1.0 - person;
    return vec4(c * w, w);
}

void main() {
    vec4 s = tap(v_uv) * 4.0;
    s += tap(v_uv + vec2(-1.0, -1.0) * u_texel);
    s += tap(v_uv + vec2(1.0, -1.0) * u_texel);
    s += tap(v_uv + vec2(-1.0, 1.0) * u_texel);
    s += tap(v_uv + vec2(1.0, 1.0) * u_texel);
    o_color = s / 8.0;
}

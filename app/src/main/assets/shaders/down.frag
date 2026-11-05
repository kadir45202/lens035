#version 300 es
precision highp float;
//#include common
// 2x downsample with a 5-tap tent filter. With u_lumaAlpha = 1 the perceptual luminance of
// each tap is averaged into alpha (used by local contrast and tone masks).
in vec2 v_uv;
out vec4 o_color;
uniform sampler2D u_tex;
uniform vec2 u_texel; // texel size of the SOURCE level
uniform float u_lumaAlpha;

vec4 tap(vec2 uv) {
    vec4 c = texture(u_tex, uv);
    if (u_lumaAlpha > 0.5) c.a = perceptualLuma(c.rgb);
    return c;
}

void main() {
    vec4 s = tap(v_uv) * 4.0;
    s += tap(v_uv + vec2(-1.0, -1.0) * u_texel);
    s += tap(v_uv + vec2(1.0, -1.0) * u_texel);
    s += tap(v_uv + vec2(-1.0, 1.0) * u_texel);
    s += tap(v_uv + vec2(1.0, 1.0) * u_texel);
    o_color = s / 8.0;
}

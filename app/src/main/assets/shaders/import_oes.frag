#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
// Copies a camera/video SurfaceTexture frame into the working texture (top-down), with an
// approximate HDR (HLG / PQ, BT.2020) to SDR conversion for previewing HDR clips.
in vec2 v_uv;
out vec4 o_color;
uniform samplerExternalOES u_tex;
uniform mat4 u_texMatrix;
uniform int u_transfer; // 0 = SDR, 1 = HLG, 2 = PQ

vec3 bt2020ToBt709(vec3 c) {
    return mat3(1.6605, -0.1246, -0.0182,
                -0.5876, 1.1329, -0.1006,
                -0.0728, -0.0083, 1.1187) * c;
}

float hlgInverse(float e) {
    const float a = 0.17883277;
    const float b = 0.28466892;
    const float c = 0.55991073;
    return e <= 0.5 ? e * e / 3.0 : (exp((e - c) / a) + b) / 12.0;
}

float pqToNits(float e) {
    const float m1 = 0.1593017578125;
    const float m2 = 78.84375;
    const float c1 = 0.8359375;
    const float c2 = 18.8515625;
    const float c3 = 18.6875;
    float p = pow(max(e, 0.0), 1.0 / m2);
    return 10000.0 * pow(max(p - c1, 0.0) / (c2 - c3 * p), 1.0 / m1);
}

vec3 toneMap(vec3 lin) {
    float y = dot(lin, vec3(0.2126, 0.7152, 0.0722));
    float knee = 0.8;
    float yt = y <= knee ? y : knee + (1.0 - knee) * (1.0 - exp(-(y - knee) / (1.0 - knee)));
    vec3 c = y > 1.0e-6 ? lin * (yt / y) : lin;
    return clamp(c, 0.0, 1.0);
}

float linearToSrgb1(float c) {
    c = max(c, 0.0);
    return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

void main() {
    vec2 st = vec2(v_uv.x, 1.0 - v_uv.y);
    vec2 tc = (u_texMatrix * vec4(st, 0.0, 1.0)).xy;
    vec3 c = texture(u_tex, tc).rgb;
    if (u_transfer == 1) {
        vec3 scene = vec3(hlgInverse(c.r), hlgInverse(c.g), hlgInverse(c.b));
        float ys = max(dot(scene, vec3(0.2627, 0.6780, 0.0593)), 1.0e-6);
        vec3 display = scene * pow(ys, 0.2);
        vec3 lin = toneMap(max(bt2020ToBt709(display) * 4.9, 0.0));
        c = vec3(linearToSrgb1(lin.r), linearToSrgb1(lin.g), linearToSrgb1(lin.b));
    } else if (u_transfer == 2) {
        vec3 nits = vec3(pqToNits(c.r), pqToNits(c.g), pqToNits(c.b));
        vec3 lin = toneMap(max(bt2020ToBt709(nits / 203.0), 0.0));
        c = vec3(linearToSrgb1(lin.r), linearToSrgb1(lin.g), linearToSrgb1(lin.b));
    }
    o_color = vec4(c, 1.0);
}

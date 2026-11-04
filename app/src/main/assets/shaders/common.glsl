// Shared colour helpers. Inserted by the loader where a shader says "//#include common".
// The Kotlin counterparts live in core (ColorMath / ColorOps); keep the two in sync.

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const float PI = 3.14159265359;

float luma(vec3 c) { return dot(c, LUMA); }

float srgbToLinear1(float c) {
    c = max(c, 0.0);
    return c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4);
}

float linearToSrgb1(float c) {
    c = max(c, 0.0);
    return c <= 0.0031308 ? c * 12.92 : 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

vec3 srgbToLinear(vec3 c) {
    return vec3(srgbToLinear1(c.r), srgbToLinear1(c.g), srgbToLinear1(c.b));
}

vec3 linearToSrgb(vec3 c) {
    return vec3(linearToSrgb1(c.r), linearToSrgb1(c.g), linearToSrgb1(c.b));
}

// Perceptual (display-encoded) luminance of a display-encoded colour.
float perceptualLuma(vec3 enc) { return linearToSrgb1(luma(srgbToLinear(enc))); }

vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

vec3 hsv2rgb(vec3 c) {
    vec3 p = abs(fract(c.xxx + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return c.z * mix(vec3(1.0), clamp(p - 1.0, 0.0, 1.0), c.y);
}

float hueDist(float a, float b) { return mod(a - b + 540.0, 360.0) - 180.0; }

// Weight of one hue band (centre c, distances to the neighbouring centres).
float bandW(float h, float c, float left, float right) {
    float d = hueDist(h, c);
    float width = d >= 0.0 ? right : left;
    float t = clamp(1.0 - abs(d) / width, 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

// Eight colour-mixer bands: red, orange, yellow, green | aqua, blue, purple, magenta.
void bandWeights(float h, out vec4 w0, out vec4 w1) {
    w0 = vec4(bandW(h, 0.0, 60.0, 30.0), bandW(h, 30.0, 30.0, 30.0),
              bandW(h, 60.0, 30.0, 60.0), bandW(h, 120.0, 60.0, 60.0));
    w1 = vec4(bandW(h, 180.0, 60.0, 60.0), bandW(h, 240.0, 60.0, 30.0),
              bandW(h, 270.0, 30.0, 30.0), bandW(h, 300.0, 30.0, 60.0));
}

vec3 wbGains(float temp, float tint) {
    vec3 g = vec3(exp2(temp * 0.30), 1.0, exp2(-temp * 0.40));
    g.g *= exp2(-tint * 0.26);
    g.r *= exp2(tint * 0.06);
    g.b *= exp2(tint * 0.06);
    return g / luma(g);
}

float contrastCurve(float x, float c) {
    if (abs(c) < 1.0e-5) return x;
    float v = clamp(x, 0.0, 1.0);
    float gExp = log(0.5) / log(0.45);
    float t = pow(v, gExp);
    float k = exp(c * 0.9);
    float a = pow(t, k);
    float b = pow(1.0 - t, k);
    float s = (a + b) <= 1.0e-12 ? t : a / (a + b);
    return pow(s, 1.0 / gExp);
}

// Scales a linear colour back into gamut keeping luminance and hue.
vec3 gamutClip(vec3 lin) {
    float y = luma(lin);
    if (y >= 1.0) return vec3(1.0);
    if (y <= 0.0) return vec3(0.0);
    float mx = max(lin.r, max(lin.g, lin.b));
    float mn = min(lin.r, min(lin.g, lin.b));
    float k = 1.0;
    if (mx > 1.0) k = min(k, (1.0 - y) / (mx - y));
    if (mn < 0.0) k = min(k, y / (y - mn));
    return y + (lin - y) * k;
}

vec3 screenBlend(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - clamp(b, 0.0, 1.0)); }

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

#version 300 es
precision highp float;
//#include common
// Final pass in output space: geometry (crop/rotate/flip/straighten), creative effects,
// vignette, grain, date stamp and frame. v_uv is the output coordinate (v = 0 at top).

in vec2 v_uv;
out vec4 o_color;

uniform sampler2D u_graded;  // colour pipeline result, source space
uniform sampler2D u_bloomH;  // 1/2 res copy of the graded image
uniform sampler2D u_bloomA;  // 1/4 res, blurred
uniform sampler2D u_bloomB;  // 1/8 res, blurred
uniform sampler2D u_bloomC;  // 1/16 res, blurred
uniform sampler2D u_overlay; // date stamp glyphs (white on transparent)
uniform sampler2D u_personMaskE; // person segmentation, source space
uniform float u_hasPersonMaskE;
uniform sampler2D u_bokehA; // background-only blur, premultiplied (rgb * w, w), 1/4 res
uniform sampler2D u_bokehB; // 1/8 res
uniform sampler2D u_bokehC; // 1/16 res

uniform mat3 u_geo;
uniform vec2 u_outSize;
uniform vec2 u_srcSize;
uniform float u_time;
uniform float u_frameIndex;

uniform vec4 u_vignette;  // amount, midpoint, roundness, feather
uniform float u_vignetteHL;
uniform vec4 u_grain;     // amount, size, roughness, colour
uniform vec4 u_halation;  // amount, threshold, radius
uniform vec3 u_halationColor;
uniform vec4 u_glow;      // amount, threshold, radius, warmth
uniform float u_orton;

uniform vec4 u_fxMirror;
uniform vec4 u_fxFisheye;
uniform vec4 u_fxGlitch;
uniform vec4 u_fxVhs;
uniform float u_fxVhsSeed;
uniform vec4 u_fxPixel;
uniform vec4 u_fxZoom;
uniform vec4 u_fxBokeh;   // amount, glow, edge softness
uniform vec4 u_fxTilt;
uniform vec4 u_fxRgb;
uniform vec4 u_fxPrism;
uniform vec4 u_fxSoft;
uniform vec4 u_fxKira;
uniform float u_fxKiraAngle;
uniform vec4 u_fxRays;
uniform float u_fxRaysWarmth;
uniform vec4 u_fxLeak;
uniform float u_fxLeakSeed;
uniform vec4 u_fxRainbow;
uniform vec4 u_fxDuotone;
uniform vec3 u_duoShadow;
uniform vec3 u_duoHighlight;
uniform vec4 u_fxPoster;
uniform vec4 u_fxScan;
uniform vec4 u_fxDust;
uniform vec4 u_frameFx;    // enabled, style, corner radius
uniform vec4 u_frameInner; // x, y, w, h in output uv
uniform vec4 u_frameUv;    // scale xy, offset zw
uniform vec4 u_fxDate;     // amount, style
uniform vec4 u_dateRect;
uniform vec3 u_dateColor;

float gAspect; // aspect of the photo area, set in main()

// Output -> source coordinate. u_geo is projective when perspective correction is on.
vec2 toSrc(vec2 q) {
    vec3 p = u_geo * vec3(q, 1.0);
    return p.xy / p.z;
}

vec3 img(vec2 q) {
    vec2 s = toSrc(q);
    if (s.x < -0.0005 || s.y < -0.0005 || s.x > 1.0005 || s.y > 1.0005) return vec3(0.0);
    return texture(u_graded, s).rgb;
}

vec3 bloomTex(sampler2D t, vec2 q) { return texture(t, toSrc(q)).rgb; }

vec2 mirrorUv(vec2 q, float mode) {
    if (mode < 0.5) { if (q.x > 0.5) q.x = 1.0 - q.x; }
    else if (mode < 1.5) { if (q.x < 0.5) q.x = 1.0 - q.x; }
    else if (mode < 2.5) { if (q.y > 0.5) q.y = 1.0 - q.y; }
    else { q = 0.5 - abs(q - 0.5); }
    return q;
}

vec2 fisheyeUv(vec2 q, float amount, float zoom) {
    vec2 asp = vec2(gAspect, 1.0);
    vec2 c = (q - 0.5) * asp;
    float rmax = length(asp * 0.5);
    float rn = length(c) / rmax;
    float k = amount * 0.9;
    float rs = pow(rn, 1.0 + k) * (1.0 - zoom * 0.35 * amount);
    vec2 dir = rn > 1.0e-5 ? c / (rn * rmax) : vec2(0.0);
    return 0.5 + dir * rs * rmax / asp;
}

float sdRoundRect(vec2 p, vec2 halfSize, float r) {
    vec2 d = abs(p) - halfSize + r;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - r;
}

void main() {
    vec2 uv = v_uv;
    float timeSeed = floor(u_frameIndex);

    // ---------------------------------------------------------------- frame layout
    vec2 puv = uv;
    float inside = 1.0;
    if (u_frameFx.x > 0.5) {
        vec2 inner = (uv - u_frameInner.xy) / u_frameInner.zw;
        vec2 sizePx = u_frameInner.zw * u_outSize;
        float radPx = u_frameFx.z * min(sizePx.x, sizePx.y);
        float sd = sdRoundRect((inner - 0.5) * sizePx, sizePx * 0.5, radPx);
        inside = 1.0 - smoothstep(-0.75, 0.75, sd);
        puv = inner * u_frameUv.xy + u_frameUv.zw;
        gAspect = (u_frameInner.z * u_outSize.x) / (u_frameInner.w * u_outSize.y);
    } else {
        gAspect = u_outSize.x / u_outSize.y;
    }

    // ---------------------------------------------------------------- uv distortions
    vec2 q = puv;
    if (u_fxFisheye.x > 0.0) q = fisheyeUv(q, u_fxFisheye.x, u_fxFisheye.y);
    vec2 qm = q;
    if (u_fxMirror.x > 0.0) qm = mirrorUv(q, u_fxMirror.y);
    if (u_fxMirror.x >= 0.999) q = qm;

    float glitchShift = 0.0;
    if (u_fxGlitch.x > 0.0) {
        float g = u_fxGlitch.x;
        float rows = mix(10.0, 46.0, u_fxGlitch.y);
        float row = floor(q.y * rows);
        float tseed = floor(u_time * 9.0);
        float r1 = hash12(vec2(row, u_fxGlitch.w + tseed));
        if (r1 < 0.12 + 0.35 * g) {
            float off = (hash12(vec2(row * 1.7, u_fxGlitch.w + tseed + 3.1)) - 0.5) * 0.16 * g;
            float sub = step(0.5, hash12(vec2(floor(q.x * 6.0), row + u_fxGlitch.w)));
            q.x += off * (0.4 + 0.6 * sub);
            glitchShift = u_fxGlitch.z * g * 0.012 * sign(off);
        }
    }
    if (u_fxVhs.x > 0.0) {
        float v = u_fxVhs.x;
        float line = floor(q.y * u_outSize.y * 0.5);
        float jitter = (valueNoise(vec2(line * 0.05, u_time * 6.0 + u_fxVhsSeed)) - 0.5) * 0.006 * u_fxVhs.y * v;
        float band = smoothstep(0.86, 0.9, q.y) * (1.0 - smoothstep(0.95, 0.99, q.y));
        jitter += band * (hash12(vec2(line, timeSeed + u_fxVhsSeed)) - 0.5) * 0.03 * v;
        q.x += jitter;
    }
    if (u_fxPixel.x > 0.0) {
        float cells = mix(150.0, 12.0, pow(u_fxPixel.x, 0.6));
        vec2 grid = vec2(cells * gAspect, cells);
        q = (floor(q * grid) + 0.5) / grid;
    }

    // ---------------------------------------------------------------- sampling
    vec3 col;
    vec2 rgbOff = vec2(0.0);
    if (u_fxRgb.x > 0.0) {
        rgbOff = vec2(cos(u_fxRgb.y), sin(u_fxRgb.y)) * u_fxRgb.x * 0.012 / vec2(gAspect, 1.0);
        rgbOff += (q - 0.5) * u_fxRgb.z * u_fxRgb.x * 0.035;
    }
    rgbOff.x += glitchShift;
    float bleed = u_fxVhs.x > 0.0 ? u_fxVhs.w * u_fxVhs.x : 0.0;
    if (dot(rgbOff, rgbOff) > 0.0 || bleed > 0.0) {
        float r = img(q + rgbOff).r;
        float g = img(q).g;
        float b = img(q - rgbOff).b;
        col = vec3(r, g, b);
        if (bleed > 0.0) {
            // VHS: luminance stays sharp, colour smears sideways.
            vec3 smear = vec3(0.0);
            for (int i = 0; i < 5; i++) {
                float o = (float(i) - 1.0) * 0.004 * bleed;
                smear += img(q + vec2(o, 0.0));
            }
            smear /= 5.0;
            float y = luma(col);
            col = clamp(y + (smear - luma(smear)) * 1.1, 0.0, 1.0);
            col = mix(col, vec3(luma(col)), 0.15 * u_fxVhs.x);
        }
    } else {
        col = img(q);
    }
    if (u_fxMirror.x > 0.0 && u_fxMirror.x < 0.999) col = mix(col, img(qm), u_fxMirror.x);

    if (u_fxZoom.x > 0.0) {
        vec2 c = u_fxZoom.yz;
        float dist = length((q - c) * vec2(gAspect, 1.0));
        float k = smoothstep(u_fxZoom.w * 0.5, u_fxZoom.w * 0.5 + 0.35, dist) * u_fxZoom.x * 0.16;
        vec3 acc = vec3(0.0);
        for (int i = 0; i < 14; i++) {
            float t = float(i) / 13.0;
            acc += img(q + (c - q) * t * k);
        }
        col = acc / 14.0;
    }

    if (u_fxBokeh.x > 0.0 && u_hasPersonMaskE > 0.5) {
        // Portrait blur: the person stays sharp, the background comes from a blur pyramid that was
        // built from background pixels only, so the subject does not bleed into it as a halo.
        vec2 s = toSrc(q);
        float person = texture(u_personMaskE, s).r;
        float edge = mix(0.06, 0.32, u_fxBokeh.z);
        float fg = smoothstep(0.5 - edge, 0.5 + edge, person);
        float amt = u_fxBokeh.x;
        vec4 a = texture(u_bokehA, s);
        vec4 b = texture(u_bokehB, s);
        vec4 c = texture(u_bokehC, s);
        vec4 blur = amt < 0.5 ? mix(a, b, amt * 2.0) : mix(b, c, (amt - 0.5) * 2.0);
        vec3 bg = blur.a > 0.002 ? blur.rgb / blur.a : col;
        bg += max(bg - 0.62, 0.0) * u_fxBokeh.y * 1.4;
        vec3 soft = mix(col, bg, smoothstep(0.0, 0.25, amt));
        col = mix(soft, col, fg);
    }
    if (u_fxTilt.x > 0.0) {
        vec2 n = vec2(-sin(u_fxTilt.w), cos(u_fxTilt.w));
        float d = abs(dot((q - vec2(0.5, u_fxTilt.y)) * vec2(gAspect, 1.0), n));
        float b = smoothstep(u_fxTilt.z * 0.5, u_fxTilt.z * 0.5 + 0.28, d) * u_fxTilt.x;
        vec3 blurH = bloomTex(u_bloomH, q);
        vec3 blurA = bloomTex(u_bloomA, q);
        vec3 blurB = bloomTex(u_bloomB, q);
        vec3 blurred = b < 0.5 ? mix(blurH, blurA, b * 2.0) : mix(blurA, blurB, (b - 0.5) * 1.4);
        col = mix(col, blurred, smoothstep(0.0, 0.35, b));
        float y = luma(col);
        col = clamp(y + (col - y) * (1.0 + 0.18 * u_fxTilt.x), 0.0, 1.0);
    }

    if (u_fxPrism.x > 0.0) {
        vec2 d = vec2(cos(u_fxPrism.z), sin(u_fxPrism.z)) * (0.01 + 0.05 * u_fxPrism.y) / vec2(gAspect, 1.0);
        vec3 ghost = vec3(img(q + d).r, img(q + d * 1.6).g, img(q + d * 2.2).b);
        vec3 ghost2 = vec3(img(q - d * 2.0).r, img(q - d * 1.4).g, img(q - d * 0.8).b);
        col = mix(col, screenBlend(col, (ghost + ghost2) * 0.42), u_fxPrism.x * 0.85);
    }

    // ---------------------------------------------------------------- light
    if (u_fxSoft.x > 0.0) {
        vec3 b = mix(bloomTex(u_bloomA, q), bloomTex(u_bloomB, q), u_fxSoft.y);
        vec3 soft = screenBlend(col, b * (0.55 + 0.6 * u_fxSoft.z));
        col = mix(col, soft, u_fxSoft.x * 0.75);
        col = mix(col, mix(col, b, 0.35), u_fxSoft.x * 0.5);
    }
    if (u_orton > 0.0) {
        vec3 b = bloomTex(u_bloomB, q) * 1.08;
        vec3 o = screenBlend(col, b);
        o = mix(o, clamp(o * col * 1.7, 0.0, 1.0), 0.35);
        float y = luma(o);
        o = clamp(y + (o - y) * 1.12, 0.0, 1.0);
        col = mix(col, o, u_orton * 0.75);
    }
    if (u_glow.x > 0.0) {
        vec3 b1 = bloomTex(u_bloomA, q);
        vec3 b2 = bloomTex(u_bloomB, q);
        vec3 b3 = bloomTex(u_bloomC, q);
        vec3 b = mix(b1 * 0.55 + b2 * 0.45, b2 * 0.35 + b3 * 0.65, u_glow.z);
        float th = u_glow.y * 0.9;
        vec3 hi = max(b - th, 0.0) / max(1.0 - th, 0.05);
        vec3 tintC = u_glow.w >= 0.0 ? mix(vec3(1.0), vec3(1.0, 0.78, 0.55), u_glow.w) : mix(vec3(1.0), vec3(0.6, 0.8, 1.0), -u_glow.w);
        col = screenBlend(col, hi * tintC * u_glow.x * 1.25);
    }
    if (u_halation.x > 0.0) {
        vec3 b = mix(bloomTex(u_bloomA, q), bloomTex(u_bloomB, q), u_halation.z);
        float th = u_halation.y * 0.95;
        float h = max(luma(b) - th, 0.0) / max(1.0 - th, 0.05);
        float core = smoothstep(th, 1.0, luma(col));
        vec3 halo = u_halationColor * h * (1.0 - 0.6 * core) * u_halation.x * 1.6;
        col = screenBlend(col, halo);
    }
    if (u_fxKira.x > 0.0) {
        float th = u_fxKira.y * 0.95;
        float len = mix(0.025, 0.14, u_fxKira.z);
        float rays = u_fxKira.w;
        vec3 acc = vec3(0.0);
        for (int r = 0; r < 4; r++) {
            if (float(r) * 2.0 >= rays) break;
            float ang = u_fxKiraAngle + PI * float(r) / (rays * 0.5);
            vec2 dir = vec2(cos(ang), sin(ang)) / vec2(gAspect, 1.0);
            for (int i = 1; i <= 10; i++) {
                float t = float(i) / 10.0;
                vec2 o = dir * t * len;
                float fall = (1.0 - t) * (1.0 - t);
                vec3 s1 = bloomTex(u_bloomH, q + o);
                vec3 s2 = bloomTex(u_bloomH, q - o);
                acc += (max(s1 - th, 0.0) + max(s2 - th, 0.0)) * fall;
            }
        }
        acc /= max(1.0 - th, 0.05);
        vec3 star = mix(vec3(1.0), vec3(0.9, 0.95, 1.0), 0.5);
        col = screenBlend(col, acc * star * u_fxKira.x * 0.16);
    }
    if (u_fxRays.x > 0.0) {
        vec2 lp = u_fxRays.yz;
        vec2 delta = (q - lp) * (0.25 + 0.75 * u_fxRays.w) / 24.0;
        vec2 p = q;
        float illum = 1.0;
        float acc = 0.0;
        for (int i = 0; i < 24; i++) {
            p -= delta;
            float l = luma(bloomTex(u_bloomA, p));
            acc += max(l - 0.5, 0.0) * illum;
            illum *= 0.93;
        }
        vec2 dv = (q - lp) * vec2(gAspect, 1.0);
        float dist = length(dv);
        float ang = atan(dv.y, dv.x);
        float n1 = valueNoise(vec2(ang * 5.0 + 3.0, 1.7));
        float n2 = valueNoise(vec2(ang * 11.0 + 9.0, 4.1));
        float beams = smoothstep(0.35, 0.95, n1 * 0.65 + n2 * 0.35);
        float falloff = exp(-dist * (3.0 - 1.8 * u_fxRays.w));
        float source = exp(-dist * dist * 28.0);
        vec3 warm = mix(vec3(1.0, 0.97, 0.9), vec3(1.0, 0.72, 0.38), u_fxRaysWarmth);
        float rays = acc * 0.25 + beams * falloff * 0.28 * smoothstep(0.0, 0.08, dist) + source * 0.55;
        col = screenBlend(col, warm * rays * u_fxRays.x);
    }
    if (u_fxLeak.x > 0.0) {
        float drift = u_time * 0.03;
        float ang = (u_fxLeak.z + drift) * 6.2831853 + u_fxLeakSeed * 0.37;
        vec2 asp = vec2(gAspect, 1.0);
        vec2 c1 = 0.5 + vec2(cos(ang), sin(ang)) * 0.62;
        vec2 c2 = 0.5 + vec2(cos(ang + 2.4 + u_fxLeakSeed * 0.11), sin(ang + 2.4)) * 0.7;
        float size = 0.22 + 0.55 * u_fxLeak.w;
        float d1 = length((q - c1) * asp);
        float d2 = length((q - c2) * asp);
        float b1 = exp(-pow(d1 / size, 2.0) * 1.6);
        float b2 = exp(-pow(d2 / (size * 0.7), 2.0) * 2.2) * 0.55;
        float flicker = 0.9 + 0.1 * sin(u_time * 2.3 + u_fxLeakSeed);
        vec3 h1 = hsv2rgb(vec3(fract(u_fxLeak.y / 360.0), 0.85, 1.0));
        vec3 h2 = hsv2rgb(vec3(fract((u_fxLeak.y + 32.0) / 360.0), 0.7, 1.0));
        vec3 leak = mix(h2, h1, b1) * b1 + h2 * b2;
        col = screenBlend(col, leak * u_fxLeak.x * flicker);
    }
    if (u_fxRainbow.x > 0.0) {
        vec2 dir = vec2(cos(u_fxRainbow.y), sin(u_fxRainbow.y));
        float band = fract(dot(q * vec2(gAspect, 1.0), dir) * mix(0.6, 3.0, u_fxRainbow.z) + 0.15 * sin(q.y * 7.0));
        vec3 rb = hsv2rgb(vec3(band, 0.55, 1.0));
        float y = luma(col);
        vec3 soft = mix(2.0 * col * rb + col * col * (1.0 - 2.0 * rb), sqrt(col) * (2.0 * rb - 1.0) + 2.0 * col * (1.0 - rb), step(0.5, rb));
        col = mix(col, soft, min(1.0, u_fxRainbow.x * 1.6) * (0.45 + 0.55 * smoothstep(0.15, 0.85, y)));
    }

    // ---------------------------------------------------------------- colour effects
    if (u_fxDuotone.x > 0.0) {
        float y = luma(col);
        float t = contrastCurve(y, (u_fxDuotone.y - 0.5) * 1.6);
        vec3 duo = mix(u_duoShadow, u_duoHighlight, t);
        col = mix(col, duo, u_fxDuotone.x);
    }
    if (u_fxPoster.x > 0.0) {
        float levels = mix(9.0, 2.5, u_fxPoster.x);
        col = floor(col * levels + 0.5) / levels;
    }

    // ---------------------------------------------------------------- vignette
    if (abs(u_vignette.x) > 0.001) {
        vec2 pp = (puv - 0.5) * 2.0;
        float r = u_vignette.z;
        vec2 circ = gAspect >= 1.0 ? vec2(gAspect, 1.0) : vec2(1.0, 1.0 / gAspect);
        vec2 q2 = pp * mix(vec2(1.0), circ, clamp(r, 0.0, 1.0));
        float n = r < 0.0 ? mix(2.0, 7.0, -r) : 2.0;
        float d = pow(pow(abs(q2.x), n) + pow(abs(q2.y), n), 1.0 / n);
        float inner = mix(0.15, 1.15, u_vignette.y);
        float v = smoothstep(inner, inner + mix(0.04, 1.3, u_vignette.w), d);
        if (u_vignette.x < 0.0) {
            vec3 dark = col * (1.0 - v * -u_vignette.x * 0.95);
            float protect = u_vignetteHL * smoothstep(0.65, 1.0, luma(col));
            col = mix(dark, col, protect);
        } else {
            col = mix(col, vec3(1.0), v * u_vignette.x * 0.85);
        }
    }

    // ---------------------------------------------------------------- texture effects
    if (u_fxVhs.x > 0.0) {
        float v = u_fxVhs.x;
        float noise = hash12(vec2(floor(q.y * u_outSize.y * 0.5), timeSeed + u_fxVhsSeed));
        float streak = step(0.995 - 0.01 * u_fxVhs.z * v, noise) * hash12(vec2(floor(q.x * 60.0), noise * 91.0));
        float snow = (hash12(uv * u_outSize + timeSeed * 13.1) - 0.5) * 0.18 * u_fxVhs.z * v;
        col += streak * 0.6 + snow;
        float scan = 0.5 + 0.5 * sin(uv.y * u_outSize.y * 1.5);
        col *= 1.0 - 0.08 * v * scan;
    }
    if (u_fxScan.x > 0.0) {
        float lines = mix(160.0, 520.0, u_fxScan.y);
        float s = 0.5 + 0.5 * sin(uv.y * lines * PI);
        col *= mix(1.0, 0.68 + 0.32 * s, u_fxScan.x);
        float px = mod(floor(uv.x * u_outSize.x / max(1.0, u_outSize.x / 640.0)), 3.0);
        vec3 maskC = px < 0.5 ? vec3(1.0, 0.85, 0.85) : (px < 1.5 ? vec3(0.85, 1.0, 0.85) : vec3(0.85, 0.85, 1.0));
        col *= mix(vec3(1.0), maskC, u_fxScan.x * 0.5);
        col *= 1.0 + 0.12 * u_fxScan.x;
    }
    if (u_fxDust.x > 0.0) {
        float acc = 0.0;
        float tseed = floor(u_time * 12.0);
        for (int layer = 0; layer < 2; layer++) {
            float fl = float(layer);
            float cells = mix(16.0, 7.0, u_fxDust.z) * (1.0 + fl * 1.6);
            vec2 g = puv * vec2(gAspect, 1.0) * cells;
            vec2 id = floor(g);
            vec2 f = fract(g);
            float h = hash12(id + u_fxDust.w * 13.7 + fl * 31.0 + tseed * 0.71);
            if (h > 1.0 - 0.38 * u_fxDust.x) {
                vec2 pos = hash22(id + u_fxDust.w + fl * 7.0 + tseed);
                float rad = mix(0.04, 0.15, hash12(id + 11.0 + u_fxDust.w)) * (1.0 - fl * 0.4);
                vec2 dd = (f - pos) * vec2(1.0, mix(1.0, 3.5, step(0.8, hash12(id + 5.0))));
                acc += smoothstep(rad, rad * 0.35, length(dd)) * (0.55 + 0.45 * hash12(id + 19.0));
            }
        }
        float scratch = 0.0;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float x = hash12(vec2(fi * 3.7, u_fxDust.w + floor(u_time * 3.0)));
            float wob = 0.003 * sin(puv.y * 13.0 + fi * 4.0);
            float on = step(hash12(vec2(fi, u_fxDust.w + 2.0)), u_fxDust.y);
            float seg = smoothstep(0.2, 0.5, valueNoise(vec2(fi * 10.0, puv.y * 4.0 + u_time)));
            scratch += on * seg * smoothstep(0.0012, 0.0, abs(puv.x - x - wob));
        }
        float d = clamp(acc + scratch, 0.0, 1.0) * min(1.0, u_fxDust.x * 1.3);
        // Most dust prints light (it blocks light on the negative); some specks print dark.
        float darkSpeck = step(0.7, hash12(floor(puv * 97.0) + u_fxDust.w));
        vec3 speck = mix(vec3(0.95, 0.94, 0.9), vec3(0.06), darkSpeck * 0.8);
        col = mix(col, speck, d * 0.9);
    }
    if (u_grain.x > 0.0) {
        float cellsPerHeight = mix(1500.0, 360.0, u_grain.y);
        vec2 gp = puv * vec2(gAspect, 1.0) * cellsPerHeight;
        vec2 off = vec2(timeSeed * 37.13, timeSeed * 11.71);
        float n1 = valueNoise(gp + off);
        float n2 = valueNoise(gp * 2.3 + off * 1.3 + 17.0);
        float n = mix(n1, (n1 + n2) * 0.5, u_grain.z) - 0.5;
        float hf = hash12(gp + off) - 0.5;
        n = mix(n, hf, 0.25 * u_grain.z) * 2.0;
        float y = luma(clamp(col, 0.0, 1.0));
        float amp = u_grain.x * 0.2 * (0.35 + 0.65 * 4.0 * y * (1.0 - y));
        vec3 gn = vec3(n);
        if (u_grain.w > 0.0) {
            vec3 cn = vec3(valueNoise(gp + off + 101.0), valueNoise(gp + off + 211.0), valueNoise(gp + off + 307.0)) - 0.5;
            gn = mix(gn, cn * 2.0, u_grain.w);
        }
        col += gn * amp;
    }

    // ---------------------------------------------------------------- date stamp
    if (u_fxDate.x > 0.0) {
        vec2 lp = (uv - u_dateRect.xy) / u_dateRect.zw;
        if (lp.x > -0.1 && lp.y > -0.3 && lp.x < 1.1 && lp.y < 1.3) {
            float a = texture(u_overlay, clamp(lp, 0.0, 1.0)).a;
            if (lp.x < 0.0 || lp.y < 0.0 || lp.x > 1.0 || lp.y > 1.0) a = 0.0;
            float glow = 0.0;
            vec2 px = 1.0 / (u_dateRect.zw * u_outSize);
            for (int i = 0; i < 8; i++) {
                float ang = float(i) * PI * 0.25;
                vec2 o = vec2(cos(ang), sin(ang)) * px * 2.5;
                vec2 sp = lp + o;
                if (sp.x >= 0.0 && sp.y >= 0.0 && sp.x <= 1.0 && sp.y <= 1.0) glow += texture(u_overlay, sp).a;
            }
            glow /= 8.0;
            vec3 dc = u_dateColor;
            col = screenBlend(col, dc * glow * 0.55 * u_fxDate.x);
            col = mix(col, min(dc * 1.08, vec3(1.0)), a * 0.92 * u_fxDate.x);
        }
    }

    // ---------------------------------------------------------------- frame
    if (u_frameFx.x > 0.5) {
        float style = u_frameFx.y;
        vec3 paper = vec3(0.957, 0.945, 0.918);
        paper += (valueNoise(uv * u_outSize * 0.5) - 0.5) * 0.025;
        vec3 border = style > 1.5 && style < 2.5 ? vec3(0.025) : paper;
        col = mix(border, col, inside);
    }

    o_color = vec4(clamp(col, 0.0, 1.0), 1.0);
}

#version 300 es
precision highp float;
precision highp sampler3D;
//#include common
// The colour pipeline (Lightroom-style). Works in source-image space at working resolution.
// Order: noise reduction -> white balance & exposure -> dehaze -> tone (highlights, shadows,
// whites, blacks) -> contrast -> local contrast (texture, clarity, structure) -> film LUT ->
// fade -> custom LUT -> saturation & vibrance -> smart tools (sky, golden hour, skin) ->
// colour mixer -> colour grading -> tone curve -> sharpening.

in vec2 v_uv;
out vec4 o_color;

uniform sampler2D u_src;
uniform sampler2D u_pyr1; // 1/2 res, rgb = blurred colour, a = perceptual luma
uniform sampler2D u_pyr2; // 1/4 res, blurred
uniform sampler2D u_pyr3; // 1/8 res, blurred
uniform sampler2D u_pyr4; // 1/16 res, blurred
uniform sampler3D u_lut;
uniform sampler3D u_lut2;
uniform sampler2D u_curves;
uniform float u_lutSize;
uniform float u_lut2Size;

uniform vec2 u_texel;
uniform float u_aspect;

uniform float u_exposure;
uniform float u_contrast;
uniform float u_highlights;
uniform float u_shadows;
uniform float u_whites;
uniform float u_blacks;

uniform float u_temp;
uniform float u_tint;
uniform float u_vibrance;
uniform float u_saturation;

uniform float u_texture;
uniform float u_clarity;
uniform float u_dehaze;
uniform vec3 u_atmosphere;

uniform vec4 u_sharpen; // amount, radius px, detail, masking
uniform vec2 u_noise;   // luminance, colour

uniform vec4 u_mixHue[2];
uniform vec4 u_mixSat[2];
uniform vec4 u_mixLum[2];
uniform float u_mixEnabled;

uniform vec4 u_gradeShadow;
uniform vec4 u_gradeMid;
uniform vec4 u_gradeHigh;
uniform vec4 u_gradeGlobal;
uniform vec2 u_gradeBalance; // blending, balance
uniform float u_gradeEnabled;

uniform float u_curvesEnabled;

uniform float u_lutStrength;
uniform float u_filmFade;
uniform float u_lut2Strength;

uniform float u_structure;
uniform float u_sky;
uniform float u_golden;
uniform float u_skin;

uniform int u_maskCount;
uniform vec4 u_maskGeo[8];
uniform vec4 u_maskAdj[12];

uniform sampler2D u_personMask; // person segmentation (source space, r = confidence)
uniform float u_hasPersonMask;

float personAt(vec2 uv) {
    return u_hasPersonMask > 0.5 ? texture(u_personMask, uv).r : 0.0;
}

float maskWeight(vec4 g0, vec4 g1, vec2 uv) {
    vec2 asp = vec2(u_aspect, 1.0);
    vec2 p = uv * asp;
    float w;
    if (g0.x > 1.5) {
        float f = clamp(g0.z, 0.0, 1.0) * 0.45 + 0.02;
        w = smoothstep(0.5 - f, 0.5 + f, personAt(uv));
    } else if (g0.x < 0.5) {
        vec2 a = g1.xy * asp;
        vec2 b = g1.zw * asp;
        vec2 ab = b - a;
        float t = dot(p - a, ab) / max(dot(ab, ab), 1.0e-6);
        w = 1.0 - smoothstep(0.0, 1.0, t);
    } else {
        vec2 d = p - g1.xy * asp;
        float ca = cos(g0.w);
        float sa = sin(g0.w);
        d = vec2(ca * d.x + sa * d.y, -sa * d.x + ca * d.y);
        float dist = length(d / max(g1.zw, vec2(1.0e-4)));
        float f = clamp(g0.z, 0.001, 1.0);
        w = 1.0 - smoothstep(1.0 - f, 1.0, dist);
    }
    return g0.y > 0.5 ? 1.0 - w : w;
}

vec3 sampleLut(sampler3D lut, float size, vec3 c) {
    vec3 coord = clamp(c, 0.0, 1.0) * ((size - 1.0) / size) + 0.5 / size;
    return texture(lut, coord).rgb;
}

float curveLookup(float x, int channel) {
    vec4 t = texture(u_curves, vec2(clamp(x, 0.0, 1.0) * (255.0 / 256.0) + 0.5 / 256.0, 0.5));
    return channel == 0 ? t.r : (channel == 1 ? t.g : t.b);
}

// Rescales a display-encoded colour so that its perceptual luminance becomes newP.
vec3 withPerceptualLuma(vec3 enc, float oldP, float newP) {
    vec3 lin = srgbToLinear(enc);
    float oldY = srgbToLinear1(oldP);
    float newY = srgbToLinear1(clamp(newP, 0.0, 1.2));
    if (oldY < 1.0e-5) return linearToSrgb(vec3(newY));
    return linearToSrgb(gamutClip(lin * (newY / oldY)));
}

void main() {
    vec2 uv = v_uv;
    vec3 src = texture(u_src, uv).rgb;
    vec4 p1 = texture(u_pyr1, uv);
    vec4 p2 = texture(u_pyr2, uv);
    vec4 p3 = texture(u_pyr3, uv);
    vec4 p4 = texture(u_pyr4, uv);
    float srcP = perceptualLuma(src);

    // ---------------------------------------------------------------- local masks
    float exposure = u_exposure;
    float contrast = u_contrast;
    float highlights = u_highlights;
    float shadows = u_shadows;
    float whites = u_whites;
    float blacks = u_blacks;
    float temp = u_temp;
    float tint = u_tint;
    float saturation = u_saturation;
    float clarity = u_clarity;
    float dehaze = u_dehaze;
    float texAmt = u_texture;
    for (int i = 0; i < 4; i++) {
        if (i >= u_maskCount) break;
        float w = maskWeight(u_maskGeo[i * 2], u_maskGeo[i * 2 + 1], uv);
        vec4 a0 = u_maskAdj[i * 3];
        vec4 a1 = u_maskAdj[i * 3 + 1];
        vec4 a2 = u_maskAdj[i * 3 + 2];
        exposure += w * a0.x;
        contrast += w * a0.y;
        highlights += w * a0.z;
        shadows += w * a0.w;
        whites += w * a1.x;
        blacks += w * a1.y;
        temp += w * a1.z;
        tint += w * a1.w;
        saturation += w * a2.x;
        clarity += w * a2.y;
        dehaze += w * a2.z;
        texAmt += w * a2.w;
    }
    contrast = clamp(contrast, -1.0, 1.0);
    highlights = clamp(highlights, -1.0, 1.0);
    shadows = clamp(shadows, -1.0, 1.0);
    whites = clamp(whites, -1.0, 1.0);
    blacks = clamp(blacks, -1.0, 1.0);
    temp = clamp(temp, -1.5, 1.5);
    tint = clamp(tint, -1.5, 1.5);
    saturation = clamp(saturation, -1.0, 1.0);
    clarity = clamp(clarity, -1.0, 1.0);
    dehaze = clamp(dehaze, -1.0, 1.0);
    texAmt = clamp(texAmt, -1.0, 1.0);

    // ---------------------------------------------------------------- noise reduction
    vec3 enc = src;
    if (u_noise.y > 0.0) {
        vec3 blur = mix(p1.rgb, p2.rgb, smoothstep(0.4, 1.0, u_noise.y));
        vec3 lin0 = srgbToLinear(enc);
        vec3 blin = srgbToLinear(blur);
        float y0 = luma(lin0);
        float by = max(luma(blin), 1.0e-4);
        vec3 nr = blin * (y0 / by);
        enc = linearToSrgb(gamutClip(mix(lin0, nr, u_noise.y)));
    }
    if (u_noise.x > 0.0) {
        float diff = abs(srcP - p1.a);
        float flatness = 1.0 - smoothstep(0.015, 0.09, diff);
        float target = mix(p1.a, p2.a, smoothstep(0.5, 1.0, u_noise.x) * 0.5);
        float nP = mix(srcP, target, u_noise.x * flatness);
        enc = withPerceptualLuma(enc, perceptualLuma(enc), nP);
    }

    // ---------------------------------------------------------------- white balance & exposure
    float expGain = exp2(exposure);
    vec3 lin = srgbToLinear(enc) * wbGains(temp, tint) * expGain;

    // ---------------------------------------------------------------- dehaze
    if (abs(dehaze) > 0.001) {
        vec3 A = srgbToLinear(u_atmosphere) * expGain;
        float darkEnc = min(min(p4.r, p4.g), p4.b);
        float darkLin = srgbToLinear1(darkEnc) * expGain;
        float amax = max(max(A.r, A.g), max(A.b, 1.0e-3));
        float t = 1.0 - 0.95 * clamp(darkLin / amax, 0.0, 1.0);
        if (dehaze > 0.0) {
            float tt = mix(1.0, max(t, 0.28), dehaze * 0.8);
            lin = max((lin - A) / tt + A, vec3(0.0));
            // Dehazing also deepens colour a little, as the real haze removal would.
            float y = luma(lin);
            lin = max(y + (lin - y) * (1.0 + 0.15 * dehaze), vec3(0.0));
        } else {
            float h = -dehaze * (1.0 - 0.5 * t) * 0.65;
            lin = mix(lin, A * 0.9, h);
        }
    }

    // ---------------------------------------------------------------- tone
    float Y = max(luma(lin), 0.0);
    float P = linearToSrgb1(Y);
    float adapt = linearToSrgb1(srgbToLinear1(mix(p3.a, p4.a, 0.5)) * expGain);
    float Pm = mix(P, adapt, 0.35);
    float Pn = P;
    if (highlights < 0.0) {
        Pn += highlights * smoothstep(0.42, 0.98, Pm) * 0.28 * Pn;
    } else if (highlights > 0.0) {
        Pn += highlights * smoothstep(0.42, 0.98, Pm) * 0.32 * max(1.0 - Pn, 0.0);
    }
    if (shadows > 0.0) {
        Pn += shadows * (1.0 - smoothstep(0.06, 0.5, Pm)) * 0.50 * sqrt(max(Pn, 0.0)) * max(1.0 - Pn, 0.0);
    } else if (shadows < 0.0) {
        Pn += shadows * (1.0 - smoothstep(0.06, 0.5, Pm)) * 0.60 * Pn * max(1.0 - Pn, 0.0);
    }
    Pn += whites * 0.20 * smoothstep(0.5, 1.0, Pn);
    Pn += blacks * 0.16 * (1.0 - smoothstep(0.0, 0.5, Pn));
    Pn = max(Pn, 0.0);
    float Yn = srgbToLinear1(Pn);
    // Highlight roll-off so that positive exposure compresses instead of clipping hard.
    if (exposure > 0.0 || whites > 0.0) {
        float knee = 1.0 - 0.28 * clamp(max(exposure, 0.0) * 0.6 + max(whites, 0.0) * 0.3, 0.0, 1.0);
        if (Yn > knee) {
            float r = 1.0 - knee;
            Yn = knee + r * (1.0 - exp(-(Yn - knee) / max(r, 1.0e-4)));
        }
    }
    lin = Y > 1.0e-6 ? lin * (Yn / Y) : vec3(Yn);
    lin = gamutClip(lin);
    enc = linearToSrgb(lin);

    // ---------------------------------------------------------------- contrast
    if (abs(contrast) > 0.001) {
        enc = vec3(contrastCurve(enc.r, contrast), contrastCurve(enc.g, contrast), contrastCurve(enc.b, contrast));
    }

    // ---------------------------------------------------------------- local contrast
    float structure = u_structure;
    if (abs(texAmt) > 0.001 || abs(clarity) > 0.001 || structure > 0.001) {
        float Pc = perceptualLuma(enc);
        float dTex = 0.25 * tanh((srcP - p1.a) / 0.25);
        float dCla = 0.25 * tanh((srcP - p3.a) / 0.25);
        float dStr = 0.12 * tanh((srcP - p2.a) / 0.12);
        float mid = 1.0 - pow(abs(2.0 * Pc - 1.0), 2.0);
        float newP = Pc + texAmt * 1.3 * dTex + clarity * 0.9 * dCla * mid + structure * 1.1 * dStr * (0.35 + 0.65 * mid);
        enc = withPerceptualLuma(enc, Pc, newP);
        if (clarity > 0.0) {
            // Clarity adds a hint of saturation like its photographic counterpart.
            vec3 l2 = srgbToLinear(enc);
            float y2 = luma(l2);
            enc = linearToSrgb(gamutClip(y2 + (l2 - y2) * (1.0 + 0.12 * clarity)));
        }
    }

    // ---------------------------------------------------------------- film
    if (u_lutStrength > 0.0) {
        enc = mix(enc, sampleLut(u_lut, u_lutSize, enc), u_lutStrength);
    }
    if (u_filmFade > 0.0) {
        enc = u_filmFade + enc * (1.0 - u_filmFade);
    }
    if (u_lut2Strength > 0.0) {
        enc = mix(enc, sampleLut(u_lut2, u_lut2Size, enc), u_lut2Strength);
    }

    // ---------------------------------------------------------------- saturation & vibrance
    if (abs(saturation) > 0.001 || abs(u_vibrance) > 0.001) {
        vec3 l3 = srgbToLinear(enc);
        float y3 = luma(l3);
        float factor = 1.0 + saturation;
        if (abs(u_vibrance) > 0.001) {
            vec3 hsv = rgb2hsv(clamp(enc, 0.0, 1.0));
            float skin = 1.0 - 0.6 * smoothstep(25.0, 0.0, abs(hueDist(hsv.x * 360.0, 28.0)));
            float muted = (1.0 - hsv.y) * (1.0 - hsv.y);
            factor *= 1.0 + u_vibrance * muted * (u_vibrance > 0.0 ? skin : 1.0) * 1.2;
        }
        enc = linearToSrgb(gamutClip(y3 + (l3 - y3) * factor));
    }

    // ---------------------------------------------------------------- smart tools
    if (u_sky > 0.0) {
        vec3 hsv = rgb2hsv(clamp(enc, 0.0, 1.0));
        float hueW = smoothstep(42.0, 8.0, abs(hueDist(hsv.x * 360.0, 215.0)));
        float m = hueW * smoothstep(0.06, 0.22, hsv.y) * smoothstep(0.25, 0.55, hsv.z) * (1.0 - smoothstep(0.35, 0.85, uv.y));
        float a = u_sky * m;
        float Pc = perceptualLuma(enc);
        float dCla = 0.2 * tanh((srcP - p3.a) / 0.2);
        enc = withPerceptualLuma(enc, Pc, Pc * (1.0 - 0.18 * a) + dCla * 1.2 * a);
        vec3 l4 = srgbToLinear(enc);
        float y4 = luma(l4);
        enc = linearToSrgb(gamutClip(y4 + (l4 - y4) * (1.0 + 0.55 * a)));
    }
    if (u_golden > 0.0) {
        float Pc = perceptualLuma(enc);
        vec3 warm = vec3(1.0, 0.62, 0.25);
        float wy = luma(warm);
        float w = smoothstep(0.15, 0.85, Pc) * u_golden;
        enc += (warm - wy) * 0.16 * w;
        vec3 l5 = srgbToLinear(max(enc, 0.0)) * wbGains(0.35 * u_golden, 0.05 * u_golden);
        enc = linearToSrgb(gamutClip(l5));
    }
    if (u_skin > 0.0) {
        vec3 hsv = rgb2hsv(clamp(enc, 0.0, 1.0));
        float m = smoothstep(26.0, 6.0, abs(hueDist(hsv.x * 360.0, 24.0)))
            * smoothstep(0.08, 0.2, hsv.y) * (1.0 - smoothstep(0.65, 0.85, hsv.y)) * smoothstep(0.15, 0.35, hsv.z);
        float detail = srcP - p2.a;
        float keepEdges = 1.0 - smoothstep(0.05, 0.14, abs(detail));
        float a = u_skin * m * keepEdges;
        // With a person mask, keep the smoothing on the person (not on wood, sand or walls).
        if (u_hasPersonMask > 0.5) a *= smoothstep(0.2, 0.6, personAt(uv));
        float Pc = perceptualLuma(enc);
        float smoothP = Pc - detail * a * 0.9 + (p1.a - srcP) * a * 0.3;
        enc = withPerceptualLuma(enc, Pc, smoothP + 0.02 * a);
    }

    // ---------------------------------------------------------------- colour mixer
    if (u_mixEnabled > 0.5) {
        vec3 hsv = rgb2hsv(clamp(enc, 0.0, 1.0));
        float h = hsv.x * 360.0;
        float chromaW = smoothstep(0.02, 0.22, hsv.y) * smoothstep(0.0, 0.12, hsv.z);
        if (chromaW > 0.0) {
            vec4 w0;
            vec4 w1;
            bandWeights(h, w0, w1);
            float dh = dot(w0, u_mixHue[0]) + dot(w1, u_mixHue[1]);
            float ds = dot(w0, u_mixSat[0]) + dot(w1, u_mixSat[1]);
            float dl = dot(w0, u_mixLum[0]) + dot(w1, u_mixLum[1]);
            float nh = h + dh * chromaW;
            float ns = clamp(hsv.y * (1.0 + ds * chromaW), 0.0, 1.0);
            enc = hsv2rgb(vec3(fract(nh / 360.0 + 1.0), ns, hsv.z));
            if (abs(dl) > 1.0e-5) {
                enc = linearToSrgb(gamutClip(srgbToLinear(enc) * exp2(dl * chromaW * hsv.y)));
            }
        }
    }

    // ---------------------------------------------------------------- colour grading
    if (u_gradeEnabled > 0.5) {
        float L = luma(clamp(enc, 0.0, 1.0));
        float pivot = 0.5 + u_gradeBalance.y * 0.3;
        float width = mix(0.12, 0.5, u_gradeBalance.x);
        float wh = smoothstep(pivot - width, pivot + width, L);
        float ws = 1.0 - wh;
        float wm = exp(-pow((L - pivot) / (width * 0.9 + 0.08), 2.0));
        enc += u_gradeShadow.rgb * ws + u_gradeMid.rgb * wm + u_gradeHigh.rgb * wh + u_gradeGlobal.rgb;
        float lumAdj = u_gradeShadow.a * ws + u_gradeMid.a * wm + u_gradeHigh.a * wh + u_gradeGlobal.a;
        enc = linearToSrgb(gamutClip(srgbToLinear(max(enc, 0.0)) * exp2(lumAdj * 2.0)));
    }

    // ---------------------------------------------------------------- tone curve
    if (u_curvesEnabled > 0.5) {
        enc = vec3(curveLookup(enc.r, 0), curveLookup(enc.g, 1), curveLookup(enc.b, 2));
    }

    // ---------------------------------------------------------------- sharpening
    if (u_sharpen.x > 0.0) {
        vec2 r = u_texel * u_sharpen.y;
        float c = srcP;
        float n = perceptualLuma(texture(u_src, uv + vec2(0.0, -r.y)).rgb);
        float s = perceptualLuma(texture(u_src, uv + vec2(0.0, r.y)).rgb);
        float e = perceptualLuma(texture(u_src, uv + vec2(r.x, 0.0)).rgb);
        float w = perceptualLuma(texture(u_src, uv + vec2(-r.x, 0.0)).rgb);
        float ne = perceptualLuma(texture(u_src, uv + vec2(r.x, -r.y)).rgb);
        float nw = perceptualLuma(texture(u_src, uv + vec2(-r.x, -r.y)).rgb);
        float se = perceptualLuma(texture(u_src, uv + vec2(r.x, r.y)).rgb);
        float sw = perceptualLuma(texture(u_src, uv + vec2(-r.x, r.y)).rgb);
        float blur = (c * 4.0 + (n + s + e + w) * 2.0 + (ne + nw + se + sw)) / 16.0;
        float d = c - blur;
        float damp = 1.0 - u_sharpen.z;
        d = d / (1.0 + abs(d) * damp * 25.0);
        float edge = length(vec2(e - w, s - n));
        float mask = u_sharpen.w > 0.0 ? smoothstep(u_sharpen.w * 0.08, u_sharpen.w * 0.08 + 0.03, edge) : 1.0;
        float Pc = perceptualLuma(enc);
        enc = withPerceptualLuma(enc, Pc, Pc + u_sharpen.x * 2.2 * d * mask);
    }

    o_color = vec4(clamp(enc, 0.0, 1.0), 1.0);
}

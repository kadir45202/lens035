#version 300 es
// Healing: each spot is covered with texture from its source patch. The patch is shifted in
// colour so it meets the blemish's surroundings: around the rim the shift follows the local
// difference (no visible seam), towards the centre it eases to the average difference.
precision highp float;

in vec2 v_uv;
out vec4 o_color;

uniform sampler2D u_tex;
uniform vec2 u_texSize;      // pixels
uniform int u_healCount;
uniform vec4 u_healA[24];    // destination centre (uv), radius (fraction of shorter side), feather
uniform vec4 u_healB[24];    // source centre (uv)

const int RING = 12;

void main() {
    vec2 uv = v_uv;
    vec3 col = texture(u_tex, uv).rgb;
    float shortSide = min(u_texSize.x, u_texSize.y);
    for (int i = 0; i < 24; i++) {
        if (i >= u_healCount) break;
        vec4 a = u_healA[i];
        vec2 src = u_healB[i].xy;
        float rPx = a.z * shortSide;
        vec2 dPx = (uv - a.xy) * u_texSize;
        float dist = length(dPx);
        if (dist >= rPx) continue;

        // Colour difference between the two surroundings, sampled just outside the circle.
        vec3 diffs[RING];
        vec3 meanDiff = vec3(0.0);
        for (int k = 0; k < RING; k++) {
            float ang = float(k) * (6.2831853 / float(RING));
            vec2 o = vec2(cos(ang), sin(ang)) * (rPx * 1.2) / u_texSize;
            diffs[k] = texture(u_tex, a.xy + o).rgb - texture(u_tex, src + o).rgb;
            meanDiff += diffs[k];
        }
        meanDiff /= float(RING);

        // The rim difference in this pixel's direction, between the two nearest ring samples.
        float ang = atan(dPx.y, dPx.x);
        if (ang < 0.0) ang += 6.2831853;
        float fk = ang / (6.2831853 / float(RING));
        int k0 = int(floor(fk)) % RING;
        int k1 = (k0 + 1) % RING;
        vec3 rim = mix(diffs[k0], diffs[k1], fract(fk));
        float t = dist / rPx;
        vec3 shift = mix(meanDiff, rim, t * t);

        vec3 cloned = texture(u_tex, uv + (src - a.xy)).rgb + shift;
        // Colour already meets the rim through the shift, so the texture hand-over can be
        // narrow: a wide cross-fade of two grain patterns shows as a soft ring.
        float inner = rPx * (1.0 - 0.45 * a.w);
        float w = 1.0 - smoothstep(inner, rPx, dist);
        col = mix(col, clamp(cloned, 0.0, 1.0), w);
    }
    o_color = vec4(col, 1.0);
}

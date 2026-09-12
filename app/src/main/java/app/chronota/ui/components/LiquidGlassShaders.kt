/*
   Copyright 2025 Kyant

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package app.chronota.ui.components
// Adapted for chronota: the rim lens of the glass, without the chromatic dispersion.
// Source: Kyant0/AndroidLiquidGlass, kmp/backdrop/.../internal/Shaders.kt (Apache-2.0).

private const val RoundedRectSDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}"""

/**
 * The rim of the glass, as a lens rather than a window.
 *
 * `coord` is the padded layer's, `offset` brings it back to the surface's own coordinates, so the
 * distance to the outline is measured on the surface the user sees. A plain blur leaves the middle
 * of the surface untouched — only the band within `rimHeight` of the outline is bent, by
 * `rimBend` at the very edge falling to nothing at the band's inner side, along the outline's
 * normal. That inward pull is what magnifies what is behind the edge and tells the eye the surface
 * has a thickness. `depthEffect` leans the normal towards the rim's own axis, so the bend keeps
 * pointing outwards around a pill's ends and a disc's whole circumference.
 */
internal const val RoundedRectRefractionShaderString = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float rimHeight;
uniform float rimBend;
uniform float depthEffect;

$RoundedRectSDF

half4 main(float2 coord) {
    float2 local = coord + offset;
    float2 halfSize = size * 0.5;
    float2 centered = local - halfSize;
    float radius = radiusAt(local, cornerRadii);

    float sd = sdRoundedRect(centered, halfSize, radius);
    if (sd <= -rimHeight) {
        return content.eval(coord);
    }
    float t = clamp(-sd / rimHeight, 0.0, 1.0);
    float bend = (1.0 - sqrt(max(0.0, 1.0 - t * t))) * -rimBend;

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centered, halfSize, gradRadius) + depthEffect * normalize(centered + 0.0001));

    return content.eval(coord + bend * grad);
}"""

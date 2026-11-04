#version 330
#moj_import <minecraft:dynamictransforms.glsl>
uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;
float coverage(vec2 uv) {
#ifdef INTENSITY
    return texture(Sampler0, uv).r;
#else
    return texture(Sampler0, uv).a;
#endif
}
void main() {
    vec4 ink = texture(Sampler0, texCoord0);
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    float c = coverage(texCoord0);
    // A subpixel dark contour supplies contrast on bright skies without a shadow or rectangle.
    float rim = max(max(coverage(texCoord0 + vec2(texel.x, 0)), coverage(texCoord0 - vec2(texel.x, 0))),
                    max(coverage(texCoord0 + vec2(0, texel.y)), coverage(texCoord0 - vec2(0, texel.y))));
    float edge = 0.7 * max(0.0, rim - c) * (1.0 - c);
    float alpha = c + edge;
    if (alpha < 0.015) discard;
#ifdef INTENSITY
    vec3 rgb = vertexColor.rgb;
#else
    vec3 rgb = ink.rgb * vertexColor.rgb;
#endif
    fragColor = vec4((rgb * c + vec3(0.025) * edge) / alpha, alpha * vertexColor.a) * ColorModulator;
}

package dev.simulated_team.simulated.port.assets;

import java.nio.charset.StandardCharsets;

/**
 * PORT-26.3: the hot air burner flame's 26.3 core shaders ({@code assets/aeronautics/shaders/core/burner_flame.*}),
 * built from the official Veil program {@code assets/aeronautics/pinwheel/shaders/program/burner_flame.{vsh,fsh}} and
 * its include {@code assets/aeronautics/pinwheel/shaders/include/util.glsl}. The official functions and {@code main()}
 * bodies are kept. This class supplies the 26.3 frame:
 * <ul>
 * <li>GLSL version, Minecraft's uniform blocks, attribute and varying locations;</li>
 * <li>Veil's per-draw uniforms read from the vertex colour instead (26.3 has no per-draw float uniforms in a batched
 * render type): red is the animation time as a fraction of its period, green the intensity, blue the palette row,
 * written by {@code HotAirBurnerRenderer}; {@code FirePalette} is the render type's {@code Sampler0};</li>
 * <li>26.3's two-part fog in place of Veil's linear fog, and the render type's colour modulator;</li>
 * <li>the flame's circle array filled before use and its index kept in range: the official loop can write slot
 * {@code count} and leave slots unwritten at wrap-around, which flashes stray circles on some drivers.</li>
 * </ul>
 */
final class BurnerFlameShaders {
    private BurnerFlameShaders() {}

    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n\n";
    private static final String PERIOD = "const float FLAME_ANIMATION_PERIOD = 10.0 / 3.0;\n";

    static byte[] vertex(final byte[] official) {
        String body = Shaders.mainBody(official);
        body = Shaders.replaceOnce(body, "fog_distance(ModelViewMat, Position, FogShape)", "length((ModelViewMat * vec4(Position, 1.0)).xyz)");
        return (HEADER
                + "#include <minecraft:dynamictransforms.glsl>\n#include <minecraft:projection.glsl>\n\n"
                + "// POSITION_TEX_COLOR: attribute locations follow the format's element order\n"
                + "layout(location = 0) in vec3 Position;\nlayout(location = 1) in vec2 UV0;\nlayout(location = 2) in vec4 Color;\n\n"
                + "layout(location = 0) out float vertexDistance;\nlayout(location = 1) out vec2 texCoord0;\nlayout(location = 2) flat out vec3 flameData;\n\n"
                + "void main() {" + body + "    flameData = Color.rgb;\n}\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] fragment(final byte[] official, final byte[] util) {
        String s = new String(official, StandardCharsets.UTF_8);
        s = Shaders.dropLinesStarting(s, "#include ");
        s = Shaders.dropLines(s, "uniform sampler2D FirePalette;", "uniform float FlameRenderTime;", "uniform float Intensity;", "uniform float Palette;",
                "uniform float FogStart;", "uniform float FogEnd;", "uniform vec4 FogColor;", "in float vertexDistance;", "in vec2 texCoord0;", "out vec4 fragColor;");
        s = Shaders.replaceOnce(s, "vec2[count] circle_positions;",
                "vec2[count] circle_positions;\n    for (int k = 0; k < count; k++) {\n        circle_positions[k] = vec2(0.0, -10.0);\n    }");
        s = Shaders.replaceOnce(s, "index = clamp(index, 0, count);", "index = clamp(index, 0, count - 1);");
        s = Shaders.replaceOnce(s, "fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);",
                "color *= ColorModulator;\n"
                        + "    float environmentalFog = linear_fog_value(vertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd);\n"
                        + "    float renderDistanceFog = linear_fog_value(vertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd);\n"
                        + "    fragColor = vec4(mix(color.rgb, FogColor.rgb, max(environmentalFog, renderDistanceFog) * FogColor.a), color.a);");
        return (HEADER
                + "#include <minecraft:dynamictransforms.glsl>\n#include <minecraft:fog.glsl>\n\n"
                + "uniform sampler2D Sampler0;\n\n"
                + "layout(location = 0) in float vertexDistance;\nlayout(location = 1) in vec2 texCoord0;\nlayout(location = 2) flat in vec3 flameData;\n\n"
                + "layout(location = 0) out vec4 fragColor;\n\n"
                + PERIOD
                + "#define FirePalette Sampler0\n#define FlameRenderTime (flameData.r * FLAME_ANIMATION_PERIOD)\n"
                + "#define Intensity (flameData.g)\n#define Palette (flameData.b)\n\n"
                + new String(util, StandardCharsets.UTF_8) + "\n"
                + s).getBytes(StandardCharsets.UTF_8);
    }
}

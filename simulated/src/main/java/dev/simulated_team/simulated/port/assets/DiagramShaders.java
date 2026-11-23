package dev.simulated_team.simulated.port.assets;

import java.nio.charset.StandardCharsets;

/**
 * PORT-26.3: the diagram preview's outline pass ({@code assets/simulated/shaders/core/diagram_outline.fsh}), built from
 * the official Veil program {@code assets/simulated/pinwheel/shaders/program/contraption_diagram/outline_diagram.fsh}.
 * The official text is kept; this class supplies the 26.3 frame: GLSL version, locations for the input and output,
 * Veil's four uniforms as shader defines (one pipeline per caller: {@code LINE_COLOR} and {@code LINE_SHADOW_COLOR}
 * as 0xRRGGBB, {@code PALETTE_OFFSET}, {@code FADE_SCALE}), the texel size computed in place of Veil's
 * {@code oneTexel} varying, and depth read as {@code 1 - depth} (26.3 clears depth to 0 and keeps the nearest at 1).
 */
final class DiagramShaders {
    private DiagramShaders() {}

    private static final String HEADER = """
            #version 330
            #extension GL_ARB_separate_shader_objects : require

            const vec4 LineColor = vec4(float((LINE_COLOR >> 16) & 255) / 255.0, float((LINE_COLOR >> 8) & 255) / 255.0, float(LINE_COLOR & 255) / 255.0, 1.0);
            const vec4 LineShadowColor = vec4(float((LINE_SHADOW_COLOR >> 16) & 255) / 255.0, float((LINE_SHADOW_COLOR >> 8) & 255) / 255.0, float(LINE_SHADOW_COLOR & 255) / 255.0, 1.0);
            const float PaletteOffset = PALETTE_OFFSET;
            const float FadeScale = FADE_SCALE;
            #define oneTexel (1.0 / vec2(textureSize(DiffuseSampler0, 0)))

            layout(location = 0) in vec2 texCoord;
            layout(location = 0) out vec4 fragColor;

            """;

    private static final String DEPTH = "uniform sampler2D DiffuseDepthSampler;";

    static byte[] fragment(final byte[] official) {
        String s = new String(official, StandardCharsets.UTF_8);
        s = Shaders.dropLines(s, "uniform vec4 LineColor;", "uniform vec4 LineShadowColor;", "uniform float PaletteOffset;", "uniform float FadeScale;",
                "in vec2 texCoord;", "in vec2 oneTexel;", "out vec4 fragColor;");
        // every depth read goes through sceneDepth (vec4 keeps the official .r swizzles and vec4 locals valid)
        s = Shaders.replaceCalls(s, "texture(DiffuseDepthSampler, ", "vec4(sceneDepth(", "))", 6);
        s = Shaders.require(s, DEPTH).replace(DEPTH, DEPTH + "\n\nfloat sceneDepth(vec2 uv) {\n    return 1.0 - texture(DiffuseDepthSampler, uv).r;\n}");
        return (HEADER + s).getBytes(StandardCharsets.UTF_8);
    }
}

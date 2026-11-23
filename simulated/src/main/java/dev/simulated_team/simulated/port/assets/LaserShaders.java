package dev.simulated_team.simulated.port.assets;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PORT-26.3: the laser's 26.3 core shaders, built from the official Veil shader programs
 * ({@code assets/simulated/pinwheel/shaders/program/laser/laser.{vsh,fsh}}). The bodies of {@code main()} are the
 * official ones; this class supplies the 26.3 frame around them (GLSL version, Minecraft's uniform blocks, attribute
 * and varying locations, 26.3's two-part fog in place of Veil's linear fog).
 */
final class LaserShaders {
    private LaserShaders() {}

    private static final String HEADER = "#version 330\n#extension GL_ARB_separate_shader_objects : require\n\n";

    static byte[] vertex(final byte[] official) {
        final String body = main(official).replaceAll("\\bUV\\b", "UV0");
        return (HEADER
                + "#include <minecraft:dynamictransforms.glsl>\n#include <minecraft:projection.glsl>\n\n"
                + "// POSITION_TEX_COLOR: attribute locations follow the format's element order\n"
                + "layout(location = 0) in vec3 Position;\nlayout(location = 1) in vec2 UV0;\nlayout(location = 2) in vec4 Color;\n\n"
                + "layout(location = 0) out float vertexDistance;\nlayout(location = 1) out vec2 lengthData;\nlayout(location = 2) out vec4 vertexColor;\n\n"
                + "void main() {" + body + "}\n").getBytes(StandardCharsets.UTF_8);
    }

    static byte[] fragment(final byte[] official) {
        String body = main(official).replaceAll("(?m)^[ \\t]*// #veil:.*\\R", "");
        final String fog = "linear_fog_fade(vertexDistance, FogStart, FogEnd)";
        if (!body.contains(fog)) throw new IllegalStateException("official laser.fsh has no linear fog call");
        body = body.replace(fog, "fogFade");
        final int last = body.lastIndexOf("fragColor");
        final int lineStart = body.lastIndexOf('\n', last) + 1;
        final String indent = body.substring(lineStart, last);
        body = body.substring(0, lineStart)
                + indent + "float environmentalFog = linear_fog_value(vertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd);\n"
                + indent + "float renderDistanceFog = linear_fog_value(vertexDistance, FogRenderDistanceStart, FogRenderDistanceEnd);\n"
                + indent + "float fogFade = 1.0 - max(environmentalFog, renderDistanceFog);\n"
                + body.substring(lineStart);
        return (HEADER
                + "#include <minecraft:dynamictransforms.glsl>\n#include <minecraft:fog.glsl>\n\n"
                + "layout(location = 0) in float vertexDistance;\nlayout(location = 1) in vec2 lengthData;\nlayout(location = 2) in vec4 vertexColor;\n\n"
                + "layout(location = 0) out vec4 fragColor;\n\n"
                + "void main() {" + body + "}\n").getBytes(StandardCharsets.UTF_8);
    }

    /** The text between the braces of {@code void main()}. */
    private static String main(final byte[] src) {
        final String s = new String(src, StandardCharsets.UTF_8);
        final Matcher m = Pattern.compile("void\\s+main\\s*\\(\\s*\\)\\s*\\{").matcher(s);
        if (!m.find()) throw new IllegalStateException("no main() in the official laser shader");
        int depth = 1, i = m.end();
        for (; i < s.length() && depth > 0; i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') depth--;
        }
        return s.substring(m.end(), i - 1);
    }
}

#version 330
#extension GL_ARB_separate_shader_objects : require

// PORT-26.3: the screen triangle of core/screenquad, plus the world block light floor for this copy, which arrives as
// the draw's first vertex (three vertices per floor level, see SubLevelLightmaps.prepare).
layout(location = 0) out vec2 texCoord;
layout(location = 1) flat out int blockFloor;

void main() {
    int corner = gl_VertexIndex % 3;
    vec2 uv = vec2((corner << 1) & 2, corner & 2);
    gl_Position = vec4(uv * vec2(2, 2) + vec2(-1, -1), 0, 1);
    texCoord = uv;
    blockFloor = gl_VertexIndex / 3;
}

#version 330
#extension GL_ARB_separate_shader_objects : require

// PORT-26.3: a sub-level's lightmap. The game's lightmap is 16x16 with block light along x and sky light along y;
// this copy takes sky row y from row floor(y * SKY_SCALE / 15), so blocks on a sub-level under cover lose their sky
// light as the sub-level's own sky-light scale says, while their block light (torches aboard) is unchanged.
// The world's block light where the sub-level is drawn (blockFloor, 0 to 15) is a floor on the block axis, taken in
// proportion to the plot's sky light at the vertex (y): an exposed face gets all of it, a closed room none. With
// blockFloor 0 the copy is the sky remap alone.
uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in int blockFloor;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    int sky = (p.y * SKY_SCALE) / 15;
    int block = max(p.x, (blockFloor * p.y) / 15);
    fragColor = texelFetch(InSampler, ivec2(block, sky), 0);
}

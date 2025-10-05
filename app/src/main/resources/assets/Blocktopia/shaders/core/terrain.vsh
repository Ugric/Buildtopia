#version 330 core
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in float aBlockLight; // NEW
layout(location = 3) in float aSunLight;   // NEW

out vec2 TexCoord;
out float BlockLight;  // pass to fragment shader
out float SunLight;    // pass to fragment shader

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
    BlockLight = aBlockLight; // pass through
    SunLight = aSunLight;     // pass through
}
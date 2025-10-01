#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in float BlockLight;
in float SunLight;

uniform sampler2D texture1;
uniform float dayNight;

void main() {
    vec4 texColor = texture(texture1, TexCoord);
    float ambient = 0.1;
    float light = max(BlockLight, SunLight * dayNight);
    light = max(light, ambient);
    FragColor = texColor * light;
}
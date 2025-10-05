#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in float BlockLight;
in float SunLight;

uniform sampler2D texture1;
uniform float dayNight;

void main() {
    vec3 normalLight = vec3(255,248,215)/255;
    vec3 day = vec3(255,255,255)/255;
    vec3 night = vec3(42,42,71)/255;
    vec3 ambient = vec3(14.0,14.0,14.0)/255;
    vec4 texColor = texture(texture1, TexCoord);
    float howNight = 1.0-dayNight;
    vec3 light = ambient+clamp((night*howNight+day*dayNight)*SunLight+normalLight*BlockLight-ambient, 0.0,1.0);
    light = clamp(light, 0.0, 1.0);
    FragColor = texColor * vec4(light, 0.0);
}
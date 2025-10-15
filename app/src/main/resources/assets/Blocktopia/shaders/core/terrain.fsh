#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in float BlockLight;
in float SunLight;

uniform sampler2D texture1;
uniform float dayNight;

void main() {
    float brightness = 1.0; // 0 = default, 1 = Java-style maximum shadow lift

    vec3 normalLight = vec3(255,248,215)/255.0;
    vec3 day = vec3(255,255,255)/255.0;
    vec3 night = vec3(42,42,71)/255.0;
    vec3 ambient = vec3(14,14,14)/255.0;

    vec4 texColor = texture(texture1, TexCoord);

    float howNight = 1.0 - dayNight;

    // base lighting
    vec3 light = ambient + clamp(
    (night * howNight + day * dayNight) * SunLight +
    normalLight * BlockLight - ambient,
    0.0, 1.0
    );

    // Java-style brightness lift (shadows only)
    vec3 shadowLift = vec3(brightness * 0.1); // strength of shadow lift
    vec3 factor = clamp(0.5 - light, 0.0, 1.0); // only affects darker areas
    light = light + shadowLift * factor;

    // Apply lighting to RGB but preserve alpha from texture
    FragColor = vec4(texColor.rgb * light, texColor.a);
}
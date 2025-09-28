plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"

    // Apply the Application plugin to add support for building an executable JVM application.
    application
}
val lwjglVersion = "3.3.6"

dependencies {
    implementation(project(":utils"))

    implementation("org.joml:joml:1.10.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Core LWJGL modules
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-stb:$lwjglVersion")

    // Natives for all platforms
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-macos")

    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-macos")

    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-macos")

    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb:$lwjglVersion:natives-macos")
}

application {
    // Fully Qualified Name of your main function's class
    mainClass.set("dev.wbell.buildtopia.app.AppKt")
}


tasks {
    shadowJar {
        archiveBaseName.set("app")
        archiveClassifier.set("")   // so it produces app.jar, not app-all.jar
        archiveVersion.set("")      // omit version in filename
        manifest {
            attributes["Main-Class"] = "dev.wbell.buildtopia.app.AppKt"
        }
    }
}
plugins {
    `java-library`
}

/*
 * Speech recognition on the device (formerly the Whisper app): queue, Whisper/Parakeet via sherpa-onnx, voice activity
 * detection, speakers, search. Plain Java, shared by Android (app) and Windows (desktop compiles these sources too).
 */
dependencies {
    // Java API of sherpa-onnx (the same classes on Android and Windows; the native part comes per platform).
    api(files("libs/sherpa-onnx-jvm-1.13.8.jar"))
    // Android has org.json built in; Windows adds it itself.
    compileOnly("org.json:json:20250517")

    testImplementation("org.json:json:20250517")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}

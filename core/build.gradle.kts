plugins {
    id("org.jetbrains.kotlin.jvm")
}

val ortVersion = "1.30.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation("com.google.code.gson:gson:2.11.0")
    // The app supplies onnxruntime-android; tests use the desktop build. Same Java API.
    compileOnly("com.microsoft.onnxruntime:onnxruntime:$ortVersion")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:$ortVersion")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    maxHeapSize = "6g"
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    environment("MODEL_DIR", System.getenv("MODEL_DIR") ?: "")
    environment("REFERENCE_JSON", System.getenv("REFERENCE_JSON") ?: "")
}

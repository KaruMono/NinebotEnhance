plugins { id("com.android.application") }

android {
    namespace = "dev.ichinomiya.ninebotcast"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.ichinomiya.ninebotcast"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

val compileHostTests by tasks.registering(JavaCompile::class) {
    source("src/main/java", "../tests/cast")
    classpath = files(android.bootClasspath)
    destinationDirectory.set(layout.buildDirectory.dir("host-test-classes"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
}
val hostTest by tasks.registering(JavaExec::class) {
    dependsOn(compileHostTests)
    classpath = files(compileHostTests.flatMap { it.destinationDirectory }) + files(android.bootClasspath)
    mainClass.set("dev.ichinomiya.ninebotcast.CastTests")
    systemProperty("file.encoding", "UTF-8")
}
tasks.named("check") { dependsOn(hostTest) }

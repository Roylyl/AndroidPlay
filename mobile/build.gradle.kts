plugins {
    alias(libs.plugins.android.application)
}

// CI explicitly builds without credentials; normal local builds still require them.
val sourceOnlyBuild = providers.gradleProperty("androidplay.sourceOnly").orNull == "true"

// Android Studio Run and CLI builds use the same local authentication input.
val localAuthenticationAssets = if (sourceOnlyBuild) null else providers.environmentVariable("ANDROIDPLAY_AUTH_ASSETS_DIR")
    .orNull?.let { file(it).canonicalFile }
    ?: file(System.getProperty("user.home") + "/Library/Application Support/AndroidPlay/runtime-assets").canonicalFile

android {
    namespace = "com.shilapi.xcertplay"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.androidplay.app"
        minSdk = 28
        // API36+ overrides landscape restrictions on large displays.
        targetSdk = 35
        versionCode = 37
        versionName = "1.0.0"

    }


    localAuthenticationAssets?.let { sourceSets.getByName("main").assets.srcDir(it) }

    signingConfigs {
        create("release") {
            storeFile = file(
                providers.environmentVariable("ANDROID_KEYSTORE_PATH")
                    .getOrElse("missing-release-keystore.jks"),
            )
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").getOrElse("")
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").getOrElse("")
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").getOrElse("")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":shared"))
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
}

// Only the two files in the selected local runtime directory are allowed.
val credentialAssets = files(android.sourceSets.flatMap { source ->
    source.assets.directories.map { directory ->
        fileTree(directory) {
            include("**/offline-mfi/**", "**/*.pk8", "**/*.p7b", "**/*.key",
                "**/*.pem", "**/*.p12", "**/*.pfx", "**/*.jks", "**/*.keystore")
        }
    }
})
val rejectBundledCredentials by tasks.registering {
    group = "verification"
    description = "Reject unexpected credential files in APK assets."
    val filesToCheck = credentialAssets
    val requiresAuthentication = !sourceOnlyBuild
    val allowed = localAuthenticationAssets?.let { dir ->
        listOf("identity.pk8", "certificate.p7b").map { dir.resolve("offline-mfi/$it").canonicalFile }.toSet()
    } ?: emptySet()
    inputs.files(filesToCheck)
    doLast {
        check(!requiresAuthentication || allowed.all { it.isFile && it.length() > 0 }) { "CarPlay认证文件缺失：请配置ANDROIDPLAY_AUTH_ASSETS_DIR，或放入本机Library/Application Support/AndroidPlay/runtime-assets/offline-mfi目录。" }
        val unexpected = filesToCheck.files.filter { it.canonicalFile !in allowed }
        check(unexpected.isEmpty()) { "Unexpected credential files in APK assets" }
    }
}
tasks.named("preBuild") { dependsOn(rejectBundledCredentials) }

// All installable builds require the locally provisioned identity.
val verifyStandaloneAuthentication by tasks.registering {
    group = "verification"
    description = "Require the explicit runtime authentication input for a standalone car-test APK."
    val directory = localAuthenticationAssets
    doLast {
        check(directory != null) {
            "Standalone car builds require authentication; remove -Pandroidplay.sourceOnly=true and configure ANDROIDPLAY_AUTH_ASSETS_DIR."
        }
        check(listOf("identity.pk8", "certificate.p7b").all {
            directory.resolve("offline-mfi/$it").let { file -> file.isFile && file.length() > 0 }
        }) { "Standalone CarPlay authentication files are missing or empty" }
    }
}
tasks.named("preBuild") { mustRunAfter(verifyStandaloneAuthentication) }
tasks.register("assembleStandaloneDebug") {
    group = "build"
    description = "Build a standalone car-test APK with explicitly provisioned authentication."
    dependsOn(verifyStandaloneAuthentication, "assembleDebug")
}

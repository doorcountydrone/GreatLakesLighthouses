import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun loadGithubKeystore(): Properties? {
    val file = rootProject.file("keystore.properties")
    if (!file.exists()) return null
    val props = Properties()
    file.inputStream().use { props.load(it) }
    val store = props.getProperty("storeFile")?.trim().orEmpty()
    if (store.isEmpty() || !rootProject.file(store).isFile) return null
    if (props.getProperty("storePassword").isNullOrBlank()) return null
    if (props.getProperty("keyAlias").isNullOrBlank()) return null
    if (props.getProperty("keyPassword").isNullOrBlank()) return null
    return props
}

android {
    namespace = "com.doorcountylighthouses"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.doorcountylighthouses"
        minSdk = 30
        targetSdk = 34
        versionCode = 15
        versionName = "1.14"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val mapsApiKey = loadMapsApiKey()
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_KEY", if (mapsApiKey.isNotEmpty()) "true" else "false")
    }

    val githubKeys = loadGithubKeystore()
    if (githubKeys != null) {
        signingConfigs {
            create("github") {
                storeFile = rootProject.file(githubKeys.getProperty("storeFile"))
                storePassword = githubKeys.getProperty("storePassword")
                keyAlias = githubKeys.getProperty("keyAlias")
                keyPassword = githubKeys.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (githubKeys != null) {
                signingConfig = signingConfigs.getByName("github")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (githubKeys != null) {
                signingConfig = signingConfigs.getByName("github")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

// Older AndroidX pulls kotlin-stdlib-jdk7/8 1.8.20; align to a cached 1.9.20.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" &&
            requested.name in setOf("kotlin-stdlib-jdk7", "kotlin-stdlib-jdk8")
        ) {
            useVersion("1.9.20")
        }
    }
}

fun loadMapsApiKey(): String {
    val files = listOf(
        rootProject.file("secrets.properties"),
        rootProject.file("local.properties"),
    )
    for (file in files) {
        if (!file.exists()) continue
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val key = props.getProperty("MAPS_API_KEY")?.trim().orEmpty()
        if (key.isNotEmpty()) return key
    }
    return ""
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// One number, read from gradle.properties, turned into every other form of itself.
val appVersion = (project.findProperty("appVersion") as String? ?: "1").trim().toInt()
// DM Finder's own number (2.11.2026), in the same file, by the same rules.
val dmVersion = (project.findProperty("dmVersion") as String? ?: "1").trim().toInt()

// THE GITHUB EDITION (Marko, 2.11.2026: "build it there with different signature, I will have 2
// different apps"). -PghBuild gives it its own id and name, so it installs beside the Mac's build,
// signed with GitHub's own key (secret SPAR_GH_KEYSTORE). Without the flag nothing changes.
val ghBuild = project.hasProperty("ghBuild")

// The signing key is a repository secret restored by the workflow (android-app.md §3,
// updatable.md). Locally the file is absent and the release build falls back to unsigned.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// THE VERSION IN THE ICON (version-visible.md §2), copied from Mantra Manual Camera's
// app/build.gradle.kts. A vector drawable has no text, so the number is drawn as segment strokes
// under the symbol, inside the 42-unit symbol box (app-icon.md). The template is src/main/icon;
// the icon the app ships is generated from it here, never edited per version.
// One generated folder per flavour: Spar's template (src/main/icon) with Spar's number, dm's
// (src/dm/icon) with dm's.
fun versionIcon(flavour: String, version: Int, templatePath: String): File {
    val versionIconDir = layout.buildDirectory.dir("generated/versionIcon/$flavour").get().asFile
    val segments = mapOf(
        'a' to "M0,0 H5", 'b' to "M5,0 V5", 'c' to "M5,5 V10", 'd' to "M0,10 H5",
        'e' to "M0,5 V10", 'f' to "M0,0 V5", 'g' to "M0,5 H5"
    )
    val digits = mapOf(
        '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
        '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg"
    )
    val text = "v$version"
    val cell = 5.0
    val gap = 2.2
    val width = text.length * cell + (text.length - 1) * gap
    val left = 54.0 - width / 2
    val top = 64.0
    val paths = StringBuilder()
    text.forEachIndexed { i, ch ->
        val x = left + i * (cell + gap)
        val data = if (ch == 'v') "M0,4 L2.5,10 L5,4"
            else digits[ch].orEmpty().map { segments.getValue(it) }.joinToString(" ")
        // Each glyph's path is written in its 5 x 10 cell and moved into place.
        val moved = Regex("([MLHV])([-0-9.]+)(,([-0-9.]+))?").replace(data) { m ->
            val cmd = m.groupValues[1]
            val a = m.groupValues[2].toDouble()
            val b = m.groupValues[4].takeIf { it.isNotEmpty() }?.toDouble()
            // Rounded by hand: a Double prints with a dot in every locale.
            fun n(v: Double) = (Math.round(v * 100) / 100.0).toString()
            when (cmd) {
                "H" -> "H" + n(a + x)
                "V" -> "V" + n(a + top)
                else -> cmd + n(a + x) + "," + n((b ?: 0.0) + top)
            }
        }
        paths.append("    <path android:strokeColor=\"#F2DDB4\" android:strokeWidth=\"1.5\" ")
            .append("android:strokeLineCap=\"round\" android:strokeLineJoin=\"round\" ")
            .append("android:pathData=\"").append(moved).append("\" />\n")
    }
    val template = file(templatePath).readText()
    val out = File(versionIconDir, "drawable/ic_launcher_foreground.xml")
    out.parentFile.mkdirs()
    out.writeText(template.replace("    <!--VERSION-->\n", "    <!-- v$version, generated -->\n$paths"))
    return versionIconDir
}
val sparIcon = versionIcon("spar", appVersion, "src/main/icon/ic_launcher_foreground.xml")
val dmIcon = versionIcon("dm", dmVersion, "src/dm/icon/ic_launcher_foreground.xml")

android {
    namespace = "com.mantra.spar"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
    }

    // TWO SHOPS, ONE APP (2.11.2026): the same map, pin and panels; each flavour brings its shop's
    // list, name, id, number and icon. The Mac's Spar Locator keeps com.mantra.spar.
    flavorDimensions += "shop"
    productFlavors {
        create("spar") {
            dimension = "shop"
            applicationId = if (ghBuild) "com.mantra.spar.gh" else "com.mantra.spar"
            manifestPlaceholders["appLabel"] = if (ghBuild) "Spar GH" else "@string/app_name"
            versionCode = appVersion
            versionName = appVersion.toString()
            buildConfigField("String", "SITE", "\"spar.hr\"")
            buildConfigField("String", "AGENT", "\"SparLocator\"")
            buildConfigField("String", "STORES_URL", "\"https://www.spar.hr/lokacije/_jcr_content.stores.v2.html\"")
            buildConfigField("String", "STORES_FILE", "\"spar_stores.json\"")
            buildConfigField("long", "BUILT_IN_AT", "1793551380000L")
            buildConfigField("String", "RELEASES", if (ghBuild)
                "\"https://github.com/markoboskoauroville/spar_locator/releases?q=spar-gh-v&expanded=true\""
                else "\"https://github.com/markoboskoauroville/spar_locator/releases/latest\"")
        }
        create("dm") {
            dimension = "shop"
            applicationId = if (ghBuild) "com.mantra.dmfinder.gh" else "com.mantra.dmfinder"
            manifestPlaceholders["appLabel"] = "@string/app_name"
            versionCode = dmVersion
            versionName = dmVersion.toString()
            buildConfigField("String", "SITE", "\"dm.hr\"")
            buildConfigField("String", "AGENT", "\"DMFinder\"")
            buildConfigField("String", "STORES_URL",
                "\"https://store-data-service.services.dmtech.com/stores/bbox/46.6,13.4,42.3,19.5?countryCode=HR\"")
            buildConfigField("String", "STORES_FILE", "\"dm_stores.json\"")
            // When the built-in list was read: 2.11.2026, 08:31 in Zagreb.
            buildConfigField("long", "BUILT_IN_AT", "1793604668000L")
            buildConfigField("String", "RELEASES", "\"https://github.com/markoboskoauroville/spar_locator/releases?q=dm-v&expanded=true\"")
        }
    }

    sourceSets {
        getByName("spar") { res.srcDirs(sparIcon) }
        getByName("dm") { res.srcDirs(dmIcon) }
    }

    signingConfigs {
        if (keystoreProperties.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProperties.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // G3: Lint blocking from the first build, as in Mantra ARKOD.
    lint {
        ignoreTestSources = false
        warningsAsErrors = true
        abortOnError = true
        checkReleaseBuilds = true
        htmlReport = false
        xmlReport = true
        // OldTargetApi (2.11.2026): lint learned of Android 16 (API 36); moving targetSdk is a
        // decision with its own test round, not something a lint update decides. Same kind as the two
        // "a newer version exists" checks beside it.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "ObsoleteLintCustomCheck", "OldTargetApi")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        allWarningsAsErrors = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")

    // THE MAP ENGINE: VTM, mapsforge's OpenGL renderer, the same one Mantra ARKOD draws with.
    // OpenStreetMap's raster tiles are drawn as textures on the GPU. LGPL-3.0. The natives are its
    // tile decoder, one library per architecture: arm64 is the phone, the other two so an APK
    // handed to somebody else still runs.
    implementation("org.mapsforge:vtm:0.25.0")
    implementation("org.mapsforge:vtm-android:0.25.0")
    runtimeOnly("org.mapsforge:vtm-android:0.25.0:natives-arm64-v8a")
    runtimeOnly("org.mapsforge:vtm-android:0.25.0:natives-armeabi-v7a")
    runtimeOnly("org.mapsforge:vtm-android:0.25.0:natives-x86_64")

    testImplementation("junit:junit:4.13.2")
    // The real org.json for Test 1: android.jar carries only stubs that throw, and Stores.kt reads
    // spar.hr's answer with it.
    testImplementation("org.json:json:20240303")
}

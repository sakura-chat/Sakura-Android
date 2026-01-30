plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.google.devtools.ksp)
	alias(libs.plugins.google.services)
	alias(libs.plugins.dagger.hilt.android)
	kotlin("plugin.serialization") version "2.2.0"
}

android {
	namespace = "dev.kuylar.sakura"
	compileSdk {
		version = release(36)
	}

	defaultConfig {
		applicationId = "dev.kuylar.sakura"
		minSdk = 33
		targetSdk = 36
		versionCode = 1
		versionName = "1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
			signingConfig = signingConfigs.getByName("debug")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlin {
		jvmToolchain(17)
	}
	buildFeatures {
		viewBinding = true
		buildConfig = true
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)
	implementation(libs.material)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.navigation.runtime.ktx)
	implementation(libs.androidx.navigation.fragment.ktx)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)

	implementation(libs.trixnity.client)
	implementation(libs.trixnity.client.repository.room)
	implementation(libs.trixnity.client.media.okio)
	implementation(libs.trixnity.client.cryptodriver.vodozemac)
	implementation(libs.lognity.api.android)
	implementation(libs.lognity.core.android)
	implementation(libs.ktor)
	implementation(libs.ktor.resources)
	implementation(libs.kotlinx.serialization.json)
	implementation(libs.recyclerviewbuilder)
	implementation(libs.glide)
	ksp(libs.glide.compiler)
	implementation(libs.overlappingpanels)
	implementation(libs.mentionsedittext)
	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.messaging)
	implementation(libs.emoji2)
	implementation(project(":emojipicker"))
	implementation(libs.dagger.hilt.android)
	ksp(libs.dagger.hilt.compiler)
	implementation(libs.commonmark)
	implementation(libs.avatarview)
	implementation(libs.jsoup)
	implementation(libs.kotlin.reflect)
}
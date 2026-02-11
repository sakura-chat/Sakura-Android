plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.google.devtools.ksp)
	alias(libs.plugins.dagger.hilt.android)
	alias(libs.plugins.kotlin.android)
}

android {
	namespace = "dev.kuylar.sakura.markdown"
	compileSdk {
		version = release(36)
	}

	defaultConfig {
		minSdk = 33

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		consumerProguardFiles("consumer-rules.pro")
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlin {
		jvmToolchain(17)
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)
	implementation(libs.material)
	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	implementation(libs.dagger.hilt.android)
	ksp(libs.dagger.hilt.compiler)
	implementation(libs.commonmark)
	implementation(libs.commonmark.ext.gfm.strikethrough)
	implementation(libs.commonmark.ext.autolink)
	implementation(libs.commonmark.ext.ins)
	implementation(libs.jsoup)
}
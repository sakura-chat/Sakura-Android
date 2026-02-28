pluginManagement {
	repositories {
		google {
			content {
				includeGroupByRegex("com\\.android.*")
				includeGroupByRegex("com\\.google.*")
				includeGroupByRegex("androidx.*")
			}
		}
		mavenCentral()
		gradlePluginPortal()
	}
}
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
		maven("https://jitpack.io")
		// https://gitlab.com/kuylar/trixnity
		maven("https://gitlab.com/api/v4/projects/79642625/packages/maven")
	}
}

rootProject.name = "Sakura"
include(":app")
include(":emojipicker")
include(":markdown")

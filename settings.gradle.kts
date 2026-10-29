pluginManagement { repositories { google(); mavenCentral(); maven { url = uri("/opt/maven-repo") }; gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Ninebot Enhance"
include(":app", ":cast")

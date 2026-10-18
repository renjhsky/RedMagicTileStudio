pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
 repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
 repositories {
  maven { url = uri(rootDir.resolve(".tools/maven")); metadataSources { mavenPom(); artifact() } }
  google(); mavenCentral()
 }
}
rootProject.name = "RedMagicTileStudio"
include(":app")

import java.util.Properties
import java.security.KeyStore
import java.security.MessageDigest

plugins { id("com.android.application") }
val appVersion = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val githubRepository = Properties().apply { rootProject.file("repository.properties").inputStream().use { load(it) } }.getProperty("githubRepository", "").trim()
check(githubRepository.isEmpty() || githubRepository.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]+"))) { "githubRepository 必须为 GitHub 的 owner/repository。" }
val releaseVariables = listOf("RMT_RELEASE_KEYSTORE", "RMT_RELEASE_STORE_PASSWORD", "RMT_RELEASE_KEY_ALIAS", "RMT_RELEASE_KEY_PASSWORD")
val releaseValues = releaseVariables.associateWith { System.getenv(it)?.takeIf { value -> value.isNotBlank() } }
val releaseConfigured = releaseValues.values.all { it != null }
val localKeyFile = rootProject.file(System.getenv("RMT_KEYSTORE") ?: ".build-sign/prebuilt.jks")
val localKeyAvailable = localKeyFile.isFile
check(System.getenv("RMT_KEYSTORE").isNullOrBlank() || localKeyAvailable) { "RMT_KEYSTORE 必须指向已有的本地密钥库。" }
val validateReleaseSigning = tasks.register("validateReleaseSigning") {
 doLast {
  check(githubRepository.isNotEmpty()) { "正式发布前请在 repository.properties 设置真实 GitHub 仓库，供程序的更新入口使用。" }
  check(releaseConfigured) { "正式发布需要外部私钥签名，请配置："+releaseVariables.filter { releaseValues[it] == null }.joinToString() }
  val keyFile = rootProject.file(releaseValues.getValue("RMT_RELEASE_KEYSTORE")!!).canonicalFile
  check(keyFile.isFile && !keyFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) { "正式发布密钥必须是工程目录外的有效文件。" }
  val releaseStore = KeyStore.getInstance(keyFile,releaseValues.getValue("RMT_RELEASE_STORE_PASSWORD")!!.toCharArray())
  val certificate = releaseStore.getCertificate(releaseValues.getValue("RMT_RELEASE_KEY_ALIAS")!!)
  check(certificate != null) { "正式发布密钥别名不存在。" }
  val publicKeyHash = MessageDigest.getInstance("SHA-256").digest(certificate.publicKey.encoded).joinToString("") { "%02x".format(it.toInt() and 0xff) }
  check(publicKeyHash != rootProject.file(".build-sign/test-public-key.sha256").readText().trim().lowercase()) {
   "正式发布不能使用旧测试签名。"
  }
 }
}
android {
 namespace = "io.github.renjhtools.redmagictiles"
 compileSdk = 36
 defaultConfig {
  applicationId = "io.github.renjhtools.redmagictiles"
  minSdk = 35
  targetSdk = 35
  versionCode = appVersion.getProperty("versionCode").toInt()
  versionName = appVersion.getProperty("versionName")
  buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepository\"")
 }
 buildFeatures { buildConfig = true }
 signingConfigs {
  if(localKeyAvailable) create("local") {
   storeFile = localKeyFile
   storePassword = System.getenv("RMT_SIGN_PASS") ?: "android"
   keyAlias = System.getenv("RMT_KEY_ALIAS") ?: "tile-studio"
   keyPassword = System.getenv("RMT_SIGN_PASS") ?: "android"
  }
  if(releaseConfigured) create("distribution") {
   storeFile = rootProject.file(releaseValues.getValue("RMT_RELEASE_KEYSTORE")!!)
   storePassword = releaseValues.getValue("RMT_RELEASE_STORE_PASSWORD")
   keyAlias = releaseValues.getValue("RMT_RELEASE_KEY_ALIAS")
   keyPassword = releaseValues.getValue("RMT_RELEASE_KEY_PASSWORD")
  }
 }
 buildTypes {
  getByName("debug") { if(localKeyAvailable) signingConfig = signingConfigs.getByName("local") }
  getByName("release") { isMinifyEnabled = false; signingConfig = if(releaseConfigured) signingConfigs.getByName("distribution") else null }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 packaging { resources { merges += "META-INF/xposed/*" } }
 lint { abortOnError = false }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateReleaseSigning) }
dependencies { compileOnly("io.github.libxposed:api:102.0.0") }

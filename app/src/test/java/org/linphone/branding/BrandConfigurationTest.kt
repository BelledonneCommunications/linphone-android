package org.linphone.branding

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.linphone.environment.environments
import org.linphone.models.EnvironmentOverride

/**
 * Checks each reseller brand's source set (app/src/<brand>) against its release pipeline
 * (build-release-*.yml) and the main app configuration. Read-only: nothing here writes files.
 */
class BrandConfigurationTest {

    private val appDir: File = listOf(File(System.getProperty("user.dir")!!), File("app"))
        .map { it.absoluteFile }
        .first { File(it, "src/main/AndroidManifest.xml").exists() }
    private val repoDir: File = appDir.parentFile!!

    private data class Brand(val name: String, val packageName: String, val gradleTask: String)

    // Parsed from the pipelines, so adding a brand pipeline automatically brings it under test.
    private val brands: List<Brand> by lazy {
        repoDir.listFiles { f -> f.name.matches(Regex("build-release-.+\\.yml")) }!!
            .sortedBy { it.name }
            .map { yml ->
                val text = yml.readText()
                fun find(pattern: String) = Regex(pattern).find(text)?.groupValues?.get(1)
                    ?: throw AssertionError("${yml.name}: no match for $pattern")
                Brand(
                    name = find(
                        """baseFolder\s*\n\s*value:\s*'\$\(Build\.SourcesDirectory\)/app/src/([^']+)'"""
                    ),
                    packageName = find("""s/cloud\.xarios\.dimensions/([^/]+)/g"""),
                    gradleTask = find("""tasks:\s*'([^']+)'""")
                )
            }
    }

    private val sourceSetsThatAreNotBrands = setOf("main", "test", "androidTest", "debug")

    // Every brand overrides these. If a brand is missing one it silently shows the main app's version.
    private val requiredBrandFiles = listOf(
        "AndroidManifest.xml",
        "google-services.json",
        "res/drawable/about_logo_dark.xml",
        "res/drawable/header_logo_dark.xml",
        "res/drawable/ic_launcher_foreground.xml",
        "res/drawable-night/about_logo_dark.xml",
        "res/drawable-night/header_logo_dark.xml",
        "res/raw/environment_overrides.json",
        "res/values/colors.xml",
        "res/values/ic_launcher_background.xml",
        "res/values/strings.xml",
        "res/values/styles.xml"
    )

    // Required files a brand is knowingly missing, per brand. Remove the entry once the file is added.
    private val knownMissingBrandFiles = mapOf(
        // Deferred, no WI yet: in dark mode the About screen shows Voyager's light-mode logo.
        "voyager" to setOf("res/drawable-night/about_logo_dark.xml")
    )

    // Override keys that are known to be wrong but not yet fixed, per brand. Anything listed here
    // is ignored by the override checks below; remove the entry once the JSON is corrected.
    // e.g. "ecx" to setOf("documentationRootUrl")
    private val knownOverrideIssues = emptyMap<String, Set<String>>()

    @Test
    fun `every brand source set has a release pipeline and vice versa`() {
        val sourceSets = File(appDir, "src").listFiles { f -> f.isDirectory }!!
            .map { it.name }
            .filterNot { it in sourceSetsThatAreNotBrands }
            .toSet()
        assertEquals(sourceSets, brands.map { it.name }.toSet())
    }

    @Test
    fun `every brand pipeline builds a build type declared in build gradle`() {
        val buildGradle = File(appDir, "build.gradle").readText()
        for (brand in brands) {
            assertTrue(
                "${brand.name}: build.gradle has no '${brand.name} { initWith release }' build type",
                Regex("""\b${Regex.escape(brand.name)}\s*\{\s*initWith release\s*}""").containsMatchIn(
                    buildGradle
                )
            )
            assertTrue(
                "${brand.name}: pipeline task '${brand.gradleTask}' doesn't match build type '${brand.name}'",
                brand.gradleTask.equals("bundle${brand.name}", ignoreCase = true)
            )
        }
    }

    @Test
    fun `every brand has the required branded files`() {
        val missing = brands.flatMap { brand ->
            val exempt = knownMissingBrandFiles[brand.name].orEmpty()
            requiredBrandFiles
                .filterNot { File(appDir, "src/${brand.name}/$it").isFile || it in exempt }
                .map { "${brand.name}: $it" }
        }
        assertTrue("Missing branded files:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    @Test
    fun `known missing brand files are still missing`() {
        // Keeps knownMissingBrandFiles honest: once a file is added, its exemption must go too.
        val stale = knownMissingBrandFiles.flatMap { (brand, files) ->
            files.filter { File(appDir, "src/$brand/$it").isFile }.map { "$brand: $it" }
        }
        assertTrue(
            "These files now exist; remove them from knownMissingBrandFiles:\n" + stale.joinToString(
                "\n"
            ),
            stale.isEmpty()
        )
    }

    @Test
    fun `every brand's splash theme and splash icon exist`() {
        for (brand in brands) {
            val dir = File(appDir, "src/${brand.name}")
            val manifest = File(dir, "AndroidManifest.xml").readText()
            val styles = File(dir, "res/values/styles.xml").readText()

            val theme = Regex("""android:theme="@style/([^"]+SplashScreenTheme)"""").find(manifest)
                ?.groupValues?.get(1)
                ?: throw AssertionError("${brand.name}: manifest sets no splash screen theme")

            val style = Regex(
                """<style name="${Regex.escape(theme)}"[^>]*>(.*?)</style>""",
                RegexOption.DOT_MATCHES_ALL
            )
                .find(styles)?.groupValues?.get(1)
                ?: throw AssertionError("${brand.name}: styles.xml doesn't define $theme")

            val icon = Regex("""windowSplashScreenAnimatedIcon">@drawable/([^<]+)<""").find(style)
                ?.groupValues?.get(1)
                ?: throw AssertionError(
                    "${brand.name}: $theme sets no windowSplashScreenAnimatedIcon"
                )

            val iconExists = listOf(dir, File(appDir, "src/main")).any {
                File(
                    it,
                    "res/drawable/$icon.xml"
                ).isFile
            }
            assertTrue("${brand.name}: splash icon @drawable/$icon not found", iconExists)
        }
    }

    @Test
    fun `every google-services json matches its package name`() {
        val expected = listOf("main" to mainPackageName()) + brands.map { it.name to it.packageName }
        for ((sourceSet, packageName) in expected) {
            val file = if (sourceSet == "main") {
                File(appDir, "google-services.json")
            } else {
                File(
                    appDir,
                    "src/$sourceSet/google-services.json"
                )
            }
            val packages = JsonParser.parseString(file.readText()).asJsonObject
                .getAsJsonArray("client")
                .map { it.asJsonObject["client_info"].asJsonObject["android_client_info"].asJsonObject["package_name"].asString }
            assertTrue(
                "$sourceSet: $file has no client for $packageName (found $packages)",
                packageName in packages
            )
        }
    }

    @Test
    fun `environment overrides refer to real environments`() {
        val ids = environments.map { it.id }.toSet()
        for ((sourceSet, overrides) in allOverrides()) {
            for (override in overrides) {
                val id = override["id"]?.asString
                assertTrue(
                    "$sourceSet: override has unknown environment id '$id' (known: $ids)",
                    id in ids
                )
            }
        }
    }

    @Test
    fun `environment overrides only use fields that exist`() {
        val fields = EnvironmentOverride::class.java.declaredFields.map { it.name }.toSet()
        val unknown = allOverrides().flatMap { (sourceSet, overrides) ->
            val ignored = knownOverrideIssues[sourceSet].orEmpty()
            overrides.flatMap { it.keySet() }
                .filterNot { it in fields || it in ignored }
                .map { "$sourceSet: $it" }
        }
        assertTrue(
            "Unknown override keys (silently ignored at runtime; valid keys: $fields):\n" + unknown.joinToString(
                "\n"
            ),
            unknown.isEmpty()
        )
    }

    @Test
    fun `environment overrides set at most one default`() {
        for ((sourceSet, overrides) in allOverrides()) {
            val defaults = overrides.filter { it["isDefault"]?.asBoolean == true }.map { it["id"]?.asString }
            if (defaults.size > 1) fail("$sourceSet: more than one default environment: $defaults")
        }
    }

    private fun allOverrides(): List<Pair<String, List<JsonObject>>> =
        (listOf("main") + brands.map { it.name }).map { sourceSet ->
            val file = File(appDir, "src/$sourceSet/res/raw/environment_overrides.json")
            sourceSet to JsonParser.parseString(file.readText()).asJsonArray.map { it.asJsonObject }
        }

    private fun mainPackageName(): String =
        Regex("""def packageName = "([^"]+)"""").find(File(appDir, "build.gradle").readText())!!.groupValues[1]
}

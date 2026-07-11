// On GitHub Actions runners, google()/mavenCentral()/gradlePluginPortal() are fully
// reachable and far more reliable than the regional mirrors below (which are only there
// for local development machines in Iran, where google()/mavenCentral() are blocked
// entirely by the network). Putting a flaky regional mirror FIRST on CI has actually
// broken builds before (e.g. "Received status code 502 from server: Bad Gateway" from
// maven.aliyun.com) even though the real repos were sitting right there unreachable-only-
// locally. GITHUB_ACTIONS is set to "true" automatically by every Actions runner.
val isCi = System.getenv("GITHUB_ACTIONS") == "true" || System.getenv("CI") == "true"

pluginManagement {
    repositories {
        if (isCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
        // Iranian/regional mirrors - only reached first on local (non-CI) builds, where
        // google()/gradlePluginPortal() are unreachable from this network entirely
        // (confirmed: a plain "google(), mavenCentral(), gradlePluginPortal()" setup
        // failed to find the Android Gradle Plugin in any of them). Aliyun is listed
        // first among these because it's a byte-for-byte proxy of Google's actual repo
        // (correct Gradle Module Metadata included) - myket.ir went first originally but
        // served the artifact without metadata that matched, causing a separate "No
        // matching variant" resolution error.
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://en-mirror.ir") }
        maven { url = uri("https://maven.myket.ir") }
        if (!isCi) {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCi) {
            google()
            mavenCentral()
        }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://en-mirror.ir") }
        maven { url = uri("https://maven.myket.ir") }
        // Hosts Myket's billing client (com.github.myketstore:myket-billing-client),
        // shared by both the bazaar and myket flavors - see StoreBillingHelper.kt.
        maven { url = uri("https://jitpack.io") }
        if (!isCi) {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "MaliarPro"
include(":app")

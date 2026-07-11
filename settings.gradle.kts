pluginManagement {
    repositories {
        // Iranian/regional mirrors first - google()/gradlePluginPortal() below are
        // unreachable from this network entirely (confirmed: a plain "google(),
        // mavenCentral(), gradlePluginPortal()" setup failed to find the Android Gradle
        // Plugin in any of them). Aliyun is listed first because it's a byte-for-byte
        // proxy of Google's actual repo (correct Gradle Module Metadata included) -
        // myket.ir went first originally but served the artifact without metadata that
        // matched, causing a separate "No matching variant" resolution error.
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://en-mirror.ir") }
        maven { url = uri("https://maven.myket.ir") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://en-mirror.ir") }
        maven { url = uri("https://maven.myket.ir") }
        // Hosts Myket's billing client (com.github.myketstore:myket-billing-client),
        // shared by both the bazaar and myket flavors - see StoreBillingHelper.kt.
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
    }
}

rootProject.name = "MaliarPro"
include(":app")

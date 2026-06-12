pluginManagement {
}

plugins{
    // Auto-provision the JDK required by jvm.toolchain (25) — this machine ships JDK 21.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "ABDownloadManager"

include("android:app")
include("desktop:app")
include("desktop:app-utils")
include("desktop:shared")
include("desktop:slf4j-impl")
include("desktop:mac_utils")
include("downloader:core")
include("downloader:monitor")
include("integration:server")
include("shared:utils")
include("shared:app")
include("shared:compose-utils")
include("shared:resources")
include("shared:resources:contracts")
include("shared:config")
include("shared:updater")
include("shared:auto-start")
include("shared:nanohttp4k")
includeBuild("./compositeBuilds/shared"){
    name="build-shared"
}
includeBuild("./compositeBuilds/plugins")

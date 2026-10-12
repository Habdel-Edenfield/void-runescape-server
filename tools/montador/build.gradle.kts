plugins {
    id("shared")
}

kotlin {
    dependencies {
        implementation(project(":buffer"))
        implementation(project(":cache"))
        implementation(project(":config"))
        implementation(libs.displee.cache)
    }
}

// Exports the cache's sprites, fonts and interfaces for the "Void - Montador de Interfaces" tool.
// Args via -Pargs="<cache dir> <data dir> <output dir>" (see MontadorExport).
tasks.register<JavaExec>("montadorExport") {
    group = "montador"
    description = "Exports every sprite (PNG), font and interface (JSON) of the cache for the Montador de Interfaces."
    mainClass.set("world.gregs.voidps.tools.montador.MontadorExport")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    jvmArgs("-Djava.awt.headless=true", "-Xmx2g")
    val cliArgs = (findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    args = cliArgs
}

// Checks that the Montador's IF3 component codec writes back every component of the cache byte for byte.
tasks.register<JavaExec>("montadorVerify") {
    group = "montador"
    description = "Re-encodes every interface component of the cache and compares it with the original bytes (read only)."
    mainClass.set("world.gregs.voidps.tools.montador.MontadorVerify")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    val cliArgs = (findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    args = cliArgs
}

// Packs one interface from the Montador into the cache. Run by the tool's packing command, which refuses
// while the game server is running and backs the cache up first. Args: "<package.json> <cache dir>".
tasks.register<JavaExec>("montadorPack") {
    group = "montador"
    description = "Writes one interface made in the Montador de Interfaces into the cache (index 3)."
    mainClass.set("world.gregs.voidps.tools.montador.MontadorPack")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    jvmArgs("-Xmx2g")
    val cliArgs = (findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    args = cliArgs
}

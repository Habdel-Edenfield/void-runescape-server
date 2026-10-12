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

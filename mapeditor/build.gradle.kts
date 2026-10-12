plugins {
    id("shared")
}

kotlin {
    dependencies {
        implementation(project(":buffer"))
        implementation(project(":cache"))
        implementation(project(":config"))
        implementation(project(":types"))
        implementation(project(":tools"))
        implementation(libs.displee.cache)
    }
}

tasks.withType<JavaExec> {
    workingDir = rootDir
}

fun JavaExec.cliArgs() {
    args = (findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}

tasks.register<JavaExec>("mapRegion") {
    group = "map editor"
    description = "Exports a map region to toml or imports one into a cache. Args via -Pargs=\"export 12850\" (see MapRegionCli)."
    mainClass.set("world.gregs.voidps.mapeditor.MapRegionCli")
    classpath = sourceSets["main"].runtimeClasspath
    cliArgs()
}

tasks.register<JavaExec>("renderMinimap") {
    group = "map editor"
    description = "Renders every region of data/cache/ into images/<level>/<region>.png for the minimap viewer."
    mainClass.set("world.gregs.voidps.mapeditor.RenderMinimap")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Djava.awt.headless=true", "-Xmx4g")
}

tasks.register<JavaExec>("minimap") {
    group = "map editor"
    description = "Opens the top down map viewer over the images made by renderMinimap."
    mainClass.set("world.gregs.voidps.tools.map.view.MapViewer")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Xmx4g")
}

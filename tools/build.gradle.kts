plugins {
    id("shared")
}

kotlin {
    dependencies {
        implementation(project(":buffer"))
        implementation(project(":cache"))
        implementation(project(":engine"))
        implementation(project(":game"))
        implementation(project(":network"))
        implementation(project(":database"))
        implementation(libs.exposed)
        implementation(libs.exposed.jdbc)
        implementation(project(":config"))
        implementation(project(":types"))

        implementation("org.seleniumhq.selenium:selenium-java:4.16.1")

        implementation(libs.kotlinx.io)
        implementation(libs.kotlinx.coroutines)

        implementation("org.apache.commons:commons-compress:1.24.0")
        implementation("org.jsoup:jsoup:1.17.2")
        implementation("org.sweble.wikitext:swc-engine:3.1.9")
        implementation("com.github.weisj:darklaf-core:2.7.3")
        implementation("javax.xml.bind:jaxb-api:2.3.1")
        implementation(libs.kasechange)

        implementation(libs.koin)
        implementation(libs.displee.cache)
        implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
        implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
        implementation(libs.fastutil)
        implementation(libs.rsmod.pathfinder)

        testImplementation(libs.mockk)
    }
}

tasks.withType<AbstractTestTask>().configureEach {
    failOnNoDiscoveredTests = false
}

tasks.withType<JavaExec> {
    workingDir = projectDir
}

tasks.register<JavaExec>("importPetTranscript") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("world.gregs.voidps.tools.cache.ImportPetTranscript")
    workingDir = rootDir
}

tasks.register<JavaExec>("fixEnums") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("world.gregs.voidps.tools.cache.FixEnums")
    workingDir = rootDir
}

// Brazilian Portuguese for interfaces the client draws from the cache: --args=dump|table|patch (see InterfaceTranslation)
tasks.register<JavaExec>("translateInterfaces") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("world.gregs.voidps.tools.cache.InterfaceTranslation")
    workingDir = rootDir
}

tasks.register<JavaExec>("autoMiningObjects") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("world.gregs.voidps.tools.cache.AutoMiningObjects")
    workingDir = rootDir
}

// Doors and furniture of the houses players can buy in the open world (content.social.house): --args=check|patch (see WorldHouseObjects)
tasks.register<JavaExec>("worldHouseObjects") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("world.gregs.voidps.tools.cache.WorldHouseObjects")
    workingDir = rootDir
}

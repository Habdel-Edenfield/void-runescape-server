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

// Tira do cache o que a página das cadeias de produção precisa e os dados não têm: o nome de cada item,
// os bônus de equipamento (os mesmos da janela de equipamento) e os ingredientes das bolsas de Summoning.
// Args via -Pargs="<pasta do cache> <pasta data> <itens.json de saída>" (ver CadeiasExport).
tasks.register<JavaExec>("cadeiasExport") {
    group = "cadeias"
    description = "Exporta nome, bônus e ingredientes de Summoning de cada item para o gerador da página das cadeias de produção."
    mainClass.set("world.gregs.voidps.tools.cadeias.CadeiasExport")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootDir
    jvmArgs("-Djava.awt.headless=true")
    val cliArgs = (findProperty("args") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    args = cliArgs
}

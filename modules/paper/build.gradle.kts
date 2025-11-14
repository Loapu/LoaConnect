import io.papermc.paperweight.util.capitalized

plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

project.group = "dev.loapu"
project.version = "0.2"

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
    implementation(libs.nimbus)
    implementation(libs.luckperms)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.jar {
    archiveFileName.set("${rootProject.name}-${project.name.capitalized()}-${project.version}.jar")
}
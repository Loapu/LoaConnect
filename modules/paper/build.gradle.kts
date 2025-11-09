plugins {
    `java-library`
    alias(libs.plugins.paperweight)
}

project.group = "dev.loapu"
project.version = "0.1"

dependencies {
    paperweight.paperDevBundle(libs.versions.paper)
}

paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
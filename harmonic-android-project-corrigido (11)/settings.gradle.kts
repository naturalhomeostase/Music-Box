pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack: onde vive o jaudiotagger (fork compatível com Android,
        // sem dependências de java.awt), usado pra editar tags reais
        // (artista/álbum/gênero/etc) direto no arquivo de áudio.
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "Harmonic"
include(":app")

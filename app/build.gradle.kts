plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.harmonic.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.harmonic.player"
        minSdk = 26 // Android 8.0 — cobre praticamente todos os aparelhos em uso
        targetSdk = 34
        // Sobe sozinho a cada build do GitHub Actions (usa o número da
        // execução do workflow); em builds locais, sempre 1. Isso evita
        // precisar lembrar de subir esse número manualmente toda hora.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0-mvp"

        vectorDrawables.useSupportLibrary = true
    }

    // Chaves de assinatura FIXAS, commitadas no repositório (pasta
    // /keystore) — o motivo de ter que "desinstalar a versão antiga" a
    // cada teste era esse: sem uma chave fixa, cada máquina/execução do
    // GitHub Actions gerava sua própria chave de debug do zero, e o
    // Android bloqueia atualizar um app quando a assinatura muda.
    //
    // A senha do keystore de DEBUG ("android"/"androiddebugkey") é segura
    // deixar assim — é a senha padrão e PÚBLICA que o próprio Android SDK
    // usa em todo projeto novo, não é segredo de ninguém. Por isso o
    // debug.keystore continua commitado no repositório normalmente.
    //
    // Já o keystore de RELEASE não fica mais commitado (ver .gitignore) —
    // ele é reconstruído em tempo de build a partir do secret
    // `RELEASE_KEYSTORE_BASE64` do GitHub Actions (o workflow decodifica
    // o base64 de volta pro arquivo "keystore/release.keystore" antes de
    // rodar o Gradle). Localmente, você mantém sua própria cópia do
    // arquivo na mesma pasta — ela nunca é enviada ao Git.
    //
    // Senha, alias e senha da chave também vêm só de variáveis de
    // ambiente (`RELEASE_STORE_PASSWORD` / `RELEASE_KEY_ALIAS` /
    // `RELEASE_KEY_PASSWORD`, configuradas como "Secret" nas configurações
    // do GitHub). Sem esses secrets configurados, o build de release falha
    // ao tentar assinar — de propósito, pra nunca builder silenciosamente
    // com uma senha/alias placeholder.
    signingConfigs {
        getByName("debug") {
            storeFile = file("../keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = file("../keystore/release.keystore")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "DEFINA_O_SECRET_RELEASE_STORE_PASSWORD"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "DEFINA_O_SECRET_RELEASE_KEY_ALIAS"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "DEFINA_O_SECRET_RELEASE_KEY_PASSWORD"
        }
    }

    buildTypes {
        release {
            // Minificação desligada por enquanto: o app usa Room, Media3,
            // Glance e uma lib de tags de áudio (jaudiotagger) que dependem
            // bastante de reflexão — ligar o R8 sem regras de "keep" bem
            // testadas é um jeito clássico de introduzir crash só na build
            // de release. Deixei documentado abaixo pra quando quiser
            // reativar com calma.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // APIs experimentais do Material3 (TopAppBar, ModalBottomSheet, etc.)
        // são usadas o app inteiro — em vez de depender de lembrar o @OptIn
        // em cada arquivo (foi exatamente isso que quebrou o build), liberamos
        // globalmente aqui. Essas APIs já são consideradas estáveis na prática,
        // só ainda carregam o rótulo "experimental" no Material3.
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }

    buildFeatures {
        compose = true
        // Precisa disso pra acessar BuildConfig.VERSION_NAME (tela "Sobre")
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Media3 (ExoPlayer + MediaSession) — motor de reprodução em segundo plano,
    // notificação, controles de tela bloqueada, Bluetooth, Android Auto
    implementation("androidx.media3:media3-exoplayer:1.4.0")
    implementation("androidx.media3:media3-session:1.4.0")
    implementation("androidx.media3:media3-ui:1.4.0")

    // Room — banco local da biblioteca de músicas
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore — preferências (cor de destaque, fundo, pastas ignoradas, etc.)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Palette — extrair cor dominante da capa do álbum
    implementation("androidx.palette:palette-ktx:1.0.0")

    // Edição de tags reais (ID3/Vorbis/MP4...) direto no arquivo — fork do
    // jaudiotagger sem dependências de java.awt, feito pra Android.
    implementation("com.github.Adonai:jaudiotagger:2.3.15")

    // Coil — carregar capas de álbum/imagens de fundo
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Accompanist — permissões em runtime
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // Glance — widgets de tela inicial (Material You também nos widgets)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

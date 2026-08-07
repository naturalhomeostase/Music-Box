package com.harmonic.player.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.harmonic.player.BuildConfig
import com.harmonic.player.R

/**
 * Tela "Sobre" — versão, licenças de código aberto, contato e política de
 * privacidade, tudo direto no app (nada depende de uma página externa, o
 * que é importante já que o repositório no GitHub é privado — um link pra
 * lá não abriria pra ninguém de fora).
 *
 * A política de privacidade aqui é curta de propósito: o app não tem conta
 * de usuário, não usa analytics/rastreamento/anúncios, e a única chamada de
 * rede que existe (busca de álbum/ano/gênero pelo iTunes, na edição de
 * tags) só acontece quando a pessoa toca no botão — nunca em segundo
 * plano. Não é um texto genérico copiado de outro app; reflete o que o
 * código realmente faz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Sobre", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Music Box", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "Versão ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(28.dp))

            AboutSection(title = "Contato") {
                Text(
                    "natural.homeostase@gmail.com",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                            data = android.net.Uri.parse("mailto:natural.homeostase@gmail.com")
                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Music Box — contato")
                        }
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            AboutSection(title = "Privacidade") {
                Text(
                    "O Music Box não pede login, não tem conta de usuário e não usa nenhum " +
                        "serviço de analytics, rastreamento ou anúncios. Sua biblioteca de " +
                        "músicas (títulos, favoritos, playlists, configurações de tema) fica " +
                        "guardada só no seu aparelho — nada é enviado pra fora.\n\n" +
                        "A única exceção é a busca online de álbum/ano/gênero na tela de " +
                        "editar tags: quando você toca nesse botão especificamente, o título " +
                        "e o artista da música são enviados pro serviço de busca da Apple " +
                        "(iTunes) pra sugerir esses dados. Isso só acontece nesse toque " +
                        "específico, nunca automaticamente ou em segundo plano.\n\n" +
                        "Pra apagar todos os dados do app, basta desinstalar ou limpar os " +
                        "dados dele nas configurações do Android.",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(20.dp))

            AboutSection(title = "Bibliotecas de código aberto") {
                val libraries = listOf(
                    "Jetpack Compose, AndroidX, Media3, Room" to "Apache License 2.0",
                    "jaudiotagger" to "LGPL",
                    "Coil" to "Apache License 2.0",
                    "Accompanist" to "Apache License 2.0"
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    libraries.forEachIndexed { index, (name, license) ->
                        // Antes era um Row com SpaceBetween e nenhuma das
                        // duas Text tinha largura limitada — com o nome da
                        // biblioteca mais comprido (ex: a lista "Jetpack
                        // Compose, AndroidX, Media3, Room"), as duas
                        // ocupavam mais espaço do que cabia na linha e
                        // acabavam sobrepondo uma na outra. Empilhando o
                        // nome em cima e a licença embaixo, cada Text tem a
                        // largura toda pra si e quebra linha normalmente
                        // quando precisa — nunca mais sobrepõe.
                        Text(name, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        Text(license, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.labelSmall)
                        if (index != libraries.lastIndex) Spacer(Modifier.height(10.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(14.dp)
        ) {
            content()
        }
    }
}

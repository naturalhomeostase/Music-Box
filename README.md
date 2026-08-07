# Harmonic 🎵

Player de música local para Android — rápido, offline, sem anúncios.
Inspirado no Poweramp, com interface Material You.

## Status: Fase 3 em andamento

Adicionado nesta leva:

- ✅ Capa real do álbum (embutida no arquivo de áudio), com cache em memória —
  aparece na Biblioteca, no mini player e em "Agora Tocando"; cai num ícone
  de nota musical quando a música não tem capa
- ✅ Letras sincronizadas (LRC) offline — procura automaticamente um arquivo
  `.lrc` (sincronizado) ou `.txt` (simples) com o mesmo nome da música, na
  mesma pasta; auto-scroll e destaque da linha atual em "Agora Tocando"
- ✅ Widget de tela inicial (Glance) — mostra música atual + play/pause/
  próxima/anterior, atualiza sozinho quando a música troca

## Correções críticas (feedback do segundo teste)

- ✅ **Músicas duplicando a cada abertura do app** — a tabela não tinha
  índice único em `mediaStoreId`, então cada escaneamento inserida linhas
  novas em vez de atualizar as existentes. Corrigido, e agora o
  escaneamento também preserva favoritos/contagem de reprodução/posição
  salva em vez de resetar tudo.
- ✅ **Músicas não apareciam até reiniciar o app** — o primeiro
  escaneamento rodava antes da permissão ser concedida (no
  `Application.onCreate`), e nada disparava um novo escaneamento depois
  que o usuário aceitava a permissão. Agora isso é automático.
- ✅ **Player não aparecia na barra de notificação** — faltava pedir a
  permissão de notificações em tempo de execução (obrigatória no Android
  13+). Sem ela, o sistema simplesmente não mostra a notificação mesmo com
  o player funcionando normalmente.

## Visual (segunda rodada de ajustes)

- ✅ Barras de título transparentes com o texto na cor de destaque
- ✅ Sombra (escurecimento) sobre o fundo agora é ajustável por slider (0-90%)
- ✅ Blur agora é ajustável por slider (0-40dp) em vez de switch liga/desliga
  — e o padrão agora é 0 (nítido), já que o blur fixo anterior estava forte demais
- ✅ Temas em gradiente (6 opções) que não dependem de nenhuma imagem — mais
  leve, e agora é o fundo padrão do app quando nada foi escolhido
- ✅ Título/artista na tela "Agora Tocando" com cores explícitas (destaque/branco)
- ✅ Música tocando atualmente é destacada na lista da Biblioteca — ícone
  (equalizador tocando/pausado) + texto na cor de destaque



- ✅ Fundo transparente atrás das listas (Biblioteca, Playlists) — antes cada
  item tinha um fundo sólido escondendo o papel de parede
- ✅ Mini player translúcido em vez de opaco
- ✅ Tema padrão trocado de "sistema" pra "escuro" — se o celular está no
  tema claro do sistema, mas o app sempre mostra uma foto de fundo escura,
  o texto ficava escuro sobre fundo escuro (ilegível); título/artista pretos
  na tela "Agora Tocando" era esse bug
- ✅ Paleta de cores de destaque ampliada (16 cores) + seletor de cor
  personalizada (RGB) — antes só 5 opções fixas
- ✅ Opção de escolher qualquer foto da galeria como fundo (antes só os 5
  wallpapers inclusos)
- ✅ Opção de desfocar o fundo (blur) — só tem efeito real no Android 12+,
  em versões mais antigas a imagem fica nítida mesmo com a opção ativada
- ✅ Ícone de play/pause do mini player agora atualiza na hora (antes podia
  ficar um instante mostrando o ícone errado até o player confirmar)



O widget foi escrito usando a API do Glance (`androidx.glance:glance-appwidget`),
que é bem menos comum que o Compose "normal" — por isso é a parte deste PR
com **menor confiança de compilar de primeira**. Se o próximo build falhar
especificamente em arquivos dentro de `widget/`, é o candidato nº 1 a
investigar (nomes de parâmetros de `Row`/`Column`/`ColorProvider` no Glance
podem estar levemente diferentes da versão 1.1.0 real). O resto do projeto
(capa do álbum, letras) usa só Compose/Coil/MediaStore padrão, mais testado.

## O que falta (continuando a fase 3)

- ⬜ Crossfade + ReplayGain (mixagem de fato entre faixas)
- ⬜ Android Auto (requer migrar de `MediaSessionService` pra `MediaLibraryService`)
- ⬜ Visualizador de espectro/ondas
- ⬜ Detecção de duplicatas e arquivos quebrados
- ⬜ A-B Repeat, marcadores/bookmarks, "Wrapped" anual
- ⬜ Editor de tags
- ⬜ Busca de letras online (a busca offline já funciona)
- ⬜ Presets de equalizador prontos (Rock, Pop, Jazz...)
- ⬜ Widgets em outros tamanhos (hoje só tem um tamanho médio)

## Como compilar

### Opção 1 — Android Studio (recomendado para desenvolvimento)
1. Abra este projeto no Android Studio (Hedgehog ou mais recente).
2. O Studio vai baixar as dependências automaticamente na primeira sincronização.
3. Rode no seu celular (via USB, com depuração USB ativada) ou num emulador.

### Opção 2 — GitHub Actions (gera o APK sem precisar instalar nada)
1. Faça push deste repositório para o GitHub.
2. Vá na aba **Actions** → o workflow `Build APK` roda automaticamente.
3. Ao terminar, baixe o artefato `harmonic-debug-apk` — é o `.apk` pronto pra instalar no celular.

> Nota: o `gradle-wrapper.jar` não foi commitado (é um binário) — o workflow do
> GitHub Actions gera ele automaticamente antes de compilar. Se for abrir no
> Android Studio, ele mesmo cuida disso na sincronização inicial.

## Arquitetura

```
app/src/main/java/com/harmonic/player/
├── MainActivity.kt          # Activity única, hospeda a navegação Compose
├── HarmonicApp.kt           # Application: expõe database e settings
├── data/                    # Room (Song, Playlist), MediaStoreScanner, SettingsRepository (DataStore)
├── playback/                # PlaybackService (Media3) + PlayerController (ponte com a UI)
└── ui/
    ├── theme/               # Material You + cor de destaque customizável
    ├── library/              # Tela de biblioteca
    ├── nowplaying/            # Tela "Agora Tocando"
    ├── settings/              # Aparência (cor + wallpaper)
    └── common/                # Telas compartilhadas (permissão, etc.)
```

## Papéis de parede inclusos

Os 5 fundos padrão ficam em `app/src/main/assets/default_wallpapers/`:
leão em chamas, guitarra elétrica, toca-discos vintage, floresta encantada, cidade lo-fi.

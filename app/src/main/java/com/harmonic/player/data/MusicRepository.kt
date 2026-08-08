package com.harmonic.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Antes, o escaneamento do MediaStore rodava dentro de um LaunchedEffect na
 * LibraryScreen — o que significa que toda vez que o usuário saía e voltava
 * pra essa tela (ex: abrindo "Agora Tocando"), o escaneamento rodava de novo
 * do zero. Aqui centralizamos isso: escaneia uma vez, e depois só reage a
 * mudanças reais no MediaStore, no ciclo de vida do app inteiro — não da tela.
 */
class MusicRepository(
    private val scanner: MediaStoreScanner,
    private val dao: SongDao,
    private val settings: SettingsRepository
) {
    private var observingStarted = false

    /**
     * Fica `true` assim que o primeiro escaneamento que realmente encontrou
     * alguma música termina. Usado pelo `MainActivity.onResume()` como
     * critério de "ainda vale a pena tentar de novo" — depois da primeira
     * vez, não precisa mais forçar rescans a cada volta ao app.
     */
    @Volatile
    var hasScannedSuccessfully: Boolean = false
        private set

    // O escaneamento inicial (disparado no Application.onCreate) e os
    // escaneamentos reativos (disparados por mudanças no MediaStore — ex:
    // o próprio MediaScannerConnection.scanFile chamado depois de editar
    // tags) são lançados como jobs separados na mesma scope, então podiam
    // rodar ao mesmo tempo. Como runScan() lê o banco, mescla, e só DEPOIS
    // regrava (não é atômico), um scan mais antigo que começou a ler ANTES
    // de uma edição de tags podia terminar de escrever DEPOIS dela — e
    // sobrescrevia a edição de volta pro valor antigo, sem nenhum erro
    // visível. Serializando com um Mutex, só um runScan() roda por vez, e
    // cada um sempre lê o estado mais recente do banco antes de mesclar.
    private val scanMutex = Mutex()

    // Resolução do ano em segundo plano (ver runScan/launchYearFallbackResolution):
    // guardamos o Job pra nunca ter duas rodadas rodando ao mesmo tempo — se
    // um re-scan (ex: ContentObserver disparando várias vezes seguidas)
    // chegar enquanto a rodada anterior ainda está lendo tags de arquivo,
    // ela simplesmente não dispara outra; a própria consulta busca de novo
    // "quem ainda está sem ano" no banco, então nada fica de fora.
    private var yearFallbackJob: Job? = null

    fun startObserving(scope: CoroutineScope) {
        if (observingStarted) return
        observingStarted = true
        scope.launch { runScanSerialized(scope) }
        scanner.observeChanges()
            .onEach { runScanSerialized(scope) }
            .launchIn(scope)
    }

    /**
     * Força um novo escaneamento imediatamente — usado logo depois que o
     * usuário concede a permissão de áudio pela primeira vez. Sem isso, o
     * escaneamento inicial (que roda no Application.onCreate, antes da
     * permissão existir) simplesmente não encontrava nada, e só um
     * reinício completo do app rodava o scan de novo com a permissão já
     * concedida — daí a sensação de "preciso reiniciar pra ver as músicas".
     */
    fun rescanNow(scope: CoroutineScope) {
        scope.launch { runScanSerialized(scope) }
    }

    private suspend fun runScanSerialized(scope: CoroutineScope) = scanMutex.withLock { runScan(scope) }

    private suspend fun runScan(scope: CoroutineScope) {
        val ignored = settings.ignoredFolders.first()
        val scanned = scanner.scan(ignoredFolders = ignored)
        if (scanned.isEmpty()) return // provavelmente sem permissão ainda; não apaga nada do banco
        hasScannedSuccessfully = true

        // Mescla com o que já existe: preserva favoritos, contagem de
        // reproduções, última vez tocada e posição salva — sem isso, cada
        // re-scan "resetaria" essas informações mesmo a música sendo a
        // mesma (só o `REPLACE` do SQLite recriando a linha do zero).
        val existingSongs = dao.getAllSongsOnce()
        val existingByMediaStoreId = existingSongs.associateBy { it.mediaStoreId }
        // Casar só pelo ID do MediaStore não é suficiente: pedir pro
        // Android reindexar um arquivo que a gente acabou de editar (tags)
        // às vezes faz o MediaStore recriar a linha dele com um _id NOVO —
        // aí a busca acima não encontrava a música "existente", tratava
        // como uma música nova (com os dados antigos, ainda em cache) e
        // literalmente apagava a linha certa (com a edição) por baixo.
        // Casando também pelo caminho do arquivo (bem mais estável),
        // reconhecemos que é a mesma música mesmo com o _id tendo mudado.
        val existingByPath = existingSongs.associateBy { it.path }
        val merged = scanned.map { fresh ->
            val existing = existingByMediaStoreId[fresh.mediaStoreId] ?: existingByPath[fresh.path]
            if (existing != null) {
                fresh.copy(
                    id = existing.id,
                    // O MediaStore só reflete as tags editadas pelo app
                    // depois de um rescan do sistema — até lá, ele ainda
                    // tem os valores antigos em cache. Preservando esses
                    // campos a partir do banco (uma vez que a música
                    // existe nele, ele vira a fonte da verdade, igual já
                    // acontecia só com o título), a edição de tags não é
                    // mais desfeita no próximo escaneamento automático.
                    title = existing.title,
                    artist = existing.artist,
                    album = existing.album,
                    genre = existing.genre,
                    trackNumber = existing.trackNumber,
                    // O MediaStore não devolve ano pra toda música (ver
                    // comentário no MediaStoreScanner) — quando o scan
                    // rápido não achou um agora, preserva o que já tinha
                    // sido resolvido em segundo plano numa rodada anterior,
                    // em vez de voltar pra null e precisar reler o arquivo
                    // de novo à toa a cada re-scan.
                    year = fresh.year ?: existing.year,
                    isFavorite = existing.isFavorite,
                    playCount = existing.playCount,
                    lastPlayedAt = existing.lastPlayedAt,
                    playbackPositionMs = existing.playbackPositionMs,
                    isHidden = existing.isHidden,
                    customCoverUri = existing.customCoverUri,
                    trimStartMs = existing.trimStartMs,
                    trimEndMs = existing.trimEndMs
                )
            } else fresh
        }

        val currentIds = merged.map { it.mediaStoreId }.toSet()
        // Pela mesma razão acima: uma música cujo _id do MediaStore mudou
        // não pode ser considerada "removida" — ela já foi reaproveitada
        // (pelo caminho) na linha mesclada acima, então tirá-la daqui pelo
        // caminho evita apagar por engano essa mesma linha logo em seguida.
        val currentPaths = merged.map { it.path }.toSet()
        val removed = existingByMediaStoreId.keys - currentIds
        val removedIds = existingSongs
            .filter { it.mediaStoreId in removed && it.path !in currentPaths }
            .map { it.mediaStoreId }
        if (removedIds.isNotEmpty()) dao.deleteByMediaStoreIds(removedIds)

        dao.insertAll(merged)

        // A partir daqui as músicas já estão no banco e aparecem na tela
        // (mesma sensação de "imediato" que a versão anterior tinha). Só
        // agora, sem segurar essa função, disparamos a leitura mais lenta
        // (arquivo por arquivo) do ano das que ainda não têm — quando
        // terminar cada uma, a lista se atualiza sozinha (Flow do Room).
        launchYearFallbackResolution(scope)
    }

    /**
     * Lê o ano direto do arquivo (jaudiotagger) só pras músicas que o
     * MediaStore não conseguiu preencher — em segundo plano, depois que a
     * biblioteca já apareceu na tela. Ver comentário em [MediaStoreScanner.toSong].
     */
    private fun launchYearFallbackResolution(scope: CoroutineScope) {
        if (yearFallbackJob?.isActive == true) return
        yearFallbackJob = scope.launch(Dispatchers.IO) {
            val pending = dao.getSongsMissingYear()
            for (song in pending) {
                val year = scanner.resolveYearFallback(song.path) ?: continue
                dao.updateSongYear(song.id, year)
            }
        }
    }
}

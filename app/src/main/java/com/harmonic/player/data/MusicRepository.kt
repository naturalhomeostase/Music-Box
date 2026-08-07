package com.harmonic.player.data

import kotlinx.coroutines.CoroutineScope
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

    fun startObserving(scope: CoroutineScope) {
        if (observingStarted) return
        observingStarted = true
        scope.launch { runScanSerialized() }
        scanner.observeChanges()
            .onEach { runScanSerialized() }
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
        scope.launch { runScanSerialized() }
    }

    private suspend fun runScanSerialized() = scanMutex.withLock { runScan() }

    private suspend fun runScan() {
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
    }
}

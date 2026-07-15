package code.name.monkey.retromusic.fragments.home

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.Toast
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.os.bundleOf
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.*
import code.name.monkey.retromusic.databinding.FragmentHomeBinding
import code.name.monkey.retromusic.dialogs.CreatePlaylistDialog
import code.name.monkey.retromusic.dialogs.ImportPlaylistDialog
import code.name.monkey.retromusic.extensions.dip
import code.name.monkey.retromusic.extensions.elevatedAccentColor
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.profileBannerOptions
import code.name.monkey.retromusic.glide.RetroGlideExtension.userProfileOptions
import code.name.monkey.retromusic.interfaces.IScrollHelper
import code.name.monkey.retromusic.util.PreferenceUtil.userName
import com.bumptech.glide.Glide
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.MaterialSharedAxis
import android.Manifest
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.ContentUris
import android.provider.MediaStore
import androidx.recyclerview.widget.LinearLayoutManager
import android.content.Intent
import java.io.File
import java.io.FileOutputStream
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import android.util.Log
import android.os.Environment
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

data class Subtitle(val startTime: Long, val endTime: Long, val text: String)

class HomeFragment : AbsMainActivityFragment(R.layout.fragment_home), IScrollHelper {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var savedPosition: Int = 0
    private var selectedFolderUri: Uri? = null
    private val videoPlaylist = mutableListOf<Uri>()
    private var currentIndex = 0
    private val downloadVideoList = mutableListOf<Pair<String, Uri>>()
    private val subtitleList = mutableListOf<Subtitle>()
    private val handler = Handler(Looper.getMainLooper())
    private var selectedSubtitleUri: Uri? = null
    private var selectedAudioUri: Uri? = null
    private var selectedAudioUris = mutableListOf<Uri>()

    // NUEVO: indica si el próximo srt seleccionado debe disparar el burn automáticamente
    private var pendingHardcodeBurn = false

    // NUEVO: estado y detector de gestos para el fullscreen del reproductor
    private var isFullscreen = false
    private lateinit var fullscreenGestureDetector: GestureDetector


    // Nuevo Launcher para selección múltiple de audio
    private val multiaudioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            selectedAudioUris = uris.toMutableList()
            Toast.makeText(requireContext(), "${uris.size} audios seleccionados", Toast.LENGTH_SHORT).show()
        }
    }
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) loadVideosFromDownloads() else Toast.makeText(requireContext(), "Permiso denegado, no podemos cargar videos", Toast.LENGTH_SHORT).show()
    }

    // BOTÓN FUTURO en home_content.xml: btnCreateVideoFromPhotos
    // binding.homeContent.btnCreateVideoFromPhotos.setOnClickListener { photosPickerLauncher.launch("image/*") }
    private val photosPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            crearVideoDesdeFotos(uris)
        } else {
            Toast.makeText(requireContext(), "Selecciona al menos una foto", Toast.LENGTH_SHORT).show()
        }
    }

    private val updateSubtitleTask = object : Runnable {
        override fun run() {
            if (binding.homeContent.videoPlayer.isPlaying) {
                val player = binding.homeContent.videoPlayer
                val currentPos = player.currentPosition
                val currentSub = subtitleList.find { currentPos.toLong() in it.startTime..it.endTime }
                binding.homeContent.tvSubtitleOverlay.text = currentSub?.text ?: ""
                binding.homeContent.videoSeekBar.max = player.duration
                binding.homeContent.videoSeekBar.progress = currentPos
                binding.homeContent.tvCurrentTime.text = formatTime(currentPos)
                binding.homeContent.tvTotalTime.text = formatTime(player.duration)
            }
            handler.postDelayed(this, 500)
        }
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedFolderUri = uri
                saveFolderUri(uri)
                loadVideosFromSelectedFolder(uri)
            }
        }
    }

    private val subtitlePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedSubtitleUri = it
            try {
                requireContext().contentResolver.openInputStream(it)?.use { stream -> parseSrt(stream) }
                Toast.makeText(requireContext(), "Subtítulos cargados", Toast.LENGTH_SHORT).show()

                // NUEVO: si veníamos del botón "incrustar subtítulos" y todavía no había srt,
                // arrancamos el burn apenas se elige el archivo.
                if (pendingHardcodeBurn) {
                    pendingHardcodeBurn = false
                    hardcodearSubtitulos()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error al leer subtítulos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedAudioUri = it
            val name = requireContext().contentResolver.query(
                it, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "Audio" } ?: "Audio"
            Toast.makeText(requireContext(), "Audio cargado: $name", Toast.LENGTH_SHORT).show()
        }
    }

    // Picker de videos para UNIR — selección múltiple
    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            // Reproducir el primero en el player
            videoPlaylist.clear()
            videoPlaylist.addAll(uris)
            currentIndex = 0
            reproducirVideoActual()

            // Si seleccionó más de uno, preguntar si desea unirlos
            if (uris.size > 1) {
                mostrarDialogoUnirVideos(uris)
            }
        }
    }

    private fun mostrarDialogoUnirVideos(uris: List<Uri>) {
        val nombres = uris.map { uri ->
            requireContext().contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else uri.lastPathSegment ?: "Video" } ?: "Video"
        }

        val listaTexto = nombres.mapIndexed { i, nombre -> "${i + 1}. $nombre" }.joinToString("\n")

        AlertDialog.Builder(requireContext())
            .setTitle("¿Unir estos ${uris.size} videos?")
            .setMessage("Se unirán en este orden:\n\n$listaTexto\n\nEl resultado se guardará en Downloads.")
            .setPositiveButton("Unir") { _, _ ->
                unirVideos(uris, nombres)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun unirVideos(uris: List<Uri>, nombres: List<String>) {
        Toast.makeText(requireContext(), "Uniendo ${uris.size} videos...", Toast.LENGTH_LONG).show()

        Thread {
            try {
                // 1. Copiar cada video a caché
                val archivos = uris.mapIndexed { i, uri ->
                    cacheUriToFile(uri, "merge_input_$i.mp4")
                }

                // 2. Crear el archivo de lista para FFmpeg (formato concat)
                val listaFile = File(requireContext().cacheDir, "merge_list.txt")
                listaFile.writeText(archivos.joinToString("\n") { "file '${it.absolutePath}'" })

                // 3. Archivo de salida en Downloads
                val nombreSalida = "Video_Unido_${System.currentTimeMillis()}.mp4"
                val outputFile = File(requireContext().cacheDir, "merge_output.mp4")
                if (outputFile.exists()) outputFile.delete()

                // 4. Comando FFmpeg usando el demuxer concat (copia sin recodificar, rápido)
                val command = "-f concat -safe 0 -i ${listaFile.absolutePath} -c copy ${outputFile.absolutePath}"

                FFmpegKit.executeAsync(command) { session ->
                    if (ReturnCode.isSuccess(session.returnCode)) {
                        // 5. Guardar en Downloads vía MediaStore
                        val contentValues = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, nombreSalida)
                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                            }
                        }
                        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        } else {
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }
                        val destUri = requireContext().contentResolver.insert(collectionUri, contentValues)
                        if (destUri != null) {
                            requireContext().contentResolver.openOutputStream(destUri)?.use { out ->
                                outputFile.inputStream().use { it.copyTo(out) }
                            }
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), "✓ Video unido guardado en Downloads", Toast.LENGTH_LONG).show()
                            }
                        }
                        // Limpiar caché
                        archivos.forEach { it.delete() }
                        listaFile.delete()
                        outputFile.delete()
                    } else {
                        android.util.Log.e("FFmpegMerge", session.allLogsAsString)
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error al unir videos. Revisa Logcat.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FFmpegMerge", "Error preparando archivos: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error preparando los archivos", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun parseSrt(inputStream: java.io.InputStream) {
        subtitleList.clear()
        val lines = inputStream.bufferedReader().readLines()
        for (i in lines.indices) {
            if (lines[i].contains("-->")) {
                val times = lines[i].split(" --> ")
                val start = parseTimeToMillis(times[0].trim())
                val end = parseTimeToMillis(times[1].trim())
                if (i + 1 < lines.size) subtitleList.add(Subtitle(start, end, lines[i + 1]))
            }
        }
    }

    private fun parseTimeToMillis(time: String): Long {
        val parts = time.replace(",", ":").split(":")
        return (parts[0].toLong() * 3600000) + (parts[1].toLong() * 60000) + (parts[2].toLong() * 1000) + parts[3].toLong()
    }

    private fun loadVideosFromSelectedFolder(uri: Uri) {
        downloadVideoList.clear()
        val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), uri)
        pickedDir?.listFiles()?.forEach { file ->
            if (file.type?.startsWith("video/") == true) {
                downloadVideoList.add(Pair(file.name ?: "Video", file.uri))
            }
        }
        binding.homeContent.rvDownloads.adapter = DownloadVideoAdapter(downloadVideoList) { uri ->
            videoPlaylist.clear()
            videoPlaylist.addAll(downloadVideoList.map { it.second })
            currentIndex = downloadVideoList.indexOfFirst { it.second == uri }.coerceAtLeast(0)
            reproducirVideoActual()
        }
    }

    private fun loadVideosFromDownloads() {
        downloadVideoList.clear()
        val projection = arrayOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media._ID)
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        requireContext().contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn)
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                downloadVideoList.add(Pair(name, contentUri))
            }
        }
        binding.homeContent.rvDownloads.adapter = DownloadVideoAdapter(downloadVideoList) { uri ->
            videoPlaylist.clear()
            videoPlaylist.addAll(downloadVideoList.map { it.second })
            currentIndex = downloadVideoList.indexOfFirst { it.second == uri }.coerceAtLeast(0)
            reproducirVideoActual()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)
        binding.homeContent.rvDownloads.layoutManager = LinearLayoutManager(requireContext())

        val savedUri = loadSavedFolderUri()
        if (savedUri != null) {
            loadVideosFromSelectedFolder(savedUri)
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(requireContext(), permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                loadVideosFromDownloads()
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }

        binding.homeContent.tvSubtitleOverlay.setShadowLayer(3f, 2f, 2f, android.graphics.Color.BLACK)
        handler.post(updateSubtitleTask)
        setupListeners()
        setupVideoListeners()

        // NUEVO: gesto de doble tap sobre el video para entrar/salir de fullscreen
        fullscreenGestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                toggleFullscreen()
                return true
            }
        })
        binding.homeContent.videoContainer.setOnTouchListener { _, event ->
            fullscreenGestureDetector.onTouchEvent(event)
            true
        }

        binding.imageLayout.titleWelcome.text = String.format("%s", userName)
        enterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)
        reenterTransition = MaterialFadeThrough().addTarget(binding.contentContainer)

        checkForMargins()
        loadProfile()
        setupTitle()
        colorButtons()

        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        view.doOnLayout { adjustPlaylistButtons() }
    }

private fun setupVideoListeners() {
    // Selección de archivos y carpetas
    binding.homeContent.btnOpenFile.setOnClickListener { videoPickerLauncher.launch("video/*") }
    binding.homeContent.btnLoadSubtitles.setOnClickListener { subtitlePickerLauncher.launch("*/*") }
    binding.homeContent.btnChooseFolder.setOnClickListener {
        folderPickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
    }
    binding.homeContent.btnSelectAudio.setOnClickListener {
        audioPickerLauncher.launch("audio/*")
    }

    // SeekBar
    binding.homeContent.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) binding.homeContent.videoPlayer.seekTo(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })

    // Reproducción
    binding.homeContent.videoPlayer.setOnPreparedListener { mp ->
        mp.seekTo(savedPosition)
        mp.start()
        binding.homeContent.videoSeekBar.max = mp.duration
        binding.homeContent.tvTotalTime.text = formatTime(mp.duration)
    }

    binding.homeContent.btnPlayPause.setOnClickListener {
        val player = binding.homeContent.videoPlayer
        if (player.isPlaying) {
            player.pause()
            binding.homeContent.btnPlayPause.text = "PLAY"
        } else {
            player.start()
            binding.homeContent.btnPlayPause.text = "PAUSE"
        }
    }

    // Controles de navegación
    binding.homeContent.btnPrevVideo.setOnClickListener {
        if (currentIndex > 0) { currentIndex--; reproducirVideoActual() }
    }
    binding.homeContent.btnNextVideo.setOnClickListener {
        if (currentIndex < videoPlaylist.size - 1) { currentIndex++; reproducirVideoActual() }
    }
    binding.homeContent.btnRewindTime.setOnClickListener {
        binding.homeContent.videoPlayer.seekTo((binding.homeContent.videoPlayer.currentPosition - 5000).coerceAtLeast(0))
    }
    binding.homeContent.btnForwardTime.setOnClickListener {
        binding.homeContent.videoPlayer.seekTo((binding.homeContent.videoPlayer.currentPosition + 5000).coerceAtMost(binding.homeContent.videoPlayer.duration))
    }

    // Herramientas FFmpeg
    binding.homeContent.btnMixVideo.setOnClickListener { // MKV
        val subUri = selectedSubtitleUri
        if (videoPlaylist.isNotEmpty() && subUri != null) {
            createMkvWithSubtitles(videoPlaylist[currentIndex], subUri, selectedAudioUri)
        } else {
            Toast.makeText(requireContext(), "Selecciona video y subtítulos", Toast.LENGTH_SHORT).show()
        }
    }

    // NUEVO: el botón que antes era "AAC"/fullscreen (sin uso real) ahora limpia caché temporal
    binding.homeContent.btnFullscreen.setOnClickListener {
        limpiarCacheTemporal()
    }

    binding.homeContent.btnConvert.setOnClickListener { // A MP3
        // Asumiendo que tienes una función para convertir audio
        if (videoPlaylist.isNotEmpty()) {
            convertirAudiosAMp3(listOf(videoPlaylist[currentIndex]))
        }
    }

    // Corte de Video
    binding.homeContent.btnSetStart.setOnClickListener {
        binding.homeContent.etStartTime.setText(formatTime(binding.homeContent.videoPlayer.currentPosition))
    }
    binding.homeContent.btnSetEnd.setOnClickListener {
        binding.homeContent.etEndTime.setText(formatTime(binding.homeContent.videoPlayer.currentPosition))
    }
    binding.homeContent.btnSplit.setOnClickListener {
        val startTime = binding.homeContent.etStartTime.text.toString()
        val endTime = binding.homeContent.etEndTime.text.toString()
        if (startTime.isNotEmpty() && endTime.isNotEmpty() && videoPlaylist.isNotEmpty()) {
            splitVideo(videoPlaylist[currentIndex], startTime, endTime)
        } else {
            Toast.makeText(requireContext(), "Define los tiempos de corte", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    //   CONEXIÓN DE LOS NUEVOS BOTONES DE VIDEO AVANZADOS (PARCHE INTEGRADO)
    // =========================================================================
    // Botón "incrustar subtítulos": si ya hay un srt cargado, quema directo.
    // Si no hay ninguno, lo pide y el picker se encarga de disparar el burn
    // automáticamente en cuanto el usuario elige el archivo (ver pendingHardcodeBurn).
    binding.homeContent.btnHardcodeSubtitles.setOnClickListener {
        if (videoPlaylist.isEmpty()) {
            Toast.makeText(requireContext(), "Primero carga un video en el reproductor", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        if (selectedSubtitleUri != null) {
            hardcodearSubtitulos()
        } else {
            pendingHardcodeBurn = true
            subtitlePickerLauncher.launch("*/*")
        }
    }
    binding.homeContent.btnCreateVideoFromPhotos.setOnClickListener {
        photosPickerLauncher.launch("image/*")
    }

    binding.homeContent.btnMergeVideos.setOnClickListener {
        videoPickerLauncher.launch("video/*")
    }
}

    /**
     * NUEVA: limpia el .srt actualmente cargado (selectedSubtitleUri, subtitleList)
     * y el overlay visible del reproductor. Se llama cada vez que cambia el video
     * (reproducirVideoActual) y al terminar el proceso de burn (éxito o error),
     * para evitar que subtítulos de una sesión anterior sigan "pegados".
     */
    private fun clearSubtitles() {
        selectedSubtitleUri = null
        subtitleList.clear()
        _binding?.homeContent?.tvSubtitleOverlay?.text = ""
    }

    /**
     * NUEVA: alterna el modo fullscreen del reproductor. Bloquea orientación
     * landscape, oculta las barras del sistema y oculta el resto de la UI
     * (toolbar, playlists, filas de herramientas, lista de descargas), dejando
     * solo el video + sus controles de reproducción a pantalla completa.
     */
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        val activity = requireActivity()
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)

        if (isFullscreen) {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        setUiVisibilityForFullscreen(isFullscreen)
    }

    /**
     * NUEVA: muestra/oculta los bloques de UI que no son el reproductor cuando
     * se activa/desactiva el modo fullscreen. Requiere que en home_content.xml
     * la fila de herramientas tenga id "toolsRow", la fila de corte tenga id
     * "cutRow", y el contenedor vertical de las 3 acciones extra tenga id
     * "extraActionsContainer" (ver mensaje anterior con los ids agregados).
     */
    private fun setUiVisibilityForFullscreen(fullscreen: Boolean) {
        val visibility = if (fullscreen) View.GONE else View.VISIBLE
        binding.appBarLayout.visibility = visibility
        binding.homeContent.absPlaylists.root.visibility = visibility
        binding.homeContent.toolsRow.visibility = visibility
        binding.homeContent.cutRow.visibility = visibility
        binding.homeContent.extraActionsContainer.visibility = visibility
        binding.homeContent.rvDownloads.visibility = visibility

        binding.homeContent.videoContainer.layoutParams.height = if (fullscreen) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            (250 * resources.displayMetrics.density).toInt()
        }
        binding.homeContent.videoContainer.requestLayout()
    }

    /**
     * NUEVA: borra todos los archivos/carpetas temporales de cacheDir generados
     * por las funciones de FFmpeg (inputs/outputs de burn, split, merge, mkv, etc.),
     * conservando la carpeta "subtitle_fonts" (caché intencional de la fuente
     * usada por drawtext). Conectada al botón que antes era "AAC".
     */
    private fun limpiarCacheTemporal() {
        Thread {
            var espacioLiberado = 0L
            try {
                requireContext().cacheDir.listFiles()?.forEach { file ->
                    if (file.name == "subtitle_fonts") return@forEach
                    if (file.isFile) {
                        espacioLiberado += file.length()
                        file.delete()
                    } else if (file.isDirectory) {
                        espacioLiberado += file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        file.deleteRecursively()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LimpiarCache", "Error: ${e.message}")
            }
            val mb = espacioLiberado / (1024.0 * 1024.0)
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Caché limpiada: %.1f MB liberados".format(mb), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun reproducirVideoActual() {
        if (videoPlaylist.isNotEmpty()) {
            clearSubtitles()
            savedPosition = 0
            binding.homeContent.videoPlayer.setVideoURI(videoPlaylist[currentIndex])
            binding.homeContent.videoPlayer.start()
            binding.homeContent.btnPlayPause.text = "Pause"
        }
    }

    /**
     * NUEVA: copia la fuente embebida (res/raw/roboto_regular.ttf) a una carpeta
     * privada de caché que contiene ÚNICAMENTE esa fuente. Usada por el filtro
     * drawtext en lugar del filtro subtitles=/libass, para evitar depender del
     * escaneo/fuzzy-matching de las ~300 fuentes de /system/fonts en Android.
     */
    private fun getFontDir(): File {
        val fontDir = File(requireContext().cacheDir, "subtitle_fonts")
        if (!fontDir.exists()) fontDir.mkdirs()
        val fontFile = File(fontDir, "roboto_regular.ttf")
        if (!fontFile.exists()) {
            resources.openRawResource(R.raw.roboto_regular).use { input ->
                fontFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return fontDir
    }

    /**
     * Construye la cadena de filtros drawtext (uno por cada línea de subtítulo,
     * encadenados por comas) para quemar el texto directamente en los píxeles
     * del video, sin depender de libass/fontselect.
     */
private fun buildDrawtextFilters(subtitles: List<Subtitle>, fontFile: String): String {
    return subtitles.joinToString(",") { sub ->
        val safeText = sub.text
            .replace("\\", "\\\\")
            .replace("'", "\u2019") // comilla tipográfica en vez de escapar la simple:
                                     // evita el bug del parser con \' vía filter_script
            .replace(":", "\\:")
            .replace(",", "\\,")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("%", "\\%")
            .replace("{", "\\{")
            .replace("}", "\\}")
        val startSec = sub.startTime / 1000.0
        val endSec = sub.endTime / 1000.0
        "drawtext=fontfile=$fontFile:text='$safeText':enable='between(t,$startSec,$endSec)':x=(w-text_w)/2:y=h-th-50:fontsize=24:fontcolor=white:shadowcolor=black:shadowx=2:shadowy=2"
    }
}

    private fun formatTime(millis: Int): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun adjustPlaylistButtons() {
        val buttons = listOf(
            binding.homeContent.absPlaylists.history,
            binding.homeContent.absPlaylists.lastAdded,
            binding.homeContent.absPlaylists.topPlayed,
            binding.homeContent.absPlaylists.actionShuffle
        )
        buttons.maxOf { it.lineCount }.let { maxLineCount -> buttons.forEach { it.setLines(maxLineCount) } }
    }

    private fun setupListeners() {
        binding.imageLayout.bannerImage?.setOnClickListener {
            findNavController().navigate(R.id.user_info_fragment, null, null, FragmentNavigatorExtras(binding.imageLayout.userImage to "user_image"))
            reenterTransition = null
        }
        binding.homeContent.absPlaylists.lastAdded.setOnClickListener {
            findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to LAST_ADDED_PLAYLIST))
            setSharedAxisYTransitions()
        }
        binding.homeContent.absPlaylists.topPlayed.setOnClickListener {
            findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to TOP_PLAYED_PLAYLIST))
            setSharedAxisYTransitions()
        }
        binding.homeContent.absPlaylists.actionShuffle.setOnClickListener { libraryViewModel.shuffleSongs() }
        binding.homeContent.absPlaylists.history.setOnClickListener {
            findNavController().navigate(R.id.detailListFragment, bundleOf(EXTRA_PLAYLIST_TYPE to HISTORY_PLAYLIST))
            setSharedAxisYTransitions()
        }
        // Botón para seleccionar audios
        binding.homeContent.btnSelectAudio.setOnClickListener {
            multiaudioPickerLauncher.launch("audio/*")
        }

        // Botón para convertir
        binding.homeContent.btnConvert.setOnClickListener {
            if (selectedAudioUris.isNotEmpty()) {
                convertirAudiosAMp3(selectedAudioUris)
            } else {
                Toast.makeText(requireContext(), "Selecciona audios primero", Toast.LENGTH_SHORT).show()
            }
        }
        binding.imageLayout.userImage.setOnClickListener {
            findNavController().navigate(R.id.user_info_fragment, null, null, FragmentNavigatorExtras(binding.imageLayout.userImage to "user_image"))
        }
    }

    private fun setupTitle() {
        binding.appBarLayout.toolbar.setNavigationOnClickListener { findNavController().navigate(R.id.action_search, null, navOptions) }
        binding.appBarLayout.title = getString(R.string.app_name)
    }

    private fun loadProfile() {
        binding.imageLayout.bannerImage?.let {
            Glide.with(requireContext()).load(RetroGlideExtension.getBannerModel()).profileBannerOptions(RetroGlideExtension.getBannerModel()).into(it)
        }
        Glide.with(requireActivity()).load(RetroGlideExtension.getUserModel()).userProfileOptions(RetroGlideExtension.getUserModel(), requireContext()).into(binding.imageLayout.userImage)
    }

    fun colorButtons() {
        binding.homeContent.absPlaylists.history.elevatedAccentColor()
        binding.homeContent.absPlaylists.lastAdded.elevatedAccentColor()
        binding.homeContent.absPlaylists.topPlayed.elevatedAccentColor()
        binding.homeContent.absPlaylists.actionShuffle.elevatedAccentColor()
    }

    private fun checkForMargins() {
        if (mainActivity.isBottomNavVisible) {
            binding.container.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = dip(R.dimen.bottom_nav_height)
            }
        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_main, menu)
        menu.removeItem(R.id.action_grid_size)
        menu.removeItem(R.id.action_layout_type)
        menu.removeItem(R.id.action_sort_order)
        menu.findItem(R.id.action_settings)?.setShowAsAction(1)
        val toolbar = binding.appBarLayout.toolbar
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(requireContext(), toolbar, menu, ATHToolbarActivity.getToolbarBackgroundColor(toolbar))
    }

    override fun scrollToTop() {
        binding.container.scrollTo(0, 0)
        binding.appBarLayout.setExpanded(true)
    }

    fun setSharedAxisYTransitions() {
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.Y, true).addTarget(CoordinatorLayout::class.java)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Y, false)
    }

    private fun saveFolderUri(uri: Uri) {
        requireContext().getSharedPreferences("video_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putString(PREF_SELECTED_FOLDER_URI, uri.toString()).apply()
    }

    private fun loadSavedFolderUri(): Uri? {
        val uriString = requireContext().getSharedPreferences("video_prefs", android.content.Context.MODE_PRIVATE)
            .getString(PREF_SELECTED_FOLDER_URI, null)
        return uriString?.let { Uri.parse(it) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val visibility = if (isLandscape) View.GONE else View.VISIBLE
        binding.appBarLayout.visibility = visibility
        binding.homeContent.videoSeekBar.visibility = visibility
        binding.homeContent.btnPrevVideo.visibility = visibility
        binding.homeContent.btnRewindTime.visibility = visibility
        binding.homeContent.btnForwardTime.visibility = visibility
        binding.homeContent.btnNextVideo.visibility = visibility
        binding.homeContent.btnPlayPause.visibility = visibility
        binding.homeContent.btnFullscreen.visibility = visibility
        binding.homeContent.btnOpenFile.visibility = visibility
        binding.homeContent.btnLoadSubtitles.visibility = visibility
        binding.homeContent.tvCurrentTime.visibility = visibility
        binding.homeContent.tvTotalTime.visibility = visibility
        binding.homeContent.btnChooseFolder.visibility = visibility
        binding.homeContent.videoContainer.layoutParams.height = if (isLandscape) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            (250 * resources.displayMetrics.density).toInt()
        }
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> { findNavController().navigate(R.id.settings_fragment, null, navOptions); true }
            R.id.action_import_playlist -> { ImportPlaylistDialog().show(childFragmentManager, "ImportPlaylist"); true }
            R.id.action_add_to_playlist -> { CreatePlaylistDialog.create(emptyList()).show(childFragmentManager, "ShowCreatePlaylistDialog"); true }
            else -> false
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        ToolbarContentTintHelper.handleOnPrepareOptionsMenu(requireActivity(), binding.appBarLayout.toolbar)
    }

    override fun onResume() {
        super.onResume()
        checkForMargins()
        exitTransition = null
    }

    override fun onDestroyView() {
        // NUEVO: si salimos de la pantalla estando en fullscreen, restauramos
        // orientación y barras del sistema para no dejar la Activity "atascada"
        if (isFullscreen) {
            requireActivity().requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.getInsetsController(requireActivity().window, requireActivity().window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        if (binding.homeContent.videoPlayer.isPlaying) {
            savedPosition = binding.homeContent.videoPlayer.currentPosition
        }
        handler.removeCallbacks(updateSubtitleTask)
        binding.homeContent.videoPlayer.stopPlayback()
        _binding = null
        super.onDestroyView()
    }

    private fun createMkvWithSubtitles(videoUri: Uri, subtitleUri: Uri, audioUri: Uri? = null) {
        val videoFile = cacheUriToFile(videoUri, "input_video.mp4")
        val subFile = cacheUriToFile(subtitleUri, "input_sub.srt")
        val fileName = "Video_Subtitulado_${System.currentTimeMillis()}.mkv"

        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/x-matroska")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val resolver = requireContext().contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collectionUri, contentValues)

        if (uri != null) {
            val outputFile = File(requireContext().cacheDir, "temp_output.mkv")
            if (outputFile.exists()) outputFile.delete()

            val command = if (audioUri != null) {
                val audioFile = cacheUriToFile(audioUri, "input_audio.mp3")
                "-i ${videoFile.absolutePath} -i ${subFile.absolutePath} -i ${audioFile.absolutePath} " +
                        "-map 0:v -map 2:a -map 1:s " +
                        "-c copy -c:s srt -disposition:a:0 default -disposition:s:0 default ${outputFile.absolutePath}"
            } else {
                "-i ${videoFile.absolutePath} -i ${subFile.absolutePath} -c copy -c:s srt -disposition:s:0 default ${outputFile.absolutePath}"
            }

            FFmpegKit.executeAsync(command) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    try {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            outputFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Guardado en Downloads", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FFmpegError", "Error al copiar archivo: ${e.message}")
                    }
                } else {
                    android.util.Log.e("FFmpegError", session.allLogsAsString)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error en FFmpeg", Toast.LENGTH_SHORT).show()
                    }
                }
                // Limpieza de temporales de esta función (antes no se borraban)
                videoFile.delete()
                subFile.delete()
                audioUri?.let { File(requireContext().cacheDir, "input_audio.mp3").delete() }
                if (outputFile.exists()) outputFile.delete()
            }
        }
    }

    /**
     * Incrusta (quema) los subtítulos directamente en los píxeles del video usando
     * el filtro drawtext (uno por línea de subtítulo), en lugar de subtitles=/libass.
     * Usa la fuente embebida en res/raw/roboto_regular.ttf, copiada a una carpeta
     * de caché exclusiva vía getFontDir().
     */
private fun hardcodearSubtitulos() {
    android.util.Log.d("HardcodeDebug", "hardcodearSubtitulos() llamada")
    val subUri = selectedSubtitleUri
    if (videoPlaylist.isEmpty() || subUri == null) {
        Toast.makeText(requireContext(), "Selecciona un video y carga un .srt primero (SRT)", Toast.LENGTH_SHORT).show()
        return
    }
    Toast.makeText(requireContext(), "Incrustando subtítulos, puede tardar...", Toast.LENGTH_LONG).show()

    val videoUri = videoPlaylist[currentIndex]
    Thread {
        val videoFile = cacheUriToFile(videoUri, "input_hardcode.mp4")
        val fileName = "Video_Subtitulado_${System.currentTimeMillis()}.mp4"
        val outputFile = File(requireContext().cacheDir, "output_hardcode.mp4")
        if (outputFile.exists()) outputFile.delete()

        // fontFile SIN comillas alrededor: solo escapamos los dos puntos,
        // que es lo único que el filtro drawtext necesita escapado en la ruta.
        val fontFile = File(getFontDir(), "roboto_regular.ttf").absolutePath
            .replace(":", "\\:")

        val drawtextFilter = buildDrawtextFilters(subtitleList, fontFile)

        if (drawtextFilter.isBlank()) {
            android.util.Log.e("FFmpegHardcode", "subtitleList está vacía, no se generó ningún filtro drawtext")
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "No hay subtítulos cargados para incrustar", Toast.LENGTH_SHORT).show()
            }
            videoFile.delete()
            return@Thread
        }

        // Escribimos el filtro a un archivo: así el -vf nunca contiene espacios
        // ni comillas dentro del comando, sin importar cuánto texto tenga el srt.
        val filterScriptFile = File(requireContext().cacheDir, "drawtext_filter.txt")
        filterScriptFile.writeText(drawtextFilter)

        // SIN comillas de ningún tipo en ninguna parte del comando.
        val command = "-y -i ${videoFile.absolutePath} -filter_script:v ${filterScriptFile.absolutePath} -c:v mpeg4 -q:v 2 -c:a copy ${outputFile.absolutePath}"
        android.util.Log.d("FFmpegHardcode", "Comando: $command")
        android.util.Log.d("FFmpegHardcode", "Contenido del filtro: $drawtextFilter")

        FFmpegKit.executeAsync(command) { session ->
            if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                saveToDownloads(outputFile, fileName, "video/mp4")
            } else {
                android.util.Log.e("FFmpegHardcode", session.allLogsAsString)
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al incrustar subtítulos (revisa Logcat)", Toast.LENGTH_SHORT).show()
                }
            }
            requireActivity().runOnUiThread {
                clearSubtitles()
            }
            videoFile.delete()
            filterScriptFile.delete()
            if (outputFile.exists()) outputFile.delete()
        }
    }.start()
}
    /**
     * NUEVA FUNCIÓN: crea un video tipo diapositivas a partir de una lista de fotos.
     * 3 segundos por foto, 720p, con relleno (pad) para fotos de distinta proporción.
     * BOTÓN FUTURO sugerido: btnCreateVideoFromPhotos en home_content.xml.
     */
    private fun crearVideoDesdeFotos(uris: List<Uri>) {
        Toast.makeText(requireContext(), "Creando video desde ${uris.size} fotos...", Toast.LENGTH_LONG).show()

        Thread {
            try {
                val carpetaTemp = File(requireContext().cacheDir, "slideshow_${System.currentTimeMillis()}").apply { mkdirs() }
                uris.forEachIndexed { index, uri ->
                    val destino = File(carpetaTemp, "img%03d.jpg".format(index))
                    requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destino).use { output -> input.copyTo(output) }
                    }
                }

                val fileName = "Slideshow_${System.currentTimeMillis()}.mp4"
                val outputFile = File(requireContext().cacheDir, "output_slideshow.mp4")
                if (outputFile.exists()) outputFile.delete()

                // 3 segundos por foto (1/3 fps de entrada), escalado a 720p con relleno
                val command = "-framerate 1/3 -i ${carpetaTemp.absolutePath}/img%03d.jpg " +
                        "-vf scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,fps=30 " +
                        "-c:v libx264 -pix_fmt yuv420p ${outputFile.absolutePath}"

                FFmpegKit.executeAsync(command) { session ->
                    if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                        saveToDownloads(outputFile, fileName, "video/mp4")
                    } else {
                        android.util.Log.e("FFmpegSlideshow", session.allLogsAsString)
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Error al crear el video desde fotos", Toast.LENGTH_SHORT).show()
                        }
                    }
                    carpetaTemp.deleteRecursively()
                    if (outputFile.exists()) outputFile.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("FFmpegSlideshow", "Error preparando fotos: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error preparando las fotos", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun splitVideo(videoUri: Uri, startTime: String, endTime: String) {
        val videoFile = cacheUriToFile(videoUri, "input_split.mp4")
        val fileName = "Clip_${System.currentTimeMillis()}.mp4"

        // 1. Configurar los valores para guardarlo en Descargas (Downloads)
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val resolver = requireContext().contentResolver
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val destUri = resolver.insert(collectionUri, contentValues)

        if (destUri != null) {
            val outputFile = File(requireContext().cacheDir, "output_split.mp4")

            // 2. Comando FFmpeg
            val command = "-i ${videoFile.absolutePath} -ss $startTime -to $endTime -c copy ${outputFile.absolutePath}"

            FFmpegKit.executeAsync(command) { session ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    // 3. Copiar el archivo desde caché a la URI de Descargas
                    try {
                        resolver.openOutputStream(destUri)?.use { outputStream ->
                            outputFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        requireActivity().runOnUiThread {
                            Toast.makeText(requireContext(), "Clip guardado en Downloads", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("FFmpegError", "Error al copiar: ${e.message}")
                    }
                } else {
                    android.util.Log.e("FFmpegError", session.allLogsAsString)
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "Error al cortar", Toast.LENGTH_SHORT).show()
                    }
                }
                // Limpieza de temporales de esta función (antes no se borraban)
                videoFile.delete()
                if (outputFile.exists()) outputFile.delete()
            }
        }
    }


    private fun cacheUriToFile(uri: Uri, name: String): File {
        val file = File(requireContext().cacheDir, name)
        if (file.exists()) file.delete()
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return file
    }

    private fun saveToDownloads(file: File, fileName: String, mimeType: String = "video/mp4") {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val isAudio = mimeType.startsWith("audio/")
        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else if (isAudio) {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = requireContext().contentResolver.insert(collectionUri, contentValues)

        if (uri != null) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Guardado en Descargas: $fileName", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SaveToDownloads", "Error copiando $fileName: ${e.message}")
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error al guardar $fileName", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            android.util.Log.e("SaveToDownloads", "insert() devolvió null para $fileName (mime=$mimeType)")
            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "No se pudo crear $fileName en Descargas", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // --- NUEVAS FUNCIONES PARA BINARIO ESTÁTICO ---

    private fun getFFmpegFromDownloads(context: android.content.Context): String? {
        // CAMBIA ESTA LÍNEA: de filesDir a codeCacheDir
        val destinationFile = File(context.codeCacheDir, "ffmpeg")

        if (destinationFile.exists() && destinationFile.canExecute()) {
            return destinationFile.absolutePath
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val sourceFile = File(downloadsDir, "ffmpeg")

        if (sourceFile.exists()) {
            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output -> input.copyTo(output) }
            }

            // Es vital intentar darle permisos de ejecución
            destinationFile.setExecutable(true, false) // false significa que es para todos

            return destinationFile.absolutePath
        }
        return null
    }

    private fun runManualFFmpeg(commandArgs: String, onComplete: (Boolean, String) -> Unit) {
        val binaryPath = getFFmpegFromDownloads(requireContext())
        if (binaryPath == null) {
            onComplete(false, "Binario 'ffmpeg' no encontrado en Descargas")
            return
        }

        val fullCommand = listOf(binaryPath) + commandArgs.split(" ")

        Thread {
            try {
                val process = ProcessBuilder(fullCommand)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().readText()
                process.waitFor()

                if (process.exitValue() == 0) {
                    onComplete(true, "Éxito")
                } else {
                    onComplete(false, output)
                }
            } catch (e: Exception) {
                onComplete(false, e.message ?: "Error desconocido")
            }
        }.start()
    }

    private fun convertirAudiosAMp3(uris: List<Uri>) {
        Toast.makeText(requireContext(), "Iniciando conversión masiva...", Toast.LENGTH_LONG).show()

        Thread {
            var exitosos = 0
            var fallidos = 0

            uris.forEachIndexed { index, uri ->
                val fileName = "MP3_${System.currentTimeMillis()}_$index.mp3"
                val inputFile = cacheUriToFile(uri, "temp_input_audio_$index.tmp")

                if (!inputFile.exists() || inputFile.length() == 0L) {
                    android.util.Log.e("ConvertMp3", "Archivo de entrada vacío o inexistente: $uri")
                    fallidos++
                    return@forEachIndexed
                }

                val outputFile = File(requireContext().cacheDir, "output_temp_$index.mp3")
                if (outputFile.exists()) outputFile.delete()

                val command = "-i ${inputFile.absolutePath} -c:a libmp3lame -q:a 2 ${outputFile.absolutePath}"
                val session = FFmpegKit.execute(command)

                if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0) {
                    saveToDownloads(outputFile, fileName, "audio/mpeg")
                    exitosos++
                } else {
                    fallidos++
                    android.util.Log.e("ConvertMp3", "FALLÓ para $uri: ${session.allLogsAsString}")
                }

                if (inputFile.exists()) inputFile.delete()
                if (outputFile.exists()) outputFile.delete()
            }

            requireActivity().runOnUiThread {
                Toast.makeText(requireContext(), "Conversión: $exitosos ok, $fallidos fallidos", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    companion object {
        const val PREF_SELECTED_FOLDER_URI = "pref_selected_folder_uri"
        const val TAG: String = "BannerHomeFragment"
        @JvmStatic fun newInstance(): HomeFragment = HomeFragment()
    }
}

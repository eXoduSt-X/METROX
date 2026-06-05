/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.fragments.lyrics

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.transition.Fade
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.tageditor.TagWriter
import code.name.monkey.retromusic.databinding.FragmentLyricsBinding
import code.name.monkey.retromusic.extensions.accentColor
import code.name.monkey.retromusic.extensions.openUrl
import code.name.monkey.retromusic.extensions.uri
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.lyrics.LrcView
import code.name.monkey.retromusic.model.AudioTagInfo
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.FileUtils
import code.name.monkey.retromusic.util.LyricUtil
import code.name.monkey.retromusic.util.UriUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min

class LyricsFragment : AbsMainActivityFragment(R.layout.fragment_lyrics),
    MusicProgressViewUpdateHelper.Callback {

    private var _binding: FragmentLyricsBinding? = null
    private val binding get() = _binding!!
    private lateinit var song: Song

    private lateinit var normalLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var editSyncedLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var cacheFile: File
    private var syncedLyrics: String = ""
    private lateinit var syncedFileUri: Uri

    private var lyricsType: LyricsType = LyricsType.NORMAL_LYRICS

    private val googleSearchLrcUrl: String
        get() {
            var baseUrl = "http://www.google.com/search?"
            var query = song.title + "+" + song.artistName
            query = "q=" + query.replace(" ", "+") + " lyrics"
            baseUrl += query
            return baseUrl
        }

    private lateinit var updateHelper: MusicProgressViewUpdateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        normalLyricsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    FileUtils.copyFileToUri(requireContext(), cacheFile, song.uri)
                }
            }
        editSyncedLyricsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    requireContext().contentResolver.openOutputStream(syncedFileUri)?.use { os ->
                        (os as FileOutputStream).channel.truncate(0)
                        os.write(syncedLyrics.toByteArray())
                        os.flush()
                    }
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enterTransition = Fade()
        exitTransition = Fade()
        _binding = FragmentLyricsBinding.bind(view)
        
        updateHelper = MusicProgressViewUpdateHelper(this, 50, 50)
        
        updateTitleSong()
        setupLyricsView()
        loadLyrics()

        setupWakelock()
        setupViews()
        setupToolbar()
        setupSincroControls()
    }

    private fun setupLyricsView() {
        binding.lyricsView.apply {
            setCurrentColor(accentColor())
            setTimeTextColor(accentColor())
            setTimelineColor(accentColor())
            setTimelineTextColor(accentColor())
            setDraggable(true, LrcView.OnPlayClickListener {
                MusicPlayerRemote.seekTo(it.toInt())
                return@OnPlayClickListener true
            })
        }
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.lyricsView.updateTime(progress.toLong())
        binding.tvCurrentTime.text = formatTimeLrc(progress)
        binding.btnPlayPause.text = if (MusicPlayerRemote.isPlaying) "Pause" else "Play"
    }

    private fun setupViews() {
        binding.saveFab.accentColor()
        
        val currentContent = if (lyricsType == LyricsType.SYNCED_LYRICS) {
            LyricUtil.getStringFromLrc(LyricUtil.getSyncedLyricsFile(song)) ?: getEmbeddedLyricsText()
        } else {
            getEmbeddedLyricsText()
        }
        binding.etLyrics.setText(currentContent)

        binding.saveFab.setOnClickListener {
            val outputText = binding.etLyrics.text.toString()
            if (lyricsType == LyricsType.SYNCED_LYRICS || outputText.contains(Regex("\\[\\d{2}:\\d{2}\\.\\d{2}\\]"))) {
                saveSyncedLyricsData(outputText)
            } else {
                saveNormalLyricsData(outputText)
            }
        }
    }

    private fun setupSincroControls() {
        binding.btnPlayPause.text = if (MusicPlayerRemote.isPlaying) "Pause" else "Play"

        binding.btnPlayPause.setOnClickListener {
            // Verificado: Se usan los métodos reales de tu MusicPlayerRemote.kt
            if (MusicPlayerRemote.isPlaying) {
                MusicPlayerRemote.pauseSong()
            } else {
                MusicPlayerRemote.resumePlaying()
            }
            
            binding.btnPlayPause.postDelayed({
                binding.btnPlayPause.text = if (MusicPlayerRemote.isPlaying) "Pause" else "Play"
            }, 100)
        }

        binding.btnRew.setOnClickListener {
            val newPos = MusicPlayerRemote.position - 5000
            MusicPlayerRemote.seekTo(max(newPos, 0))
            binding.lyricsView.updateTime(MusicPlayerRemote.position.toLong())
        }

        binding.btnFwd.setOnClickListener {
            val newPos = MusicPlayerRemote.position + 5000
            MusicPlayerRemote.seekTo(min(newPos, MusicPlayerRemote.songDurationMillis))
            binding.lyricsView.updateTime(MusicPlayerRemote.position.toLong())
        }

        binding.btnMark.setOnClickListener {
            handleMarking()
            binding.lyricsView.loadLrc(binding.etLyrics.text.toString())
            binding.lyricsView.updateTime(MusicPlayerRemote.position.toLong())
        }

        binding.btnLeft.setOnClickListener {
            val pos = binding.etLyrics.selectionStart
            if (pos > 0) binding.etLyrics.setSelection(pos - 1)
        }

        binding.btnRight.setOnClickListener {
            val pos = binding.etLyrics.selectionStart
            if (pos < binding.etLyrics.text.length) binding.etLyrics.setSelection(pos + 1)
        }

        binding.btnUp.setOnClickListener { moveCursorLine(-1) }
        binding.btnDown.setOnClickListener { moveCursorLine(1) }
    }

    private fun moveCursorLine(direction: Int) {
        val pos = binding.etLyrics.selectionStart
        val text = binding.etLyrics.text.toString()
        if (text.isEmpty()) return

        val currentLineStart = text.lastIndexOf("\n", pos - 1) + 1
        var currentLineEnd = text.indexOf("\n", pos)
        if (currentLineEnd == -1) currentLineEnd = text.length

        val column = pos - currentLineStart

        if (direction == -1) {
            if (currentLineStart <= 0) return
            val prevLineStart = text.lastIndexOf("\n", currentLineStart - 2) + 1
            val prevLineEnd = currentLineStart - 1
            val prevLineLength = prevLineEnd - prevLineStart
            val targetPos = prevLineStart + min(column, prevLineLength)
            binding.etLyrics.setSelection(targetPos)
        } else if (direction == 1) {
            if (currentLineEnd >= text.length) return
            val nextLineStart = currentLineEnd + 1
            var nextLineEnd = text.indexOf("\n", nextLineStart)
            if (nextLineEnd == -1) nextLineEnd = text.length
            val nextLineLength = nextLineEnd - nextLineStart
            val targetPos = nextLineStart + min(column, nextLineLength)
            binding.etLyrics.setSelection(targetPos)
        }
    }

    private fun handleMarking() {
        val pos = binding.etLyrics.selectionStart
        val text = binding.etLyrics.text.toString().replace("\r\n", "\n").replace("\r", "\n")
        if (text.isEmpty()) return

        val lineStart = text.lastIndexOf("\n", pos - 1) + 1
        var lineEnd = text.indexOf("\n", pos)
        if (lineEnd == -1) lineEnd = text.length

        val fullLine = text.substring(lineStart, lineEnd)
        val cleanLine = if (fullLine.matches("^\\[\\d{2}:\\d{2}\\.\\d{2}\\].*".toRegex())) {
            fullLine.substring(10).trim()
        } else {
            fullLine.trim()
        }

        val timeStamp = formatTimeLrc(MusicPlayerRemote.position)
        val newLine = "$timeStamp $cleanLine"

        val updatedText = text.substring(0, lineStart) + newLine + text.substring(lineEnd)
        binding.etLyrics.setText(updatedText)

        val nextLinePos = lineStart + newLine.length + 1
        if (nextLinePos <= updatedText.length) {
            binding.etLyrics.setSelection(nextLinePos)
        } else {
            binding.etLyrics.setSelection(updatedText.length)
        }
    }

    private fun formatTimeLrc(ms: Int): String {
        val m = (ms / 1000) / 60
        val s = (ms / 1000) % 60
        val mm = (ms % 1000) / 10
        return String.format("[%02d:%02d.%02d]", m, s, mm)
    }

    private fun getEmbeddedLyricsText(): String {
        return try {
            val file = File(song.data)
            AudioFileIO.read(file).tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            ""
        }
    }

    private fun saveNormalLyricsData(input: String) {
        val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java)
        fieldKeyValueMap[FieldKey.LYRICS] = input
        GlobalScope.launch {
            if (VersionUtils.hasR()) {
                cacheFile = TagWriter.writeTagsToFilesR(
                    requireContext(), AudioTagInfo(
                        listOf(song.data), fieldKeyValueMap, null
                    )
                )[0]
                val pendingIntent = MediaStore.createWriteRequest(
                    requireContext().contentResolver,
                    listOf(song.uri)
                )
                normalLyricsLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            } else {
                TagWriter.writeTagsToFiles(
                    requireContext(), AudioTagInfo(listOf(song.data), fieldKeyValueMap, null)
                )
                activity?.runOnUiThread { loadNormalLyrics() }
            }
        }
    }

    private fun saveSyncedLyricsData(input: String) {
        if (VersionUtils.hasR()) {
            syncedLyrics = input
            val lrcFile = LyricUtil.getSyncedLyricsFile(song)
            if (lrcFile?.exists() == true) {
                syncedFileUri = UriUtil.getUriFromPath(requireContext(), lrcFile.absolutePath)
                val pendingIntent = MediaStore.createWriteRequest(
                    requireContext().contentResolver,
                    listOf(syncedFileUri)
                )
                editSyncedLyricsLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
            } else {
                val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java)
                fieldKeyValueMap[FieldKey.LYRICS] = input
                GlobalScope.launch {
                    cacheFile = TagWriter.writeTagsToFilesR(
                        requireContext(),
                        AudioTagInfo(listOf(song.data), fieldKeyValueMap, null)
                    )[0]
                    val pendingIntent = MediaStore.createWriteRequest(
                        requireContext().contentResolver,
                        listOf(song.uri)
                    )
                    normalLyricsLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                }
            }
        } else {
            LyricUtil.writeLrc(song, input)
            loadLRCLyrics()
        }
    }

    private fun loadNormalLyrics() {
        val lyrics = getEmbeddedLyricsText()
        binding.normalLyrics.isVisible = !lyrics.isNullOrEmpty()
        binding.noLyricsFound.isVisible = lyrics.isNullOrEmpty()
        binding.normalLyrics.text = lyrics
    }

    private fun loadLRCLyrics(): Boolean {
        val lrcFile = LyricUtil.getSyncedLyricsFile(song)
        if (lrcFile != null) {
            binding.lyricsView.loadLrc(lrcFile)
        } else {
            val embeddedLyrics = LyricUtil.getEmbeddedSyncedLyrics(song.data)
            if (embeddedLyrics != null) {
                binding.lyricsView.loadLrc(embeddedLyrics)
            } else {
                binding.lyricsView.setLabel(getString(R.string.empty))
                return false
            }
        }
        return true
    }

    private fun loadLyrics() {
        lyricsType = if (!loadLRCLyrics()) {
            binding.lyricsView.isVisible = false
            loadNormalLyrics()
            LyricsType.NORMAL_LYRICS
        } else {
            binding.normalLyrics.isVisible = false
            binding.noLyricsFound.isVisible = false
            binding.lyricsView.isVisible = true
            LyricsType.SYNCED_LYRICS
        }
        
        val currentContent = if (lyricsType == LyricsType.SYNCED_LYRICS) {
            LyricUtil.getStringFromLrc(LyricUtil.getSyncedLyricsFile(song)) ?: getEmbeddedLyricsText()
        } else {
            getEmbeddedLyricsText()
        }
        binding.etLyrics.setText(currentContent)
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateTitleSong()
        loadLyrics()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateTitleSong()
        loadLyrics()
    }

    private fun updateTitleSong() {
        song = MusicPlayerRemote.currentSong
    }

    private fun setupToolbar() {
        mainActivity.setSupportActionBar(binding.toolbar)
        ToolbarContentTintHelper.colorBackButton(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupWakelock() {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_lyrics, menu)
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(),
            binding.toolbar,
            menu,
            ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar)
        )
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_search) {
            openUrl(googleSearchLrcUrl)
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        updateHelper.start()
    }

    override fun onPause() {
        super.onPause()
        updateHelper.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerRemote.playingQueue.isNotEmpty())
            mainActivity.expandPanel()
        _binding = null
    }

    enum class LyricsType {
        NORMAL_LYRICS,
        SYNCED_LYRICS
    }
    }
    

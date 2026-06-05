    private fun setupSincroControls() {
        binding.btnPlayPause.text = if (MusicPlayerRemote.isPlaying) "Pause" else "Play"

        // Desactivamos el foco de los botones superiores restantes
        binding.btnRew.isFocusable = false
        binding.btnRew.isFocusableInTouchMode = false
        binding.btnFwd.isFocusable = false
        binding.btnFwd.isFocusableInTouchMode = false
        binding.btnMark.isFocusable = false
        binding.btnMark.isFocusableInTouchMode = false
        binding.btnPlayPause.isFocusable = false
        binding.btnPlayPause.isFocusableInTouchMode = false

        binding.btnPlayPause.setOnClickListener {
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
            val newPos = max(currentProgressMillis - 5000, 0)
            MusicPlayerRemote.seekTo(newPos)
            currentProgressMillis = newPos
            binding.lyricsView.updateTime(newPos.toLong())
            binding.tvCurrentTime.text = formatTimeLrc(newPos)
        }

        binding.btnFwd.setOnClickListener {
            val duration = if (MusicPlayerRemote.songDurationMillis > 0) MusicPlayerRemote.songDurationMillis else 0
            val newPos = min(currentProgressMillis + 5000, duration)
            MusicPlayerRemote.seekTo(newPos)
            currentProgressMillis = newPos
            binding.lyricsView.updateTime(newPos.toLong())
            binding.tvCurrentTime.text = formatTimeLrc(newPos)
        }

        binding.btnMark.setOnClickListener {
            handleMarking()
            binding.lyricsView.loadLrc(binding.etLyrics.text.toString())
            binding.lyricsView.updateTime(currentProgressMillis.toLong())
            // Devolvemos foco inmediato tras marcar
            binding.etLyrics.requestFocus()
        }

        // LÓGICA DE CURSOR FLUIDA ASIGNANDO ENFOQUE DIRECTO AL TEXTO
        binding.btnLeft.setOnClickListener {
            val pos = binding.etLyrics.selectionStart
            if (pos > 0) {
                binding.etLyrics.setSelection(pos - 1)
            }
            binding.etLyrics.requestFocus()
        }

        binding.btnRight.setOnClickListener {
            val pos = binding.etLyrics.selectionStart
            if (pos < binding.etLyrics.text.length) {
                binding.etLyrics.setSelection(pos + 1)
            }
            binding.etLyrics.requestFocus()
        }

        binding.btnUp.setOnClickListener { 
            moveCursorLine(-1)
            binding.etLyrics.requestFocus()
        }
        
        binding.btnDown.setOnClickListener { 
            moveCursorLine(1)
            binding.etLyrics.requestFocus()
        }
    }

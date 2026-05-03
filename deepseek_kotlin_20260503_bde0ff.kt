package com.example.pdfaudioplayer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AudioViewModel : ViewModel() {
    private val _isPlaying = MutableLiveData<Boolean>(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _audioTitle = MutableLiveData<String>("未选择音频")
    val audioTitle: LiveData<String> = _audioTitle

    fun setPlayingState(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setAudioTitle(title: String) {
        _audioTitle.value = title
    }

    fun togglePlayPause() {
        _isPlaying.value = _isPlaying.value?.not()
    }
}
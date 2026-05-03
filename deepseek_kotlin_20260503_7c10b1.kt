fun loadAndPlay(url: String) {
    mediaPlayer.reset()
    mediaPlayer.setDataSource(url)
    mediaPlayer.prepareAsync()
}
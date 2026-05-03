package com.example.pdfaudioplayer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.*
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.pdfaudioplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: AudioViewModel
    private var audioService: AudioPlaybackService? = null
    private var serviceBound = false
    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: Int = 0
    private var totalPages: Int = 0

    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlaybackService.LocalBinder
            audioService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            serviceBound = false
        }
    }

    // 注册文件选择结果处理
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadAudioFile(it) }
    }

    private val pdfPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { loadPdfDocument(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[AudioViewModel::class.java]

        // 绑定音频服务
        val serviceIntent = Intent(this, AudioPlaybackService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        initViews()
        observeViewModel()
    }

    private fun initViews() {
        // 打开PDF按钮
        binding.btnOpenPdf.setOnClickListener {
            pdfPickerLauncher.launch("application/pdf")
        }

        // 选择音频按钮
        binding.btnOpenAudio.setOnClickListener {
            audioPickerLauncher.launch("audio/*")
        }

        // 播放/暂停按钮
        binding.btnPlayPause.setOnClickListener {
            audioService?.let { service ->
                if (service.isPlaying()) {
                    service.pauseAudio()
                    viewModel.setPlayingState(false)
                } else {
                    service.playAudio()
                    viewModel.setPlayingState(true)
                }
            }
        }

        // 停止按钮
        binding.btnStop.setOnClickListener {
            audioService?.stopAudio()
            viewModel.setPlayingState(false)
        }

        // 上一页按钮
        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage(currentPage)
            }
        }

        // 下一页按钮
        binding.btnNextPage.setOnClickListener {
            pdfRenderer?.let { renderer ->
                if (currentPage < totalPages - 1) {
                    currentPage++
                    renderPage(currentPage)
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.isPlaying.observe(this) { playing ->
            binding.btnPlayPause.text = if (playing) "暂停" else "播放"
        }

        viewModel.audioTitle.observe(this) { title ->
            binding.tvAudioTitle.text = title
        }
    }

    private fun loadAudioFile(uri: Uri) {
        try {
            // 提取文件名作为标题
            val title = uri.lastPathSegment ?: "音频文件"
            viewModel.setAudioTitle(title)

            // 通过服务加载并播放音频
            audioService?.loadAndPlay(uri)
            viewModel.setPlayingState(true)
        } catch (e: Exception) {
            Toast.makeText(this, "无法加载音频文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPdfDocument(uri: Uri) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // 释放之前的渲染器
                    pdfRenderer?.close()

                    // 复制URI内容到临时文件以获取FileDescriptor
                    val pdfFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(pdfFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    pdfRenderer = PdfRenderer(fileDescriptor).also { renderer ->
                        totalPages = renderer.pageCount
                        currentPage = 0
                    }
                }

                // 更新UI
                updatePageIndicator()
                renderPage(currentPage)

                // 在主线程启用导航按钮
                withContext(Dispatchers.Main) {
                    binding.btnPrevPage.isEnabled = true
                    binding.btnNextPage.isEnabled = true
                    Toast.makeText(this@MainActivity, "PDF已加载，共${totalPages}页", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "无法加载PDF文件: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderPage(pageIndex: Int) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    pdfRenderer?.let { renderer ->
                        val page = renderer.openPage(pageIndex)
                        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap
                    }
                }

                bitmap?.let {
                    withContext(Dispatchers.Main) {
                        binding.pdfImageView.setImageBitmap(it)
                        updatePageIndicator()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "渲染页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updatePageIndicator() {
        binding.tvPageIndicator.text = "第 ${currentPage + 1} / ${totalPages} 页"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        pdfRenderer?.close()
    }
}
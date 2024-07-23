package com.abhishek.exodownload.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import com.abhishek.exodownload.DownloadTracker
import com.abhishek.exodownload.DownloadUtil
import com.abhishek.exodownload.DownloadUtil.showConfirmationDialog
import com.abhishek.exodownload.PlayerViewModel
import com.abhishek.previewexoplayer.R
import com.abhishek.previewexoplayer.databinding.ActivityMainBinding

@UnstableApi
class MainActivity : AppCompatActivity(), DownloadTracker.Listener {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private val playerViewModel: PlayerViewModel by viewModels()


    private val player by lazy {
        ExoPlayer.Builder(this).setSeekBackIncrementMs(15000)
            .setSeekForwardIncrementMs(15000).build()
    }

    private val url ="https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"

    private val mediaItem by lazy {
       MediaItem.Builder()
            .setUri(url)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setTag(com.abhishek.exodownload.MediaItem(-1,url))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        DownloadUtil.getDownloadTracker(this).addListener(this)

        initIntroVideo()
        binding.btnDownload.setOnClickListener { downloadVideo() }

        playerViewModel.downloadPercent.observe(this) {
            if (it != null) {
                onProgress(it)
            }
        }
        DownloadUtil.getDownloadTracker(this).downloads[mediaItem.localConfiguration?.uri!!]?.let {
            onDownloadsChanged(it)
        }
    }

    override fun onDownloadsChanged(download: Download) {
        when (download.state) {
            Download.STATE_DOWNLOADING -> {
                binding.btnDownload.hideLoading()
                playerViewModel.startFlow(this, download.request.uri)
                binding.btnDownload.setDndText(getString(R.string.downloading))
                onProgress(download.percentDownloaded)
            }

            Download.STATE_QUEUED -> {
                binding.btnDownload.hideLoading()
                binding.btnDownload.setDndText(getString(R.string.downloading))
                binding.btnDownload.setText(0)
                binding.btnDownload.showDownloadProgress()
            }

            Download.STATE_COMPLETED -> {
                if (download.request.uri.toString() == url) {
                    onCompleted()
                }
            }

            Download.STATE_STOPPED -> {
                onProgress(download.percentDownloaded)
            }

            else -> {
                binding.btnDownload.hideLoading()
                onDeleted()
            }
        }
    }

    private fun onProgress(value: Float = 0F) {
        value.let {
            binding.btnDownload.showDownloadProgress()
            binding.btnDownload.setText(it.toInt())
        }
    }


    private fun onDeleted() {
        binding.btnDownload.hideDownloadProgress()
        binding.btnDownload.downloadStateImage()
        binding.btnDownload.setDndText(getString(R.string.download_video))

    }

    private fun downloadVideo() {

        if (DownloadUtil.getDownloadTracker(this).isDownloaded(mediaItem)) {
            showDeleteConfirmationDialog()
        } else {
            val item = mediaItem.buildUpon()
                .setTag((mediaItem.localConfiguration?.tag as com.abhishek.exodownload.MediaItem).copy(duration = player.duration))
                .build()
            if (!DownloadUtil.getDownloadTracker(this).hasDownload(item.localConfiguration?.uri)) {
                DownloadUtil.getDownloadTracker(this).toggleDownloadDialogHelper(this, item)
            } else {
                DownloadUtil.getDownloadTracker(this)
                    .toggleDownloadPopupMenu(this, item.localConfiguration?.uri)
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        showConfirmationDialog(this,
            "Delete Confirmation",
            "Are you sure you want to Delete?",
            "Yes",
            positiveAction = {
                onDeleted()
                DownloadUtil.getDownloadTracker(this)
                    .removeDownload(mediaItem.localConfiguration?.uri)
            },
            "No",
            negativeAction = {}
        )
    }

    private fun onCompleted() {
        binding.btnDownload.hideDownloadProgress()
        binding.btnDownload.setDndText(getString(R.string.downloaded))
        binding.btnDownload.downloadedStateImage()
        initIntroVideo()
        Toast.makeText(this,"Video playing offline",Toast.LENGTH_SHORT).show()
    }

    private fun initIntroVideo() {
        val downloadRequest: DownloadRequest? =
            DownloadUtil.getDownloadTracker(this)
                .getDownloadRequest(mediaItem.localConfiguration?.uri)
        val mediaSource = if (downloadRequest == null) {
            // Online content
            DownloadUtil.createMediaSource(this, mediaItem)
        } else {
            // Offline content
            DownloadHelper.createMediaSource(
                downloadRequest,
                DownloadUtil.getReadOnlyDataSourceFactory(this)
            )
        }

        binding.exoplayer.player = player
        player.setMediaSource(mediaSource, false)
        player.prepare()
        player.play()
        player.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onResume() {
        super.onResume()
        player.play()
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.stopFlow()
    }

}
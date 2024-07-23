package com.abhishek.exodownload

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.content.ContextCompat.startActivity
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.abhishek.exodownload.DownloadUtil.showConfirmationDialog
import com.abhishek.exodownload.databinding.ExoDownloadBottomSheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

private const val TAG = "DownloadTracker"
private const val DEFAULT_BITRATE = 500_000

@UnstableApi
/** Tracks media that has been downloaded.  */
class DownloadTracker(
    context: Context,
    private val httpDataSourceFactory: HttpDataSource.Factory,
    private val downloadManager: DownloadManager
) {
    /**
     * Listens for changes in the tracked downloads.
     */
    interface Listener {
        /**
         * Called when the tracked downloads changed.
         */
        fun onDownloadsChanged(download: Download)

    }

    private val applicationContext: Context = context.applicationContext
    private val listeners: CopyOnWriteArraySet<Listener> = CopyOnWriteArraySet()
    private val downloadIndex: DownloadIndex = downloadManager.downloadIndex
    private var startDownloadDialogHelper: StartDownloadDialogHelper? = null
    private var availableBytesLeft: Long =
        StatFs(DownloadUtil.getDownloadDirectory(context).path).availableBytes

    val downloads: HashMap<Uri, Download> = HashMap()

    init {
        downloadManager.addListener(DownloadManagerListener())
        loadDownloads()
    }

    fun addListener(listener: Listener) {
        Assertions.checkNotNull(listener)
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun isDownloaded(mediaItem: MediaItem): Boolean {
        val download = downloads[mediaItem.localConfiguration?.uri]
        return download != null && download.state == Download.STATE_COMPLETED
    }

    fun hasDownload(uri: Uri?): Boolean = downloads.keys.contains(uri)

    fun getDownloadRequest(uri: Uri?): DownloadRequest? {
        uri ?: return null
        val download = downloads[uri]
        return if (download != null && download.state != Download.STATE_FAILED) download.request else null
    }

    fun toggleDownloadDialogHelper(
        context: Context, mediaItem: MediaItem,
        positiveCallback: (() -> Unit)? = null, dismissCallback: (() -> Unit)? = null
    ) {
        startDownloadDialogHelper?.release()
        startDownloadDialogHelper =
            StartDownloadDialogHelper(
                context,
                getDownloadHelper(mediaItem),
                mediaItem,
                positiveCallback,
                dismissCallback
            )
    }

    fun toggleDownloadPopupMenu(context: Context, uri: Uri?) {
        val actionDialog = BottomSheetDialog(context)
        val binding = ExoDownloadBottomSheetBinding.inflate(LayoutInflater.from(context))

        val download = downloads[uri]
        download ?: return
        binding.tvHeading.text = "Choose Action"
        binding.divider.show()
        binding.rvItems.show()
        binding.circularLoader.hide()


        val arrayAdapter = arrayListOf<String>()
        when (download.state) {
            Download.STATE_STOPPED, Download.STATE_FAILED -> {
                arrayAdapter.add("Resume Download")
                arrayAdapter.add("Cancel Download")
                // arrayAdapter.add("View Downloads")
            }

            Download.STATE_DOWNLOADING -> {
                arrayAdapter.add("Pause Download")
                arrayAdapter.add("Cancel Download")
                // arrayAdapter.add("View Downloads")
            }

            Download.STATE_QUEUED -> {
                arrayAdapter.add("Cancel Download")
                //  arrayAdapter.add("View Downloads")
            }

            Download.STATE_REMOVING -> {
                binding.tvHeading.text = "Deleting video please wait"
            }
        }

        actionDialog.setContentView(binding.root)


        binding.rvItems.adapter =
            DownloadOptionAdapter(arrayAdapter.toTypedArray(), false) { selectedFormatIndex ->
                when (arrayAdapter[selectedFormatIndex]) {
                    "Cancel Download" -> {
                        removeDownload(download.request.uri)
                    }

                    "Resume Download" -> {
                        DownloadService.sendSetStopReason(
                            context,
                            MyDownloadService::class.java,
                            download.request.id,
                            Download.STOP_REASON_NONE,
                            true
                        )
                    }

                    "Pause Download" -> {
                        DownloadService.sendSetStopReason(
                            context,
                            MyDownloadService::class.java,
                            download.request.id,
                            Download.STATE_STOPPED,
                            false
                        )
                    }
                }
                actionDialog.dismiss()
            }

        try {
            if (download.state == Download.STATE_COMPLETED) {
                showConfirmationDialog(context,
                    "Delete Confirmation",
                    "Do you want to delete this video?",
                    "Yes",
                    positiveAction = {
                        removeDownload(download.request.uri)
                    },
                    "No",
                    negativeAction = {}
                )
            } else
                actionDialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeDownload(uri: Uri?) {
        val download = downloads[uri]
        download?.let {
            DownloadService.sendRemoveDownload(
                applicationContext,
                MyDownloadService::class.java,
                download.request.id,
                false
            )
        }
    }

    private fun loadDownloads() {
        try {
            downloadIndex.getDownloads().use { loadedDownloads ->
                while (loadedDownloads.moveToNext()) {
                    val download = loadedDownloads.download
                    downloads[download.request.uri] = download
                }
            }
        } catch (e: IOException) {
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getAllDownloadProgressFlow(): Flow<List<Download>> = callbackFlow {
        while (coroutineContext.isActive) {
            trySend(downloads.values.toList()).isSuccess
            delay(1000)
        }
    }

    @ExperimentalCoroutinesApi
    suspend fun getCurrentProgressDownload(uri: Uri?): Flow<Float?> {
        var percent: Float? =
            downloadManager.currentDownloads.find { it.request.uri == uri }?.percentDownloaded
        return callbackFlow {
            while (percent != null) {
                percent =
                    downloadManager.currentDownloads.find { it.request.uri == uri }?.percentDownloaded
                trySend(percent).isSuccess
                withContext(Dispatchers.IO) {
                    delay(1000)
                }
            }
            awaitClose { channel.close() }
        }
    }


    @ExperimentalCoroutinesApi
    suspend fun getMultipleCurrentProgressDownload(): Flow<List<Download>> {
        var downloads = downloadManager.currentDownloads
        return callbackFlow {
            while (downloads.isNotEmpty()) {
                downloads = downloadManager.currentDownloads
                trySend(downloads.toList()).isSuccess
                withContext(Dispatchers.IO) {
                    delay(1000)
                }
            }
            awaitClose { channel.close() }
        }
    }


    private fun getDownloadHelper(mediaItem: MediaItem): DownloadHelper {
        return when (mediaItem.localConfiguration?.mimeType) {
            MimeTypes.APPLICATION_MPD, MimeTypes.APPLICATION_M3U8, MimeTypes.APPLICATION_SS -> {
                DownloadHelper.forMediaItem(
                    applicationContext,
                    mediaItem,
                    DefaultRenderersFactory(applicationContext),
                    httpDataSourceFactory
                )
            }

            else -> DownloadHelper.forMediaItem(applicationContext, mediaItem)
        }
    }

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            downloads[download.request.uri] = download
            for (listener in listeners) {
                listener.onDownloadsChanged(download)
            }
            try {
                if (download.state == Download.STATE_COMPLETED) {
                    // Add delta between estimation and reality to have a better availableBytesLeft
                    availableBytesLeft +=
                        Util.fromUtf8Bytes(download.request.data)
                            .toLong() - download.bytesDownloaded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            downloads.remove(download.request.uri)
            for (listener in listeners) {
                listener.onDownloadsChanged(download)
            }

            try {
                // Add the estimated or downloaded bytes to the availableBytes
                availableBytesLeft += if (download.percentDownloaded == 100f) {
                    download.bytesDownloaded
                } else {
                    Util.fromUtf8Bytes(download.request.data).toLong()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Can't use applicationContext because it'll result in a crash, instead
    // Use context of the activity calling for the AlertDialog
    inner class StartDownloadDialogHelper(
        private val context: Context,
        private val downloadHelper: DownloadHelper,
        private val mediaItem: MediaItem,
        private val positiveCallback: (() -> Unit)? = null,
        private val dismissCallback: (() -> Unit)? = null
    ) : DownloadHelper.Callback {

        val dialogBuilder = BottomSheetDialog(context)
        val binding = ExoDownloadBottomSheetBinding.inflate(LayoutInflater.from(context))

        init {
            downloadHelper.prepare(this)
            dialogBuilder.setContentView(binding.root)
            dialogBuilder.createRounderBottomSheet()
            binding.tvHeading.text = "Select Quality"
            try {
                dialogBuilder.show()
                binding.circularLoader.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun release() {
            try {
                downloadHelper.release()
                dialogBuilder.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // DownloadHelper.Callback implementation.
        override fun onPrepared(helper: DownloadHelper) {
            if (helper.periodCount == 0) {
                val mediaItem: com.abhishek.exodownload.MediaItem =
                    mediaItem.localConfiguration?.tag as com.abhishek.exodownload.MediaItem
                val estimatedContentLength: Long = (DEFAULT_BITRATE * mediaItem.duration)
                    .div(C.MILLIS_PER_SECOND).div(C.BITS_PER_BYTE)
                val downloadRequest: DownloadRequest = downloadHelper.getDownloadRequest(
                    convertGson(mediaItem).toString(),
                    Util.getUtf8Bytes(estimatedContentLength.toString())
                )
                startDownload(downloadRequest)
                downloadHelper.release()
                return
            }

            val formatDownloadable: MutableList<Format> = mutableListOf()
            val mappedTrackInfo = downloadHelper.getMappedTrackInfo(0)

            for (i in 0 until mappedTrackInfo.rendererCount) {
                if (C.TRACK_TYPE_VIDEO == mappedTrackInfo.getRendererType(i)) {
                    val trackGroups: TrackGroupArray = mappedTrackInfo.getTrackGroups(i)
                    for (j in 0 until trackGroups.length) {
                        val trackGroup: TrackGroup = trackGroups[j]
                        for (k in 0 until trackGroup.length) {
                            formatDownloadable.add(trackGroup.getFormat(k))
                        }
                    }
                }
            }

            if (formatDownloadable.isEmpty()) {
                return
            }

            // We sort here because later we use formatDownloadable to select track
            formatDownloadable.sortBy { it.height }
            val mediaItem: com.abhishek.exodownload.MediaItem =
                mediaItem.localConfiguration?.tag as com.abhishek.exodownload.MediaItem
            if (mediaItem.duration < 0) {
                return
            }
            val optionsDownload: List<String> = formatDownloadable.map {
                context.getString(
                    R.string.dialog_option, it.height,
                    (it.bitrate * mediaItem.duration).div(8000).formatFileSize()
                )
            }

            binding.rvItems.adapter =
                DownloadOptionAdapter(optionsDownload.toTypedArray()) { selectedFormatIndex ->
                    try {
                        if (selectedFormatIndex in 0 until formatDownloadable.size) {
                            val format = formatDownloadable[selectedFormatIndex]
                            val qualitySelected =
                                DefaultTrackSelector(context).buildUponParameters()
                                    .setMinVideoSize(format.width, format.height)
                                    .setMinVideoBitrate(format.bitrate)
                                    .setMaxVideoSize(format.width, format.height)
                                    .setMaxVideoBitrate(format.bitrate)
                                    .build()

                            helper.clearTrackSelections(0)
                            helper.addTrackSelection(0, qualitySelected)

                            val mediaItem =
                                this.mediaItem.localConfiguration?.tag as? com.abhishek.exodownload.MediaItem
                            if (mediaItem != null && mediaItem.duration >= 0) {
                                val estimatedContentLength: Long =
                                    (qualitySelected.maxVideoBitrate * mediaItem.duration)
                                        .div(C.MILLIS_PER_SECOND).div(C.BITS_PER_BYTE)

                                if (availableBytesLeft > estimatedContentLength) {
                                    val downloadRequest: DownloadRequest =
                                        downloadHelper.getDownloadRequest(
                                            convertGson(mediaItem).toString(),
                                            Util.getUtf8Bytes(estimatedContentLength.toString())
                                        )
                                    startDownload(downloadRequest)
                                    availableBytesLeft -= estimatedContentLength
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Not enough space to download this file",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Invalid media",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            dialogBuilder.dismiss()
                            positiveCallback?.invoke()
                        } else {
                            Toast.makeText(
                                context,
                                "Download Failed, Retry!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Download Failed, Retry!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            dialogBuilder.setOnDismissListener {
                downloadHelper.release()
                dismissCallback?.invoke()
            }
            binding.divider.show()
            binding.rvItems.show()
            binding.circularLoader.hide()
        }


        override fun onPrepareError(helper: DownloadHelper, e: IOException) {
            Toast.makeText(applicationContext, R.string.download_start_error, Toast.LENGTH_LONG)
                .show()
        }

        // Internal methods.
        private fun startDownload(downloadRequest: DownloadRequest = buildDownloadRequest()) {
            DownloadService.sendAddDownload(
                applicationContext,
                MyDownloadService::class.java,
                downloadRequest,
                true
            )
        }

        private fun buildDownloadRequest(): DownloadRequest {
            return downloadHelper.getDownloadRequest(
                convertGson((mediaItem.localConfiguration?.tag as com.abhishek.exodownload.MediaItem)).toString(),
                Util.getUtf8Bytes(mediaItem.localConfiguration?.uri.toString())
            )
        }
    }

    private fun convertGson(value: com.abhishek.exodownload.MediaItem): String? {
        val gson = Gson()
        return gson.toJson(value)
    }
}
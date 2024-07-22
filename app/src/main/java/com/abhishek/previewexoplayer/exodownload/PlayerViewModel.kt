package com.abhishek.previewexoplayer.exodownload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.*

@androidx.media3.common.util.UnstableApi
class PlayerViewModel(): ViewModel() {
    private val _downloads: MutableLiveData<List<Download>> = MutableLiveData()
    val downloads: LiveData<List<Download>>
        get() = _downloads

    private val _downloadPercent = MutableLiveData<Float>()
    private val _multipleDownloads : MutableLiveData<List<Download>> = MutableLiveData()
    val updateProgress : MutableLiveData<Triple<Long,Long,Long>> = MutableLiveData()
    val updateVideoAdProgress : MutableLiveData<Triple<Long,Long,Long>> = MutableLiveData()
    val downloadPercent: LiveData<Float>
        get() = _downloadPercent

    val multipleDownloads: LiveData<List<Download>>
        get() = _multipleDownloads


    private var job: CompletableJob = SupervisorJob()
    private var downloadScope: CoroutineScope = CoroutineScope(Dispatchers.IO + job)
    private var coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + job)


    fun startFlow(context: Context, uri: Uri) {
        coroutineScope.launch {
            DownloadUtil.getDownloadTracker(context).getCurrentProgressDownload(uri).collect {
                _downloadPercent.postValue(it)
            }
        }
    }

    fun getMultipleDownloads(context: Context) {
        coroutineScope.launch {
            DownloadUtil.getDownloadTracker(context).getMultipleCurrentProgressDownload().collect {
                _multipleDownloads.postValue(it)
            }
        }
    }

    fun stopFlow() {
        coroutineScope.cancel()
    }

    fun startFlow(context: Context) {
        downloadScope.launch {
            DownloadUtil.getDownloadTracker(context).getAllDownloadProgressFlow().collect {
                _downloads.postValue(it)
            }
        }
    }

    fun stopDownloadFlow() {
        downloadScope.cancel()
    }

}
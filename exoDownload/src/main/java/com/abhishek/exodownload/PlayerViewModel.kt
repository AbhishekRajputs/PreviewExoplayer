package com.abhishek.exodownload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.*

@androidx.media3.common.util.UnstableApi
class PlayerViewModel(): ViewModel() {

    private val _downloadPercent = MutableLiveData<Float>()

    val downloadPercent: LiveData<Float>
        get() = _downloadPercent


    private var job: CompletableJob = SupervisorJob()
    private var coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + job)


    fun startFlow(context: Context, uri: Uri) {
        coroutineScope.launch {
            DownloadUtil.getDownloadTracker(context).getCurrentProgressDownload(uri).collect {
                _downloadPercent.postValue(it)
            }
        }
    }


// in case you are downloading multiple videos here is the code and u can observe data of multiple videos
//    fun getMultipleDownloads(context: Context) {
//        coroutineScope.launch {
//            DownloadUtil.getDownloadTracker(context).getMultipleCurrentProgressDownload().collect {
//                _multipleDownloads.postValue(it)
//            }
//        }
//    }

    fun stopFlow() {
        coroutineScope.cancel()
    }

}
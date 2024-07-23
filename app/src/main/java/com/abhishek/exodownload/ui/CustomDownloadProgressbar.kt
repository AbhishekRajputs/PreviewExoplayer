package com.abhishek.exodownload.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import com.abhishek.exodownload.hide
import com.abhishek.exodownload.show
import com.abhishek.previewexoplayer.R
import com.abhishek.previewexoplayer.databinding.CustomDownloadProgressBinding


class CustomDownloadProgressbar(context: Context?, attrs: AttributeSet?) : ConstraintLayout(context!!, attrs) {
    private var mText=""
    private var binding =
        CustomDownloadProgressBinding.inflate(LayoutInflater.from(context), this, true)

    init {
        initView()
    }

    private fun initView() {
        if(mText.isNotEmpty()){
            binding.tv.text=mText
            binding.circularProgressbar.progress=0
        }
    }

    fun changeProgressColor(){
        binding.circularProgressbar.trackColor=resources.getColor(R.color.white)
    }

    fun setText(text:Int){
        binding.tv.text="${text}%"
        binding.circularProgressbar.progress=text
    }

    fun setDndText(text:String){
        binding.dnldTV.text=text
    }

    fun hideDownloadText(){
      binding.dnldTV.hide()
    }

    fun downloadStateImage(){
        binding.imageView.setBackgroundResource(R.drawable.ic_downloadnew)
    }

    fun showLoading(){
        binding.circularLoader.show()
        binding.dnldTV.text="Downloading"
        binding.imageView.hide()
    }

    fun hideLoading(){
        binding.circularLoader.hide()
        binding.imageView.show()
    }

    fun isShowingLoading(): Boolean {
        return binding.circularLoader.isVisible
    }


    fun showDownloadProgress(){
        binding.circularProgressbar.show()
        binding.tv.show()
        binding.imageView.hide()
    }

    fun hideDownloadProgress(){
        binding.circularProgressbar.hide()
        binding.tv.hide()
        binding.imageView.show()
    }

    fun downloadedStateImage(){
        binding.imageView.setBackgroundResource(R.drawable.ic_downloaded_new)

    }


}
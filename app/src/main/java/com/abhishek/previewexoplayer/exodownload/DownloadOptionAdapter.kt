package com.vidyakul.exodownload

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.abhishek.previewexoplayer.databinding.ItemDownloadOptionsBinding


class DownloadOptionAdapter(
    private val string: Array<String>,
    private val isBoldFalse: Boolean = true,
    private val listener: (Int) -> Unit
) :
    RecyclerView.Adapter<DownloadOptionAdapter.ViewHolder>() {

    private var newPosition = -1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemDownloadOptionsBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )
    }

    override fun getItemCount() = string.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindItems(string[position])
    }

    inner class ViewHolder(private val binding: ItemDownloadOptionsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bindItems(s: String) {
            if (bindingAdapterPosition == string.size - 1) {
                binding.view.visibility=View.GONE
            }
            val ss = SpannableString(s)

            if(isBoldFalse) {
                val boldSpan = StyleSpan(Typeface.BOLD)
                ss.setSpan(boldSpan, 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }else{
                binding.imgDownload.visibility=View.GONE
            }

            binding.tvItemOption.text = ss

            binding.tvItemOption.setOnClickListener {
                newPosition = bindingAdapterPosition
                listener(bindingAdapterPosition)
                notifyDataSetChanged()
            }
        }
    }

}
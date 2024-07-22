package com.abhishek.previewexoplayer.exodownload

import java.io.Serializable


data class MediaItemTag(
    val duration: Long,
    val title: String,
    var poster_image: String,
    var cipher_id: String,
    var teacher_name: String,
    var course_name: String,
    var subject: String,
    var chapter_name:String="",
    var duration_string:String="",
    var poster_image_old:String="",
    var poster_image_vertical:String=""
): Serializable


package com.feather.upscale

import com.feather.upscale.video.VideoProcessor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoProcessorTest {

    @Test
    fun `isVideoFile nhan dien chinh xac cac dinh dang video pho bien`() {
        assertTrue(VideoProcessor.isVideoFile("sample.mp4"))
        assertTrue(VideoProcessor.isVideoFile("movie.mkv"))
        assertTrue(VideoProcessor.isVideoFile("clip.webm"))
        assertTrue(VideoProcessor.isVideoFile("anime.avi"))
        assertTrue(VideoProcessor.isVideoFile("video.mov"))
        assertTrue(VideoProcessor.isVideoFile("record.3gp"))

        assertTrue(VideoProcessor.isVideoFile("SAMPLE.MP4"))
        assertTrue(VideoProcessor.isVideoFile("MY_MOVIE.MKV"))
    }

    @Test
    fun `isVideoFile tu choi dung cac dinh dang anh va truyen tranh`() {
        assertFalse(VideoProcessor.isVideoFile("comic.cbz"))
        assertFalse(VideoProcessor.isVideoFile("archive.zip"))
        assertFalse(VideoProcessor.isVideoFile("book.mobi"))
        assertFalse(VideoProcessor.isVideoFile("photo.png"))
        assertFalse(VideoProcessor.isVideoFile("image.jpg"))
        assertFalse(VideoProcessor.isVideoFile("cover.webp"))
    }
}


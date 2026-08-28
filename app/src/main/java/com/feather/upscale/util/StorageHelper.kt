package com.feather.upscale.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object StorageHelper {

    /**
     * Chuyển đổi Tree URI từ OpenDocumentTree sang đường dẫn hiển thị thân thiện (VD: /Pictures/Manga).
     */
    fun getDisplayPathFromTreeUri(uriString: String?): String? {
        if (uriString.isNullOrEmpty()) return null
        return try {
            val decoded = Uri.decode(uriString)
            if (decoded.contains("primary:")) {
                val subPath = decoded.substringAfter("primary:")
                if (subPath.isEmpty()) "Bộ nhớ trong" else "/$subPath"
            } else if (decoded.contains("document/")) {
                val subPath = decoded.substringAfter("document/")
                "/$subPath"
            } else {
                decoded
            }
        } catch (_: Throwable) {
            uriString
        }
    }

    /**
     * Mở OutputStream để ghi file đầu ra một cách an toàn và đúng quyền:
     * - Giữ nguyên định dạng đuôi file chuẩn (.cbz cho tập truyện, .png cho ảnh, .mp4 cho video).
     * - Nếu người dùng chọn Custom Tree URI (content://...): dùng DocumentFile để tạo file và mở stream.
     * - Nếu mặc định: ghi vào thư mục File công khai (Pictures/UpScale, Movies/UpScale hoặc Download/UpScale).
     */
    fun createOutputFileStream(
        context: Context,
        customOutputDirUriStr: String?,
        fileName: String,
        mimeType: String,
        isComicOrMobi: Boolean
    ): OutputTarget {
        val isVideo = fileName.endsWith(".mp4", true) || fileName.endsWith(".mkv", true) || mimeType.startsWith("video/")

        // Tối ưu MIME type để Android Scoped Storage không tự ý đổi đuôi file
        val safeMimeType = when {
            fileName.endsWith(".cbz", true) -> "application/x-cbz"
            fileName.endsWith(".png", true) -> "image/png"
            fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) -> "image/jpeg"
            fileName.endsWith(".mp4", true) -> "video/mp4"
            fileName.endsWith(".mkv", true) -> "video/x-matroska"
            else -> mimeType
        }

        if (!customOutputDirUriStr.isNullOrEmpty() && customOutputDirUriStr.startsWith("content://")) {
            try {
                val treeUri = Uri.parse(customOutputDirUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDoc != null && rootDoc.canWrite()) {
                    // Tự động tạo thư mục con UpScale nếu chưa có
                    val targetDirDoc = if (rootDoc.name.equals("UpScale", true)) {
                        rootDoc
                    } else {
                        rootDoc.findFile("UpScale") ?: rootDoc.createDirectory("UpScale") ?: rootDoc
                    }

                    // Xóa file cũ cùng tên nếu đã có
                    targetDirDoc.findFile(fileName)?.delete()
                    targetDirDoc.findFile("$fileName.zip")?.delete()

                    val fileDoc = targetDirDoc.createFile(safeMimeType, fileName)
                        ?: targetDirDoc.createFile("application/octet-stream", fileName)

                    if (fileDoc != null) {
                        val outputStream = context.contentResolver.openOutputStream(fileDoc.uri)
                        if (outputStream != null) {
                            val displayDir = getDisplayPathFromTreeUri(customOutputDirUriStr) ?: "Thư mục tùy chỉnh"
                            return OutputTarget(
                                outputStream = outputStream,
                                targetUri = fileDoc.uri,
                                absolutePath = fileDoc.uri.toString(),
                                displayDirectory = "$displayDir/UpScale",
                                isDocumentUri = true
                            )
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // Fallback thư mục công khai chuẩn
        val publicParent = when {
            isComicOrMobi -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            isVideo -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            else -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        }

        var upScaleDir = File(publicParent, "UpScale")
        if (!upScaleDir.exists()) {
            upScaleDir.mkdirs()
        }

        if (!upScaleDir.exists() || !upScaleDir.canWrite()) {
            val appBase = when {
                isComicOrMobi -> context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "downloads")
                isVideo -> context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "movies")
                else -> context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(context.filesDir, "pictures")
            }
            upScaleDir = File(appBase, "UpScale").apply { mkdirs() }
        }

        val file = File(upScaleDir, fileName)
        val stream = FileOutputStream(file)

        return OutputTarget(
            outputStream = stream,
            targetUri = null,
            absolutePath = file.absolutePath,
            displayDirectory = upScaleDir.absolutePath,
            isDocumentUri = false,
            localFile = file
        )
    }

    /**
     * Quét tệp vào thư viện thiết bị
     */
    fun scanMediaFile(context: Context, target: OutputTarget, mimeType: String) {
        if (!target.isDocumentUri && target.localFile != null) {
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.localFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } catch (_: Throwable) {}
        }
    }

    data class OutputTarget(
        val outputStream: OutputStream,
        val targetUri: Uri?,
        val absolutePath: String,
        val displayDirectory: String,
        val isDocumentUri: Boolean,
        val localFile: File? = null
    )
}

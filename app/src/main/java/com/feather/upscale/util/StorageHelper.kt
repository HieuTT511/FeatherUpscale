package com.feather.upscale.util

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
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

    /**
     * Mở thư mục chứa kết quả lưu đầu ra bằng giải pháp đa tầng (Multi-tier Intent)
     * tương thích 100% tất cả các dòng máy Android (Samsung, Xiaomi, Pixel, Oppo, Vivo, v.v.)
     */
    fun openOutputFolder(context: Context, dirPath: String?, filePath: String?) {
        // 1. Sao chép đường dẫn thân thiện vào Clipboard
        val displayPath = when {
            dirPath != null && dirPath.startsWith("content://") -> getDisplayPathFromTreeUri(dirPath) ?: dirPath
            dirPath != null -> dirPath
            filePath != null && !filePath.startsWith("content://") -> File(filePath).parent ?: filePath
            else -> "Thư mục UpScale"
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Đường dẫn UpScale", displayPath)
            clipboard?.setPrimaryClip(clip)
        } catch (_: Throwable) {}

        // --- CHIẾN LƯỢC 1: SAF Document Tree (Nếu người dùng chọn Tree URI tùy chỉnh content://) ---
        if (dirPath != null && dirPath.startsWith("content://")) {
            try {
                val treeUri = Uri.parse(dirPath)
                val docId = try {
                    DocumentsContract.getTreeDocumentId(treeUri)
                } catch (_: Throwable) {
                    DocumentsContract.getRootDocumentId(treeUri)
                }
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Toast.makeText(context, "📁 Đã mở: $displayPath", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Throwable) {
                try {
                    val treeUri = Uri.parse(dirPath)
                    val intent = Intent(Intent.ACTION_VIEW, treeUri).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu"))
                    return
                } catch (_: Throwable) {}
            }
        }

        // --- CHIẾN LƯỢC 2: DocumentsUI qua DocumentsContract (Android 7.0 - 15) ---
        val relativeSubPath = when {
            dirPath != null && dirPath.contains("Download", true) -> "Download/UpScale"
            dirPath != null && dirPath.contains("Movies", true) -> "Movies/UpScale"
            dirPath != null && dirPath.contains("Pictures", true) -> "Pictures/UpScale"
            filePath != null && (filePath.contains(".cbz", true) || filePath.contains(".zip", true)) -> "Download/UpScale"
            filePath != null && (filePath.contains(".mp4", true) || filePath.contains(".mkv", true)) -> "Movies/UpScale"
            else -> "Pictures/UpScale"
        }

        try {
            val docId = "primary:$relativeSubPath"
            val docUri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "📁 Đã mở: $displayPath", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Throwable) {}

        // --- CHIẾN LƯỢC 3: Mở DownloadManager nếu là Download / Comic ---
        if (relativeSubPath.startsWith("Download")) {
            try {
                val downloadIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(downloadIntent)
                Toast.makeText(context, "📁 Đã mở mục Tải về (Download/UpScale)", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Throwable) {}
        }

        // --- CHIẾN LƯỢC 4: Thử mở Storage Root DocumentsUI ---
        try {
            val rootUri = DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")
            val rootIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(rootUri, DocumentsContract.Root.MIME_TYPE_ITEM)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(rootIntent, "Mở trình quản lý tệp"))
            Toast.makeText(context, "📁 Đã mở bộ nhớ: $displayPath", Toast.LENGTH_SHORT).show()
            return
        } catch (_: Throwable) {}

        // --- CHIẾN LƯỢC 5: Mở File Manager của nhà sản xuất (Samsung, Xiaomi, Google Files...) ---
        val targetFile = if (dirPath != null && !dirPath.startsWith("content://")) File(dirPath)
                         else if (filePath != null && !filePath.startsWith("content://")) File(filePath).parentFile
                         else null

        if (targetFile != null && targetFile.exists()) {
            // Samsung My Files
            try {
                val samsungIntent = Intent("com.sec.android.app.myfiles.VIEW_FOLDER").apply {
                    setPackage("com.sec.android.app.myfiles")
                    putExtra("folderPath", targetFile.absolutePath)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(samsungIntent)
                Toast.makeText(context, "📁 Đã mở: ${targetFile.name}", Toast.LENGTH_SHORT).show()
                return
            } catch (_: Throwable) {}

            // Google Files app (Files by Google)
            try {
                val filesAppIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.files")
                if (filesAppIntent != null) {
                    filesAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(filesAppIntent)
                    Toast.makeText(context, "📁 Đã mở Files. Đường dẫn: $displayPath (Đã sao chép)", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Throwable) {}

            // DocumentsUI system app
            try {
                val docsUiIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.documentsui")
                    ?: context.packageManager.getLaunchIntentForPackage("com.android.documentsui")
                if (docsUiIntent != null) {
                    docsUiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(docsUiIntent)
                    Toast.makeText(context, "📁 Đã mở Files. Đường dẫn: $displayPath (Đã sao chép)", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: Throwable) {}
        }

        // --- CHIẾN LƯỢC 6: Mở Media Store / Thư viện Gallery ---
        try {
            val isVid = filePath?.endsWith(".mp4", true) == true || filePath?.endsWith(".mkv", true) == true
            val mediaUri = if (isVid) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                           else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val galleryIntent = Intent(Intent.ACTION_VIEW, mediaUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(galleryIntent, "Mở thư viện ảnh/video"))
            Toast.makeText(context, "📁 Đường dẫn lưu: $displayPath (Đã sao chép vào bộ nhớ tạm)", Toast.LENGTH_LONG).show()
            return
        } catch (_: Throwable) {}

        // --- CHIẾN LƯỢC 7: Mở trực tiếp tệp & Hiển thị thông báo đường dẫn ---
        Toast.makeText(context, "📁 Đã lưu tại: $displayPath (Đã sao chép đường dẫn)", Toast.LENGTH_LONG).show()
        if (filePath != null) {
            openFile(context, filePath)
        }
    }

    /**
     * Mở tệp đầu ra an toàn qua FileProvider hoặc Document URI
     */
    fun openFile(context: Context, filePath: String?) {
        if (filePath.isNullOrEmpty()) return
        if (filePath.startsWith("content://")) {
            try {
                val uri = Uri.parse(filePath)
                val mimeType = when {
                    filePath.contains(".mp4", true) || filePath.contains(".mkv", true) -> "video/*"
                    filePath.contains(".cbz", true) || filePath.contains(".zip", true) -> "application/zip"
                    else -> "image/*"
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Throwable) {
                try {
                    val uri = Uri.parse(filePath)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(intent, "Mở tệp bằng"))
                    return
                } catch (_: Throwable) {}
            }
        }

        val file = File(filePath)
        if (!file.exists()) {
            Toast.makeText(context, "Không tìm thấy tệp: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", file)
            val mimeType = when {
                file.extension.equals("mp4", true) || file.extension.equals("mkv", true) -> "video/*"
                file.extension.equals("cbz", true) || file.extension.equals("zip", true) -> "application/zip"
                else -> "image/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở tệp bằng"))
            } catch (_: Throwable) {
                Toast.makeText(context, "Không có ứng dụng nào mở được tệp này", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Chia sẻ tệp đầu ra qua FileProvider
     */
    fun shareFile(context: Context, filePath: String?) {
        if (filePath.isNullOrEmpty()) return
        if (filePath.startsWith("content://")) {
            try {
                val uri = Uri.parse(filePath)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = when {
                        filePath.contains(".mp4", true) || filePath.contains(".mkv", true) -> "video/*"
                        filePath.contains(".cbz", true) || filePath.contains(".zip", true) -> "application/zip"
                        else -> "image/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ kết quả"))
                return
            } catch (_: Throwable) {}
        }

        val file = File(filePath)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = when {
                    file.extension.equals("mp4", true) || file.extension.equals("mkv", true) -> "video/*"
                    file.extension.equals("cbz", true) || file.extension.equals("zip", true) -> "application/zip"
                    else -> "image/*"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ kết quả"))
        } catch (_: Throwable) {
            Toast.makeText(context, "Không thể chia sẻ tệp", Toast.LENGTH_SHORT).show()
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

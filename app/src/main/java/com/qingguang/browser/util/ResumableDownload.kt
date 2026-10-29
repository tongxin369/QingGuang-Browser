package com.qingguang.browser.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** 自建断点续传任务（暂停后分片保留，继续时用 Range 续传） */
data class ResumableTask(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val status: String,
    val bytes: Long,
    val totalBytes: Long,
    val message: String = ""
) {
    val progress: Float
        get() = if (totalBytes > 0) (bytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val isFinished: Boolean get() = status == "已完成"
    val isActive: Boolean get() = status == "下载中" || status == "等待中"
    val canResume: Boolean get() = status == "已暂停" || status == "失败"
}

object ResumableDownloads {

    const val STATUS_QUEUED = "等待中"
    const val STATUS_RUNNING = "下载中"
    const val STATUS_PAUSED = "已暂停"
    const val STATUS_FAILED = "失败"
    const val STATUS_DONE = "已完成"

    private const val PREFS = "resume_downloads"
    private const val KEY_TASKS = "tasks"

    fun tasks(context: Context): List<ResumableTask> {
        val raw = prefs(context).getString(KEY_TASKS, "").orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                ResumableTask(
                    id = obj.optString("id"),
                    url = obj.optString("url"),
                    fileName = obj.optString("fileName"),
                    mimeType = obj.optString("mimeType"),
                    status = obj.optString("status", STATUS_QUEUED),
                    bytes = obj.optLong("bytes"),
                    totalBytes = obj.optLong("totalBytes", -1),
                    message = obj.optString("message")
                )
            }.filter { it.id.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun find(context: Context, id: String): ResumableTask? = tasks(context).firstOrNull { it.id == id }

    internal fun update(context: Context, task: ResumableTask) {
        val list = tasks(context).filterNot { it.id == task.id } + task
        writeAll(context, list)
    }

    /** 新建任务并开始下载 */
    fun start(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String,
        userAgent: String
    ): ResumableTask? {
        val target = url.trim()
        if (target.isBlank()) return null
        val safeName = fileName.trim().ifBlank { target.substringAfterLast('/').substringBefore('?') }
            .ifBlank { "下载-${System.currentTimeMillis()}.bin" }
        val task = ResumableTask(
            id = UUID.randomUUID().toString(),
            url = target,
            fileName = safeName,
            mimeType = mimeType,
            status = STATUS_QUEUED,
            bytes = 0,
            totalBytes = -1
        )
        update(context, task)
        enqueue(context, task.id, userAgent)
        return task
    }

    /** 暂停：取消后台任务，分片文件保留在应用内 */
    fun pause(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        val task = find(context, id) ?: return
        if (!task.isActive) return
        update(context, task.copy(status = STATUS_PAUSED, message = "已暂停，分片已保留"))
    }

    /** 继续：用 Range 从已有分片末尾续传 */
    fun resume(context: Context, id: String, userAgent: String) {
        val task = find(context, id) ?: return
        if (task.isFinished) return
        update(context, task.copy(status = STATUS_QUEUED, message = ""))
        enqueue(context, id, userAgent)
    }

    fun remove(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        runCatching { partFile(context, id).delete() }
        writeAll(context, tasks(context).filterNot { it.id == id })
    }

    fun clearFinished(context: Context) {
        tasks(context).filter { it.isFinished }.forEach { remove(context, it.id) }
    }

    internal fun workName(id: String): String = "qg-resume-download-$id"

    internal fun partFile(context: Context, id: String): File {
        val dir = File(context.filesDir, "download_parts").apply { mkdirs() }
        return File(dir, "$id.part")
    }

    internal fun enqueue(context: Context, id: String, userAgent: String) {
        val request = OneTimeWorkRequestBuilder<ResumableDownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString("id", id)
                    .putString("userAgent", userAgent)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun writeAll(context: Context, list: List<ResumableTask>) {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("fileName", item.fileName)
                    put("mimeType", item.mimeType)
                    put("status", item.status)
                    put("bytes", item.bytes)
                    put("totalBytes", item.totalBytes)
                    put("message", item.message)
                }
            )
        }
        prefs(context).edit().putString(KEY_TASKS, array.toString()).apply()
    }

    internal fun saveToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        part: File
    ): Uri? = runCatching {
        val settings = (context.applicationContext as? com.qingguang.browser.App)?.settings
        val subDir = settings?.downloadSubDir.orEmpty()
        val relativePath = if (subDir.isBlank()) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$subDir"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { output ->
            part.inputStream().use { input -> input.copyTo(output) }
        } ?: return null
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        uri
    }.getOrNull()
}

/** 后台执行断点续传；暂停（cancel）时把分片留在 files/download_parts 下 */
class ResumableDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("id") ?: return@withContext Result.failure()
        val userAgent = inputData.getString("userAgent").orEmpty()
        val task = ResumableDownloads.find(applicationContext, id)
            ?: return@withContext Result.failure()
        val part = ResumableDownloads.partFile(applicationContext, id)
        var startBytes = if (part.isFile) part.length() else 0L
        var connection: HttpURLConnection? = null
        try {
            ResumableDownloads.update(
                applicationContext,
                task.copy(status = ResumableDownloads.STATUS_RUNNING, bytes = startBytes)
            )
            connection = (URL(task.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (userAgent.isNotBlank()) setRequestProperty("User-Agent", userAgent)
                if (startBytes > 0) setRequestProperty("Range", "bytes=$startBytes-")
            }
            connection.connect()
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK && startBytes > 0) {
                // 服务器不支持断点续传：丢弃分片从头下载
                part.delete()
                startBytes = 0
            } else if (code == 416) {
                val total = parseTotal(connection.getHeaderField("Content-Range"))
                if (total > 0 && startBytes >= total) {
                    finish(task, part, total)
                    return@withContext Result.success()
                }
                ResumableDownloads.update(
                    applicationContext,
                    task.copy(status = ResumableDownloads.STATUS_FAILED, message = "续传位置无效")
                )
                return@withContext Result.failure()
            } else if (code !in 200..299) {
                ResumableDownloads.update(
                    applicationContext,
                    task.copy(
                        status = ResumableDownloads.STATUS_FAILED,
                        message = "服务器返回 $code"
                    )
                )
                return@withContext Result.failure()
            }
            val totalBytes = when {
                code == 206 -> parseTotal(connection.getHeaderField("Content-Range"))
                else -> connection.contentLengthLong
            }
            var written = startBytes
            var lastPush = 0L
            ResumableDownloads.update(
                applicationContext,
                task.copy(
                    status = ResumableDownloads.STATUS_RUNNING,
                    bytes = written,
                    totalBytes = totalBytes
                )
            )
            part.outputStream().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastPush > 800) {
                            lastPush = now
                            ResumableDownloads.update(
                                applicationContext,
                                task.copy(
                                    status = ResumableDownloads.STATUS_RUNNING,
                                    bytes = written,
                                    totalBytes = totalBytes
                                )
                            )
                        }
                    }
                }
            }
            if (totalBytes > 0 && written < totalBytes) {
                ResumableDownloads.update(
                    applicationContext,
                    task.copy(
                        status = ResumableDownloads.STATUS_FAILED,
                        bytes = written,
                        totalBytes = totalBytes,
                        message = "连接提前结束，可继续续传"
                    )
                )
                return@withContext Result.failure()
            }
            finish(task, part, if (totalBytes > 0) totalBytes else written)
            Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            val length = if (part.isFile) part.length() else 0L
            ResumableDownloads.update(
                applicationContext,
                ResumableDownloads.find(applicationContext, id)?.copy(
                    status = ResumableDownloads.STATUS_PAUSED,
                    bytes = length,
                    message = "已暂停，分片已保留"
                ) ?: task
            )
            Result.failure()
        } catch (error: Exception) {
            val length = if (part.isFile) part.length() else 0L
            ResumableDownloads.update(
                applicationContext,
                ResumableDownloads.find(applicationContext, id)?.copy(
                    status = ResumableDownloads.STATUS_FAILED,
                    bytes = length,
                    message = error.message ?: "下载失败"
                ) ?: task
            )
            Result.failure()
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun finish(task: ResumableTask, part: File, total: Long) {
        val uri = ResumableDownloads.saveToDownloads(
            applicationContext,
            task.fileName,
            task.mimeType,
            part
        )
        if (uri == null) {
            ResumableDownloads.update(
                applicationContext,
                task.copy(status = ResumableDownloads.STATUS_FAILED, message = "保存到下载目录失败")
            )
            return
        }
        part.delete()
        ResumableDownloads.update(
            applicationContext,
            task.copy(
                status = ResumableDownloads.STATUS_DONE,
                bytes = total,
                totalBytes = total,
                message = ""
            )
        )
        DownloadNotifier.notifyComplete(applicationContext, task.fileName, uri)
        val app = applicationContext as? com.qingguang.browser.App
        if (app != null) {
            kotlinx.coroutines.runBlocking {
                runCatching {
                    app.database.downloadDao().insert(
                        com.qingguang.browser.data.DownloadRecord(
                            fileName = task.fileName,
                            url = task.url,
                            localPath = task.fileName,
                            status = "下载完成",
                            totalBytes = total,
                            mimeType = task.mimeType,
                            localUri = uri.toString()
                        )
                    )
                }
            }
        }
    }

    private fun parseTotal(contentRange: String?): Long {
        if (contentRange.isNullOrBlank()) return -1
        val total = contentRange.substringAfterLast('/', "")
        return total.toLongOrNull() ?: -1
    }
}

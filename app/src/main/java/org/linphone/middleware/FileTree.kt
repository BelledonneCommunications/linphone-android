package org.linphone.middleware

import android.annotation.SuppressLint
import android.content.Context
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import timber.log.Timber

typealias LogElement = Triple<String, Int, String?>

private var flush = BehaviorSubject.create<Long>()
private var flushCompleted = BehaviorSubject.create<Long>()

private var LOG_LEVELS = arrayOf(
    "",
    "",
    "VERBOSE",
    "DEBUG",
    "INFO",
    "WARN",
    "ERROR",
    "ASSERT"
)

/**
 * ~1.66MB/~450kb gzipped.
 */
private const val LOG_FILE_MAX_SIZE_THRESHOLD = 5 * 1024 * 1024
private val LOG_FILE_RETENTION = TimeUnit.DAYS.toMillis(14)
private val LOG_FILE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
val LOG_LINE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

private const val LOG_FILE_NAME = "application.log"

/**
 * The FileTree is the additional log handler which we plant.
 * It's role is to buffer logs and periodically write them to disk.
 */
@SuppressLint("CheckResult")
class FileTree(val context: Context) : Timber.Tree() {

    private val filePath: String
    private val logBuffer = PublishSubject.create<LogElement>()

    init {
        var processed = 0

        filePath = try {
            getLogsDirectoryFromPath(context.filesDir.absolutePath)
        } catch (e: FileNotFoundException) {
            // Fallback to default path
            context.filesDir.absolutePath
        }

        logBuffer.observeOn(Schedulers.computation())
            .doOnEach {
                processed++

                if (processed % 20 == 0) {
                    flush()
                }
            }
            .buffer(flush.mergeWith(Observable.interval(5, TimeUnit.MINUTES)))
            .subscribeOn(Schedulers.io())
            .subscribe {
                try {
                    // Open file
                    val f = getFile(filePath, LOG_FILE_NAME)

                    // Write to log
                    FileWriter(f, true).use { fw ->
                        // Write log lines to the file
                        it.forEach { (date, priority, message) ->
                            fw.append(
                                "$date\t${LOG_LEVELS[priority]}\t$message\n"
                            )
                        }

                        // Write a line indicating the number of log lines proceed
                        fw.append(
                            "${LOG_LINE_TIME_FORMAT.format(Date())}\t${LOG_LEVELS[2] /* Verbose */}\tFlushing logs -- total processed: $processed\n"
                        )

                        fw.flush()
                    }

                    // Validate file size
                    flushCompleted.onNext(f.length())
                } catch (e: Exception) {
                    logException(e)
                }
            }

        flushCompleted
            .subscribeOn(Schedulers.io())
            .filter { filesize -> filesize > LOG_FILE_MAX_SIZE_THRESHOLD }
            .subscribe { rotateLogs() }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        logBuffer.onNext(LogElement(LOG_LINE_TIME_FORMAT.format(Date()), priority, message))
    }

    fun flush(oncomplete: (() -> Unit)? = null) {
        oncomplete?.run {
            Timber.w("Subscribing to flush completion handler")

            flushCompleted
                .take(1)
                .timeout(2, TimeUnit.SECONDS)
                .subscribeOn(Schedulers.io())
                .onErrorReturn { -1L }
                .filter { it > 0 }
                .subscribe {
                    rotateLogs()

                    // Delegate back to caller
                    oncomplete()
                }
        }

        flush.onNext(1L)
    }

    fun rotateLogs() {
        rotateLogs(filePath, LOG_FILE_NAME)
    }

    companion object {
        fun getLogsDirectory(context: Context): String {
            return getLogsDirectoryFromPath(context.filesDir.absolutePath)
        }

        fun zipAll(logsFolder: String): File {
            return zipAllLogs(logsFolder)
        }
    }
}

private fun rotateLogs(path: String, name: String) {
    val file = getFile(path, name)

    if (!compress(file)) {
        // Unable to compress file
        return
    }

    // Truncate the file to zero.
    PrintWriter(file).close()

    // Iterate over the gzipped files in the directory and delete the files outside the
    // retention period.
    val currentTime = System.currentTimeMillis()
    file.parentFile.listFiles()
        ?.filter {
            it.extension.toLowerCase(Locale.ROOT) == "gz" &&
                it.lastModified() + LOG_FILE_RETENTION < currentTime
        }?.map { it.delete() }
}

private fun getLogsDirectoryFromPath(path: String): String {
    val dir = File(path, "logs")

    if (!dir.exists() && !dir.mkdirs()) {
        throw FileNotFoundException("Unable to create logs file")
    }

    return dir.absolutePath
}

private fun getFile(path: String, name: String): File {
    val file = File(path, name)

    if (!file.exists() && !file.createNewFile()) {
        throw IOException("Unable to load log file")
    }

    if (!file.canWrite()) {
        throw IOException("Log file not writable")
    }

    return file
}

private fun compress(file: File): Boolean {
    try {
        val compressed = File(
            file.parentFile.absolutePath,
            "${file.name.substringBeforeLast(".")}_${LOG_FILE_TIME_FORMAT.format(Date())}.gz"
        )

        FileInputStream(file).use { fis ->
            FileOutputStream(compressed).use { fos ->
                GZIPOutputStream(fos).use { gzos ->

                    val buffer = ByteArray(1024)
                    var length = fis.read(buffer)

                    while (length > 0) {
                        gzos.write(buffer, 0, length)

                        length = fis.read(buffer)
                    }

                    // Finish file compressing and close all streams.
                    gzos.finish()
                }
            }
        }
    } catch (e: IOException) {
        logException(e)

        return false
    }

    return true
}

private fun logException(ex: Exception) {
}

private fun zipAllLogs(logsFolder: String): File {
    val inputDirectory = File(logsFolder)
    val outputZipFile = File.createTempFile("out", ".zip")
    ZipOutputStream(BufferedOutputStream(FileOutputStream(outputZipFile))).use { zos ->
        inputDirectory.walkTopDown()
            .filter { file -> file.extension == "log" || file.extension == "json" }
            .forEach { file ->
                val zipFileName = file.absolutePath.removePrefix(inputDirectory.absolutePath).removePrefix(
                    "/"
                )
                val entry = ZipEntry("$zipFileName${(if (file.isDirectory) "/" else "")}")
                zos.putNextEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { fis -> fis.copyTo(zos) }
                }
            }
    }
    return outputZipFile
}

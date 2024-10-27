package at.rayman.savethespire

import io.github.lumkit.io.LintFile
import io.github.lumkit.io.file
import io.github.lumkit.io.openInputStream
import io.github.lumkit.io.openOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val BUFFER_SIZE = 6 * 1024

object Zipper {

    fun zip(directory: LintFile): Result {
        try {
            ZipOutputStream(FileOutputStream(ZIP_PATH)).use { zos ->
                zipDirectory(directory, "", zos)
            }
            return Result.success("Zip created")
        } catch (e: Exception) {
            return Result.error(e.message ?: "Unknown error while zipping")
        }
    }

    private fun zipDirectory(directory: LintFile, path: String, zos: ZipOutputStream) {
        if (!directory.isDirectory()) {
            throw IOException("Source path must be a directory.")
        }

        val files = directory.listFiles()

        for (file in files) {
            val filePath = path + file.name
            if (file.isDirectory()) {
                zipDirectory(file, "$filePath/", zos)
            } else {
                BufferedInputStream(file.openInputStream(), BUFFER_SIZE).use { bis ->
                    zos.putNextEntry(ZipEntry(filePath))
                    val buffer = ByteArray(BUFFER_SIZE)
                    var len: Int
                    while (bis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
            }
        }
    }

    fun unzip(zipFile: LintFile): Result {
        try {
            ZipInputStream(zipFile.openInputStream()).use { zis ->
                var zipEntry = zis.nextEntry
                while (zipEntry != null) {
                    val newFile = file("$STS_PATH/${zipEntry.name}")
                    if (zipEntry.isDirectory) {
                        newFile.mkdirs()
                    } else {
                        unzipFile(zis, newFile)
                    }
                    zipEntry = zis.nextEntry
                }
            }
            return Result.success("Unzipping done")
        } catch (e: Exception) {
            return Result.error(e.message ?: "Unknown error while unzipping")
        }
    }

    private fun unzipFile(zis: ZipInputStream, newFile: LintFile) {
        BufferedOutputStream(newFile.openOutputStream(), BUFFER_SIZE).use { bos ->
            val buffer = ByteArray(BUFFER_SIZE)
            var len: Int
            while (zis.read(buffer).also { len = it } > 0) {
                bos.write(buffer, 0, len)
            }
        }
    }

}
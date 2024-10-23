package at.rayman.savethespire

import io.github.lumkit.io.LintFile
import io.github.lumkit.io.openInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private const val BUFFER_SIZE = 6 * 1024

object ZipperKt {

    fun zip(directory: LintFile, zipName: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipName))).use { zos ->
            zipDirectory(directory, "", zos)
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

}
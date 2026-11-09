package com.example

import com.example.filemanager.archive.ArchiveManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExampleUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testZipCreatePreviewAndExtract() = runBlocking {
        val workDir = tempFolder.newFolder("zip_test")
        val sample1 = File(workDir, "hello.txt").apply { writeText("Xin chào File Manager Pro!") }
        val sample2 = File(workDir, "config.ts").apply { writeText("export const API_VERSION = 2;\nconsole.log('test');") }

        val zipFile = File(workDir, "test_archive.zip")

        // 1. Test zip creation with high speed
        val createResult = ArchiveManager.createZip(listOf(sample1, sample2), zipFile)
        assertTrue(createResult.isSuccess)
        assertTrue(zipFile.exists() && zipFile.length() > 0)

        // 2. Test list archive entries
        val listResult = ArchiveManager.listArchiveEntries(zipFile)
        assertTrue(listResult.isSuccess)
        val entries = listResult.getOrThrow()
        assertEquals(2, entries.size)
        assertTrue(entries.any { it.name == "hello.txt" })
        assertTrue(entries.any { it.name == "config.ts" })

        // 3. Test read text inside zip directly without extracting
        val readResult = ArchiveManager.readEntryText(zipFile, "hello.txt")
        assertTrue(readResult.isSuccess)
        assertEquals("Xin chào File Manager Pro!", readResult.getOrThrow())

        val readTsResult = ArchiveManager.readEntryText(zipFile, "config.ts")
        assertTrue(readTsResult.isSuccess)
        assertTrue(readTsResult.getOrThrow().contains("API_VERSION = 2"))

        // 4. Test integrity check
        val integrityResult = ArchiveManager.testArchiveIntegrity(zipFile)
        assertTrue(integrityResult.isSuccess)

        // 5. Test extract all
        val outDir = File(workDir, "extracted")
        val extractResult = ArchiveManager.extractAll(zipFile, outDir)
        assertTrue(extractResult.isSuccess)
        assertEquals(2, extractResult.getOrThrow())
        assertTrue(File(outDir, "hello.txt").exists())
        assertEquals("Xin chào File Manager Pro!", File(outDir, "hello.txt").readText())
    }
}

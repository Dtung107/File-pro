package com.example

import com.example.filemanager.archive.ArchiveEntryInfo
import com.example.filemanager.archive.ArchiveManager
import com.example.filemanager.archive.ZipSortOption
import com.example.filemanager.archive.ZipTreeBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

        // 4. Test read bytes directly (for images / binaries)
        val byteResult = ArchiveManager.readEntryBytes(zipFile, "hello.txt")
        assertTrue(byteResult.isSuccess)
        assertTrue(byteResult.getOrThrow().isNotEmpty())

        // 5. Test integrity check
        val integrityResult = ArchiveManager.testArchiveIntegrity(zipFile)
        assertTrue(integrityResult.isSuccess)

        // 6. Test extract all
        val outDir = File(workDir, "extracted")
        val extractResult = ArchiveManager.extractAll(zipFile, outDir)
        assertTrue(extractResult.isSuccess)
        assertEquals(2, extractResult.getOrThrow())
        assertTrue(File(outDir, "hello.txt").exists())
        assertEquals("Xin chào File Manager Pro!", File(outDir, "hello.txt").readText())
    }

    @Test
    fun testZipTreeBuilderHierarchicalStructure() {
        val mockEntries = listOf(
            ArchiveEntryInfo("Android/", "Android/", 0, 0, true, 1000L),
            ArchiveEntryInfo("data/", "Android/data/", 0, 0, true, 1000L),
            ArchiveEntryInfo("com.game/", "Android/data/com.game/", 0, 0, true, 1000L),
            ArchiveEntryInfo("files/", "Android/data/com.game/files/", 0, 0, true, 1000L),
            ArchiveEntryInfo("data.dat", "Android/data/com.game/files/data.dat", 4096, 2048, false, 1000L),
            ArchiveEntryInfo("config.bin", "Android/data/com.game/files/config.bin", 1024, 512, false, 1000L),
            ArchiveEntryInfo("obb/", "Android/obb/", 0, 0, true, 1000L),
            ArchiveEntryInfo("assets/", "assets/", 0, 0, true, 1000L),
            ArchiveEntryInfo("textures/", "assets/textures/", 0, 0, true, 1000L),
            ArchiveEntryInfo("player.png", "assets/textures/player.png", 8192, 4096, false, 1000L),
            ArchiveEntryInfo("version.txt", "version.txt", 128, 64, false, 1000L)
        )

        val root = ZipTreeBuilder.buildTree(mockEntries)

        // Check top-level children
        assertEquals(3, root.children.size)
        assertTrue(root.children.containsKey("Android"))
        assertTrue(root.children.containsKey("assets"))
        assertTrue(root.children.containsKey("version.txt"))

        // Check nested hierarchy: Android -> data -> com.game -> files -> data.dat
        val androidNode = root.children["Android"]
        assertNotNull(androidNode)
        assertTrue(androidNode!!.isDirectory)

        val dataNode = androidNode.children["data"]
        assertNotNull(dataNode)

        val gameNode = dataNode!!.children["com.game"]
        assertNotNull(gameNode)

        val filesNode = gameNode!!.children["files"]
        assertNotNull(filesNode)
        assertEquals(2, filesNode!!.children.size)
        assertTrue(filesNode.children.containsKey("data.dat"))
        assertTrue(filesNode.children.containsKey("config.bin"))

        val dataDat = filesNode.children["data.dat"]
        assertNotNull(dataDat)
        assertEquals(4096L, dataDat!!.size)
        assertEquals("dat", dataDat.extension)

        // Check breadcrumb generation
        val crumbs = dataDat.getBreadcrumbs()
        assertEquals(6, crumbs.size) // /, Android, data, com.game, files, data.dat
        assertEquals("/", crumbs[0].name)
        assertEquals("Android", crumbs[1].name)
        assertEquals("data.dat", crumbs[5].name)

        // Check recursive file and dir count
        assertEquals(4, root.fileCount) // data.dat, config.bin, player.png, version.txt
        assertTrue(root.totalSize > 0)

        // Check search nodes
        val searchData = ZipTreeBuilder.searchNodes(root, "data")
        assertTrue(searchData.any { it.name == "data.dat" })
        assertTrue(searchData.any { it.name == "data" })

        // Check sort children
        val sorted = ZipTreeBuilder.sortChildren(root.children.values, ZipSortOption.NAME, true)
        // Directories first (Android, assets), then files (version.txt)
        assertTrue(sorted[0].isDirectory)
        assertTrue(sorted[1].isDirectory)
        assertEquals("version.txt", sorted[2].name)
    }
}

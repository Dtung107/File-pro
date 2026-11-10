package com.example.filemanager.archive

data class ZipNode(
    val name: String,
    val fullPath: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val compressedSize: Long = 0L,
    val time: Long = 0L,
    val children: MutableMap<String, ZipNode> = mutableMapOf(),
    var parent: ZipNode? = null
) {
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    val fileCount: Int
        get() = if (isDirectory) children.values.count { !it.isDirectory } + children.values.sumOf { it.fileCount } else 0

    val dirCount: Int
        get() = if (isDirectory) children.values.count { it.isDirectory } + children.values.sumOf { it.dirCount } else 0

    val directChildrenCount: Int
        get() = children.size

    val totalSize: Long
        get() = if (isDirectory) children.values.sumOf { it.totalSize } else size

    val compressionRatio: Float
        get() = if (size > 0 && compressedSize > 0) {
            ((1f - (compressedSize.toFloat() / size.toFloat())) * 100f).coerceIn(0f, 100f)
        } else 0f

    fun getBreadcrumbs(): List<ZipNode> {
        val list = mutableListOf<ZipNode>()
        var curr: ZipNode? = this
        while (curr != null) {
            list.add(0, curr)
            curr = curr.parent
        }
        return list
    }
}

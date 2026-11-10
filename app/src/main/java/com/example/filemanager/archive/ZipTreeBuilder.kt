package com.example.filemanager.archive

enum class ZipSortOption {
    NAME,
    SIZE,
    TYPE,
    DATE
}

object ZipTreeBuilder {

    fun buildTree(entries: List<ArchiveEntryInfo>): ZipNode {
        val root = ZipNode(
            name = "/",
            fullPath = "",
            isDirectory = true
        )

        for (entry in entries) {
            val normalizedPath = entry.path.replace('\\', '/').trimStart('/')
            if (normalizedPath.isEmpty()) continue

            val segments = normalizedPath.split('/').filter { it.isNotEmpty() }
            var currentNode = root

            for (i in segments.indices) {
                val segName = segments[i]
                val isLast = i == segments.lastIndex
                val isDir = if (isLast) entry.isDirectory else true
                val curFullPath = segments.take(i + 1).joinToString("/") + if (isDir) "/" else ""

                val existing = currentNode.children[segName]
                if (existing != null) {
                    // Update entry metadata if current is the real entry
                    if (isLast) {
                        currentNode = existing.copy(
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            time = entry.lastModified
                        ).also {
                            it.children.putAll(existing.children)
                            it.parent = existing.parent
                            currentNode.children[segName] = it
                        }
                    } else {
                        currentNode = existing
                    }
                } else {
                    val newNode = ZipNode(
                        name = segName,
                        fullPath = curFullPath,
                        isDirectory = isDir,
                        size = if (isLast) entry.size else 0L,
                        compressedSize = if (isLast) entry.compressedSize else 0L,
                        time = if (isLast) entry.lastModified else 0L,
                        parent = currentNode
                    )
                    currentNode.children[segName] = newNode
                    currentNode = newNode
                }
            }
        }

        return root
    }

    fun searchNodes(root: ZipNode, query: String): List<ZipNode> {
        val results = mutableListOf<ZipNode>()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        fun dfs(node: ZipNode) {
            for (child in node.children.values) {
                if (child.name.lowercase().contains(q) || child.fullPath.lowercase().contains(q)) {
                    results.add(child)
                }
                if (child.isDirectory) {
                    dfs(child)
                }
            }
        }

        dfs(root)
        return results
    }

    fun sortChildren(
        nodes: Collection<ZipNode>,
        sortBy: ZipSortOption,
        ascending: Boolean
    ): List<ZipNode> {
        val comparator = when (sortBy) {
            ZipSortOption.NAME -> compareBy<ZipNode> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ZipSortOption.SIZE -> compareBy<ZipNode> { !it.isDirectory }.thenBy { it.totalSize }
            ZipSortOption.TYPE -> compareBy<ZipNode> { !it.isDirectory }.thenBy { it.extension }.thenBy { it.name.lowercase() }
            ZipSortOption.DATE -> compareBy<ZipNode> { !it.isDirectory }.thenBy { it.time }
        }

        val sorted = nodes.sortedWith(comparator)
        return if (ascending) sorted else {
            // Keep directories on top even when descending
            val dirs = sorted.filter { it.isDirectory }.reversed()
            val files = sorted.filter { !it.isDirectory }.reversed()
            dirs + files
        }
    }
}

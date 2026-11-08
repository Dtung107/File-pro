package com.example.filemanager.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TableColumnInfo(
    val cid: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val isPrimaryKey: Boolean
)

data class TableSummary(
    val name: String,
    val rowCount: Long,
    val sqlCreate: String,
    val columns: List<TableColumnInfo>
)

data class TableDataPage(
    val tableName: String,
    val columns: List<String>,
    val rows: List<List<String>>,
    val totalRows: Long,
    val pageIndex: Int,
    val pageSize: Int
)

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String>>,
    val rowCount: Int,
    val executionTimeMs: Long,
    val errorMessage: String? = null
)

object SqliteViewer {

    suspend fun getDatabaseSummary(dbFile: File): Result<List<TableSummary>> = withContext(Dispatchers.IO) {
        try {
            if (!dbFile.exists() || !dbFile.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Không thể đọc tệp cơ sở dữ liệu"))
            }

            val summaries = mutableListOf<TableSummary>()
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

            try {
                // Query tables
                val tableCursor = db.rawQuery(
                    "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name ASC",
                    null
                )

                tableCursor.use { cursor ->
                    while (cursor.moveToNext()) {
                        val tableName = cursor.getString(0)
                        val sqlCreate = cursor.getString(1) ?: ""

                        // Get row count
                        var rowCount = 0L
                        try {
                            val countCursor = db.rawQuery("SELECT COUNT(*) FROM \"$tableName\"", null)
                            countCursor.use { cc ->
                                if (cc.moveToFirst()) {
                                    rowCount = cc.getLong(0)
                                }
                            }
                        } catch (e: Exception) {
                            // Some virtual tables or corrupted tables may fail count
                        }

                        // Get column info
                        val columns = mutableListOf<TableColumnInfo>()
                        try {
                            val pragmaCursor = db.rawQuery("PRAGMA table_info(\"$tableName\")", null)
                            pragmaCursor.use { pc ->
                                while (pc.moveToNext()) {
                                    columns.add(
                                        TableColumnInfo(
                                            cid = pc.getInt(0),
                                            name = pc.getString(1),
                                            type = pc.getString(2) ?: "TEXT",
                                            notNull = pc.getInt(3) == 1,
                                            defaultValue = pc.getString(4),
                                            isPrimaryKey = pc.getInt(5) == 1
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            // Column info failure fallback
                        }

                        summaries.add(
                            TableSummary(
                                name = tableName,
                                rowCount = rowCount,
                                sqlCreate = sqlCreate,
                                columns = columns
                            )
                        )
                    }
                }

                Result.success(summaries)
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTableData(
        dbFile: File,
        tableName: String,
        pageIndex: Int = 0,
        pageSize: Int = 50,
        sortColumn: String? = null,
        sortAscending: Boolean = true,
        searchQuery: String? = null
    ): Result<TableDataPage> = withContext(Dispatchers.IO) {
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val offset = pageIndex * pageSize
                var sql = "SELECT * FROM \"$tableName\""

                // Optional search
                val searchArgs = mutableListOf<String>()
                if (!searchQuery.isNullOrBlank()) {
                    // Get columns to search across
                    val colNames = mutableListOf<String>()
                    val pragma = db.rawQuery("PRAGMA table_info(\"$tableName\")", null)
                    pragma.use { p ->
                        while (p.moveToNext()) {
                            colNames.add(p.getString(1))
                        }
                    }
                    if (colNames.isNotEmpty()) {
                        val whereClauses = colNames.joinToString(" OR ") { "\"$it\" LIKE ?" }
                        sql += " WHERE ($whereClauses)"
                        colNames.forEach { _ -> searchArgs.add("%$searchQuery%") }
                    }
                }

                if (!sortColumn.isNullOrBlank()) {
                    sql += " ORDER BY \"$sortColumn\" " + (if (sortAscending) "ASC" else "DESC")
                }

                sql += " LIMIT $pageSize OFFSET $offset"

                val cursor = db.rawQuery(sql, if (searchArgs.isNotEmpty()) searchArgs.toTypedArray() else null)
                val columns = mutableListOf<String>()
                val rows = mutableListOf<List<String>>()

                cursor.use { c ->
                    columns.addAll(c.columnNames)
                    while (c.moveToNext()) {
                        val row = mutableListOf<String>()
                        for (i in 0 until c.columnCount) {
                            row.add(getCursorValue(c, i))
                        }
                        rows.add(row)
                    }
                }

                // Total rows count
                var totalRows = 0L
                val countCursor = db.rawQuery("SELECT COUNT(*) FROM \"$tableName\"", null)
                countCursor.use { cc ->
                    if (cc.moveToFirst()) totalRows = cc.getLong(0)
                }

                Result.success(
                    TableDataPage(
                        tableName = tableName,
                        columns = columns,
                        rows = rows,
                        totalRows = totalRows,
                        pageIndex = pageIndex,
                        pageSize = pageSize
                    )
                )
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun executeSelectQuery(dbFile: File, rawQuery: String): QueryResult = withContext(Dispatchers.IO) {
        val trimmed = rawQuery.trim()
        val lower = trimmed.lowercase()

        // Strict read-only enforcement
        if (!lower.startsWith("select") && !lower.startsWith("pragma") && !lower.startsWith("explain")) {
            return@withContext QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = 0,
                errorMessage = "Chỉ cho phép chạy truy vấn đọc dữ liệu (SELECT, PRAGMA) trên cơ sở dữ liệu."
            )
        }

        val startTime = System.currentTimeMillis()
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val cursor = db.rawQuery(trimmed, null)
                val columns = mutableListOf<String>()
                val rows = mutableListOf<List<String>>()

                cursor.use { c ->
                    columns.addAll(c.columnNames)
                    // Limit up to 500 records for memory safety
                    var count = 0
                    while (c.moveToNext() && count < 500) {
                        val row = mutableListOf<String>()
                        for (i in 0 until c.columnCount) {
                            row.add(getCursorValue(c, i))
                        }
                        rows.add(row)
                        count++
                    }
                }

                val execTime = System.currentTimeMillis() - startTime
                QueryResult(
                    columns = columns,
                    rows = rows,
                    rowCount = rows.size,
                    executionTimeMs = execTime
                )
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            QueryResult(
                columns = emptyList(),
                rows = emptyList(),
                rowCount = 0,
                executionTimeMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message ?: "Lỗi thực thi truy vấn SQL"
            )
        }
    }

    suspend fun exportTableToCsv(dbFile: File, tableName: String, outputFile: File): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            var count = 0L
            try {
                outputFile.bufferedWriter().use { writer ->
                    val cursor = db.rawQuery("SELECT * FROM \"$tableName\"", null)
                    cursor.use { c ->
                        // Header
                        writer.write(c.columnNames.joinToString(",") { escapeCsv(it) })
                        writer.newLine()

                        while (c.moveToNext()) {
                            val row = (0 until c.columnCount).map { i ->
                                escapeCsv(getCursorValue(c, i))
                            }
                            writer.write(row.joinToString(","))
                            writer.newLine()
                            count++
                        }
                    }
                }
                Result.success(count)
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportTableToJson(dbFile: File, tableName: String, outputFile: File): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            var count = 0L
            try {
                outputFile.bufferedWriter().use { writer ->
                    writer.write("[\n")
                    val cursor = db.rawQuery("SELECT * FROM \"$tableName\"", null)
                    cursor.use { c ->
                        val colNames = c.columnNames
                        var isFirst = true
                        while (c.moveToNext()) {
                            if (!isFirst) writer.write(",\n")
                            val obj = JSONObject()
                            for (i in 0 until c.columnCount) {
                                obj.put(colNames[i], getCursorValue(c, i))
                            }
                            writer.write("  " + obj.toString())
                            isFirst = false
                            count++
                        }
                    }
                    writer.write("\n]")
                }
                Result.success(count)
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun getCursorValue(c: Cursor, index: Int): String {
        return when (c.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> "NULL"
            Cursor.FIELD_TYPE_INTEGER -> c.getLong(index).toString()
            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(index).toString()
            Cursor.FIELD_TYPE_STRING -> c.getString(index) ?: ""
            Cursor.FIELD_TYPE_BLOB -> {
                val blob = c.getBlob(index)
                if (blob == null) "NULL" else "[BLOB ${blob.size} bytes]"
            }
            else -> c.getString(index) ?: ""
        }
    }
}

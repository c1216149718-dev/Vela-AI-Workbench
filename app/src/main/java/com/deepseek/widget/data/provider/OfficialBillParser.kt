package com.deepseek.widget.data.provider

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** CSV, DeepSeek ZIP, and first-sheet XLSX parser with an explicit preview step. */
object OfficialBillParser {
    fun preview(providerId: ProviderId, fileName: String, bytes: ByteArray): BillImportPreview {
        val lower = fileName.lowercase()
        val tables = when {
            lower.endsWith(".csv") -> listOf(parseCsv(bytes.toString(Charsets.UTF_8)))
            lower.endsWith(".xlsx") -> listOf(parseXlsx(bytes))
            lower.endsWith(".zip") -> parseZip(bytes)
            else -> emptyList()
        }
        val warnings = mutableListOf<String>()
        if (tables.isEmpty()) warnings += "仅支持 CSV、XLSX 与 DeepSeek ZIP"
        val records = tables.flatMap { table -> tableToUsage(table, warnings) }
        return BillImportPreview(
            providerId,
            records.map { it.currency.uppercase() }.filter { it.isNotBlank() }.toSet(),
            records.minOfOrNull { it.date },
            records.maxOfOrNull { it.date },
            records,
            warnings.distinct()
        )
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun parseZip(bytes: ByteArray): List<List<List<String>>> {
        val tables = mutableListOf<List<List<String>>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".csv")) {
                    tables += parseCsv(zip.readBytes().toString(Charsets.UTF_8))
                }
            }
        }
        return tables
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        val normalized = text.removePrefix("\uFEFF")
        while (index < normalized.length) {
            val char = normalized[index]
            when {
                char == '"' && quoted && index + 1 < normalized.length && normalized[index + 1] == '"' -> { cell.append('"'); index++ }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { row += cell.toString().trim(); cell.clear() }
                (char == '\n' || char == '\r') && !quoted -> {
                    if (char == '\r' && index + 1 < normalized.length && normalized[index + 1] == '\n') index++
                    row += cell.toString().trim(); cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(char)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString().trim(); if (row.any { it.isNotBlank() }) rows += row }
        return rows
    }

    private fun parseXlsx(bytes: ByteArray): List<List<String>> {
        var sharedXml: ByteArray? = null
        var sheetXml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == "xl/sharedStrings.xml" -> sharedXml = zip.readBytes()
                    entry.name == "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes()
                }
            }
        }
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val shared = sharedXml?.let { xml ->
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
            val nodes = doc.getElementsByTagName("si")
            (0 until nodes.length).map { index -> (nodes.item(index) as Element).getElementsByTagName("t").let { texts -> (0 until texts.length).joinToString("") { texts.item(it).textContent } } }
        }.orEmpty()
        val sheet = sheetXml ?: return emptyList()
        val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(sheet))
        val rows = doc.getElementsByTagName("row")
        return (0 until rows.length).map { rowIndex ->
            val cells = (rows.item(rowIndex) as Element).getElementsByTagName("c")
            val values = mutableMapOf<Int, String>()
            var max = -1
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as Element
                val column = cell.getAttribute("r").takeWhile { it.isLetter() }.fold(0) { acc, c -> acc * 26 + (c.uppercaseChar() - 'A' + 1) } - 1
                val raw = cell.getElementsByTagName("v").item(0)?.textContent.orEmpty()
                val value = if (cell.getAttribute("t") == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                values[column] = value
                max = maxOf(max, column)
            }
            (0..max).map { values[it].orEmpty() }
        }
    }

    private fun tableToUsage(table: List<List<String>>, warnings: MutableList<String>): List<DailyUsagePoint> {
        if (table.size < 2) return emptyList()
        val headers = table.first().map { normalize(it) }
        fun column(vararg aliases: String): Int = headers.indexOfFirst { header -> aliases.any { normalize(it) == header } }
        val dateColumn = column("date", "usage_date", "billing_date", "日期", "时间")
        val modelColumn = column("model", "model_name", "模型")
        val costColumn = column("actual_cost", "cost", "amount", "费用", "金额", "实扣")
        val currencyColumn = column("currency", "币种")
        val requestsColumn = column("requests", "request_count", "请求数")
        val inputColumn = column("input_tokens", "输入token")
        val outputColumn = column("output_tokens", "输出token")
        val totalColumn = column("total_tokens", "token", "tokens", "总token")
        if (dateColumn < 0 || costColumn < 0) {
            warnings += "账单缺少日期或费用列"
            return emptyList()
        }
        return table.drop(1).mapNotNull { row ->
            val date = row.getOrNull(dateColumn)?.let(::parseDate) ?: return@mapNotNull null
            DailyUsagePoint(
                date = date,
                model = row.getOrNull(modelColumn).orEmpty().ifBlank { "__all__" },
                currency = row.getOrNull(currencyColumn).orEmpty().ifBlank { "USD" }.uppercase(),
                cost = row.getOrNull(costColumn)?.replace(",", "")?.toBigDecimalOrNull() ?: return@mapNotNull null,
                requests = row.getOrNull(requestsColumn)?.toLongOrNull(),
                inputTokens = row.getOrNull(inputColumn)?.toLongOrNull(),
                outputTokens = row.getOrNull(outputColumn)?.toLongOrNull(),
                totalTokens = row.getOrNull(totalColumn)?.toLongOrNull(),
                provenance = MetricProvenance.EXACT_IMPORT,
                sourceId = "official-import"
            )
        }
    }

    private fun normalize(value: String) = value.trim().lowercase().replace(" ", "_").replace("-", "_")
    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim().take(10).replace('/', '-')) }.getOrNull()
}

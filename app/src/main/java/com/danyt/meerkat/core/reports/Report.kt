package com.danyt.meerkat.core.reports

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Which tool a report came from. [code] is the name used in files, so it must never change. */
enum class ReportKind(val code: String) {
    Lan("lan"),
    Ports("ports"),
    Wifi("wifi"),
    Bluetooth("bluetooth"),
    Cell("cell"),
    Nfc("nfc"),
}

/**
 * The saved result of one scan, as a table with some facts about it.
 *
 * A report holds finished text, not live scan data, so the Reports tool can show and export any tool's results
 * without knowing that tool.
 *
 * @property id set by [ReportStore] when the report is saved. Empty for a report that is not stored.
 * @property title what was scanned, such as "192.168.1.0/24".
 * @property name a title the user gave the report. Empty means "use [title]".
 * @property description the user's own notes about the report.
 * @property createdAt when the scan was run, in milliseconds since 1970 UTC.
 * @property details facts about the scan as label and value, such as "Profile" and "Quick".
 * @property columns the table's column titles.
 * @property rows the table's cells, one list per row, each as long as [columns].
 */
data class Report(
    val id: String = "",
    val kind: ReportKind,
    val title: String,
    val createdAt: Long,
    val details: List<Pair<String, String>>,
    val columns: List<String>,
    val rows: List<List<String>>,
    val name: String = "",
    val description: String = "",
) {
    /** The title to show: the user's own when there is one, the scanned target otherwise. */
    val displayTitle: String get() = name.ifBlank { title }
}

/** Why a file could not be read as a report. */
class ReportFormatException(message: String) : Exception(message)

private const val FORMAT_NAME = "meerkat-report"
private const val FORMAT_VERSION = 1L

/** Limits for imported files, so a huge or hostile file cannot exhaust memory or fill the screen. */
const val MAX_REPORT_BYTES = 2 * 1024 * 1024
private const val MAX_ROWS = 20_000
private const val MAX_COLUMNS = 32
private const val MAX_DETAILS = 64
private const val MAX_TEXT = 2_000

/**
 * Everything that makes up the scan the report records. Left out are the id, which only says where a copy is
 * stored, and the user's own name and notes, which can be edited without the scan becoming a different one.
 */
private fun Report.content(): Map<String, Any?> = linkedMapOf(
    "format" to FORMAT_NAME,
    "version" to FORMAT_VERSION,
    "kind" to kind.code,
    "title" to title,
    "createdAt" to createdAt,
    "details" to details.map { listOf(it.first, it.second) },
    "columns" to columns,
    "rows" to rows,
)

/**
 * The report's fingerprint: the SHA-256 of its content, as 64 hex characters.
 *
 * The same scan always gives the same fingerprint, whether the report was just made, saved, renamed,
 * exported, or imported, so it can be used to recognize a copy. It has nothing to do with the encryption of
 * saved reports; it is a plain checksum of the content. Two different reports sharing one is possible only in
 * theory, so a match is a strong hint and not a proof.
 */
fun Report.contentHash(): String =
    MessageDigest.getInstance("SHA-256").digest(Json.write(content()).toByteArray(Charsets.UTF_8))
        .joinToString("") { String.format(Locale.US, "%02x", it) }

/**
 * Writes the report as JSON. This is the storage format and the only export that can be imported again.
 *
 * The fingerprint and a readable UTC time are added for people reading the file. Both are worked out again
 * from the content when the file is read, never trusted.
 */
fun Report.toJson(): String = Json.write(
    content() + mapOf(
        "name" to name,
        "description" to description,
        "createdAtUtc" to formatReportDateUtc(createdAt),
        "sha256" to contentHash(),
    ),
)

/**
 * Reads a report from JSON written by [toJson]. The result has no id.
 *
 * Every field is checked, and sizes are limited, because the text may come from an imported file. Throws
 * [ReportFormatException] when the text is not a report this version understands.
 */
fun reportFromJson(text: String): Report {
    val root = try {
        Json.read(text) as? Map<*, *>
    } catch (e: JsonException) {
        throw ReportFormatException("Not valid JSON: ${e.message}")
    } ?: throw ReportFormatException("Not a report")
    if (root["format"] != FORMAT_NAME) throw ReportFormatException("Not a Meerkat report")
    if (root["version"] != FORMAT_VERSION) throw ReportFormatException("Unsupported report version")

    val kind = ReportKind.entries.firstOrNull { it.code == root["kind"] }
        ?: throw ReportFormatException("Unknown kind")
    val columns = textList(root["columns"], MAX_COLUMNS)
    val rows = (root["rows"] as? List<*>)?.takeIf { it.size <= MAX_ROWS }?.map { textList(it, MAX_COLUMNS) }
        ?: throw ReportFormatException("Bad rows")
    if (rows.any { it.size != columns.size }) throw ReportFormatException("A row does not match the columns")
    val details = (root["details"] as? List<*>)?.takeIf { it.size <= MAX_DETAILS }?.map {
        val pair = textList(it, 2)
        if (pair.size != 2) throw ReportFormatException("Bad detail")
        pair[0] to pair[1]
    } ?: throw ReportFormatException("Bad details")
    return Report(
        kind = kind,
        title = text(root["title"]),
        createdAt = root["createdAt"] as? Long ?: throw ReportFormatException("Bad date"),
        details = details,
        columns = columns,
        rows = rows,
        // Added after the first files were written, so both may be missing.
        name = root["name"]?.let(::text).orEmpty(),
        description = root["description"]?.let(::text).orEmpty(),
    )
}

private fun text(value: Any?): String =
    (value as? String)?.takeIf { it.length <= MAX_TEXT } ?: throw ReportFormatException("Bad text")

private fun textList(value: Any?, maxSize: Int): List<String> =
    (value as? List<*>)?.takeIf { it.size <= maxSize }?.map(::text) ?: throw ReportFormatException("Bad list")

/**
 * Writes the table as CSV: a header row, then the rows. Details are not included, so the file opens cleanly
 * in a spreadsheet.
 *
 * Cells that a spreadsheet would run as a formula are prefixed with an apostrophe. Device names come from the
 * network, so a hostile device could otherwise plant a formula in the export.
 */
fun Report.toCsv(): String = buildString {
    (listOf(columns) + rows).forEach { row ->
        append(row.joinToString(",") { csvCell(it) })
        append("\r\n")
    }
}

private fun csvCell(value: String): String {
    val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
    return if (safe.any { it in ",\"\r\n" }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
}

/** Writes the report as plain text: title, date, details, then the table with aligned columns. */
fun Report.toText(): String = buildString {
    appendLine(displayTitle)
    if (description.isNotBlank()) appendLine(description)
    appendLine(formatReportDateUtc(createdAt))
    appendLine("SHA-256 ${contentHash()}")
    appendLine()
    val labelWidth = details.maxOfOrNull { it.first.length } ?: 0
    details.forEach { (label, value) -> appendLine("${label.padEnd(labelWidth)}  $value") }
    if (details.isNotEmpty()) appendLine()
    val table = (listOf(columns) + rows).map { row -> row.map(::oneLine) }
    val widths = columns.indices.map { column -> table.maxOf { it[column].length } }
    table.forEachIndexed { index, row ->
        appendLine(row.mapIndexed { column, cell -> cell.padEnd(widths[column]) }.joinToString("  ").trimEnd())
        if (index == 0) appendLine(widths.joinToString("  ") { "-".repeat(it) })
    }
}

private fun oneLine(value: String) = value.replace('\n', ' ').replace('\r', ' ')

/** A date and time for the screen, in the user's time zone: "2026-11-16 14:30". */
fun formatReportDate(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))

/** A date and time for files, always in UTC and marked as such: "2026-11-16 13:30:05 UTC". */
fun formatReportDateUtc(millis: Long): String = utcFormat("yyyy-MM-dd HH:mm:ss").format(Date(millis)) + " UTC"

/** A file name for an export, without the extension, with the scan time in UTC: "meerkat-lan-20261116-133005Z". */
fun Report.fileName(): String = "meerkat-${kind.code}-" + utcFormat("yyyyMMdd-HHmmss").format(Date(createdAt)) + "Z"

private fun utcFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

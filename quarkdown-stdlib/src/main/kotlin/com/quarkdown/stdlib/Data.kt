package com.quarkdown.stdlib

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.quarkdown.core.ast.base.block.Table
import com.quarkdown.core.ast.base.inline.Text
import com.quarkdown.core.context.Context
import com.quarkdown.core.function.library.loader.Module
import com.quarkdown.core.function.library.loader.moduleOf
import com.quarkdown.core.function.reflect.annotation.Injected
import com.quarkdown.core.function.reflect.annotation.LikelyNamed
import com.quarkdown.core.function.reflect.annotation.Name
import com.quarkdown.core.function.value.NodeValue
import com.quarkdown.core.function.value.StringValue
import com.quarkdown.core.function.value.data.Range
import com.quarkdown.core.function.value.data.subList
import com.quarkdown.core.function.value.wrappedAsValue
import com.quarkdown.core.util.normalizeLineSeparators
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * `Data` stdlib module exporter.
 * This module handles content fetched from external resources.
 */
val Data: Module =
    moduleOf(
        ::read,
        ::backquote,
        ::csv,
    )

/**
 * @param path path of the file, relative or absolute (with extension)
 * @param requireExistance whether the corresponding file must exist
 * @return a [File] instance of the file located in [path].
 *         If the path is relative, the location is determined by the working directory of the pipeline.
 * @throws IllegalArgumentException if the file does not exist and [requireExistance] is `true`
 */
internal fun file(
    context: Context,
    path: String,
    requireExistance: Boolean = true,
): File {
    val file = context.fileSystem.resolve(path)

    if (requireExistance && !file.exists()) {
        throw IllegalArgumentException("File $file does not exist.")
    }

    return file
}

/**
 * @param path path of the file (with extension)
 * @param lineRange range of lines to extract from the file.
 *                  If not specified or infinite, the whole file is read
 * @return a string value of the text extracted from the file
 * @throws IllegalArgumentException if [lineRange] is out of bounds
 * @wiki File data
 */
fun read(
    @Injected context: Context,
    path: String,
    @Name("lines") lineRange: Range = Range.INFINITE,
): StringValue {
    val file = file(context, path)

    // If the range is infinite on both ends, the whole file is read.
    if (lineRange.isInfinite) {
        return StringValue(file.readText().normalizeLineSeparators().toString())
    }

    // Lines from the file in the given range.
    val lines = file.readLines()

    // Check if the range is in bounds.
    val bounds = Range(1, lines.size)
    if (lineRange !in bounds) {
        throw IllegalArgumentException("Invalid range $lineRange in bounds $bounds")
    }

    return lines
        .subList(lineRange)
        .joinToString("\n")
        .wrappedAsValue()
}

/**
 * @param command  specifies the command line to be invoked in the default shell.
 *                `$SHELL -c COMMAND_STRING`.
 * @return a List of String.
 * @throws IllegalArgumentException if the invocation fails
 */
internal fun execString(command: String): List<String> {
    val env = System.getenv()
    val shell = env.getOrDefault("SHELL", "/bin/bash")
    val pb = ProcessBuilder(shell, "-c", command)
    val p = pb.start()

    val inS = p.getInputStream()
    val bInS = BufferedReader(InputStreamReader(inS))

    var result = ArrayList<String>()
    for (line in bInS.readLines()) {
        result.add(line)
    }
    return result
}

/**
 * @param command   specifies the command line to be invoked in the default shell.
 * @param lineRange range of lines to extract from the file.
 *                  If not specified or infinite, the whole file is read
 * @return a string value of the text extracted from the file
 * @throws IllegalArgumentException if [lineRange] is out of bounds
 * @wiki File data
 */
fun backquote(
    @Injected context: Context,
    command: String,
    @Name("lines") lineRange: Range = Range.INFINITE,
): StringValue {
    // execute the command line and retreive the output
    val lines = execString(command)

    // Check if the range is in bounds.
    val bounds = Range(1, lines.size)
    if (lineRange !in bounds) {
        throw IllegalArgumentException("Invalid range $lineRange in bounds $bounds")
    }

    return lines
        .subList(lineRange)
        .joinToString("\n")
        .wrappedAsValue()
}

/**
 * Loads a CSV file and returns its content as a display-ready table.
 * @param path path of the CSV file (with extension) to show
 * @param caption optional caption of the table. If set, the table will be numbered according to the current [numbering] format
 * @return a table whose content is loaded from the file located in [path]
 * @wiki File data
 */
fun csv(
    @Injected context: Context,
    path: String,
    @LikelyNamed caption: String? = null,
): NodeValue {
    val file = file(context, path)
    val columns = mutableMapOf<String, MutableList<String>>()

    // CSV is read row-by-row, while the Table is built by columns.
    csvReader().open(file) {
        readAllWithHeaderAsSequence()
            .flatMap { it.entries }
            .forEach { (header, content) ->
                val cells = columns.computeIfAbsent(header) { mutableListOf() }
                cells += content
            }
    }

    val table =
        Table(
            columns.map { (header, cells) ->
                Table.Column(
                    Table.Alignment.NONE,
                    Table.Cell(listOf(Text(header.trim()))),
                    cells.map { cell -> Table.Cell(listOf(Text(cell.trim()))) },
                )
            },
            caption,
        )

    return table.wrappedAsValue()
}

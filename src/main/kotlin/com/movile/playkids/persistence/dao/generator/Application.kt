package com.movile.playkids.persistence.dao.generator

import java.io.File

/**
 * @author Júlio Moreira Blás de Barros (julio.barros@movile.com)
 * @since 5/15/19
 */
fun main(args: Array<String>) {
    if (args.size > 1) {
        runFileJob(args[0], args[1])
    } else {
        println("You must provide an input file and an output file")
    }
}

fun runFileLessJob() { }

fun runFileJob(fileName: String, outFile: String) {
    val file = File(fileName)
    if (!file.exists()) {
        println("File $fileName does not exist!!")
        return
    }

    if (outFile.contains("/")) {
        val outputFolder = File(outFile.substringBeforeLast("/"))
        outputFolder.mkdirs()
    }

    val output = File(outFile)
    output.createNewFile()

    val lines = file.readLines()

    if (lines.none { it.contains("data class") }) {
        println("File $fileName is not a data class.")
        return
    }

    val header = lines
        .takeWhile { !it.startsWith("data class") }
        .joinToString("\n")

    val className = lines
        .dropWhile { !it.startsWith("data class") }
        .first()
        .removePrefix("data class ")
        .substringBefore("(")

    val properties = lines
        .dropWhile { !it.startsWith("data class") }
        .drop(1)
        .takeWhile { it.startsWith("    val ") }
        .map { it.removePrefix("    val ") }
        .map { line ->
            Property(
                name = line.substringBefore(":"),
                optional = line.indexOf("?") >= 0,
                default = line.substringAfter("= ").removeSuffix(","),
                type = line.removeSuffix(",")
                           .substringAfter(": ")
                           .substringBefore(" =")
                           .removeSuffix("?")
            )
        }

    val daoClass = """$header
class PostgreSQL${className}DAO(private val db: Connection): ${className}DAO {

    override suspend fun insert(${className.decapitalize()}: $className) {
        db.query(INSERT_QUERY,
${
    properties
        .joinToString("\n") { "            ${className.decapitalize()}.${it.name}," }
        .dropLast(1)
}
        ).await()
    }

    override suspend fun update(${className.decapitalize()}: $className) {
        db.query(UPDATE_QUERY,
${
    properties
        .drop(1)
        .joinToString("\n") { "            ${className.decapitalize()}.${it.name}," }
}
            // WHERE
            ${properties.first().name}
        ).await()
    }

    override suspend fun find(${properties.first().name}: ${properties.first().type}) =
        db.query(FIND_QUERY, ${properties.first().name})
            .awaitMapping { it.to$className() }
            .firstOrNull()

    override suspend fun findBy${properties.getOrNull(1)?.name?.capitalize()}(${properties.getOrNull(1)?.name}: ${properties.getOrNull(1)?.type}) =
        db.query(FIND_BY_${properties.getOrNull(1)?.name?.toSnakeCase()?.toUpperCase()}_QUERY,
            ${properties.getOrNull(1)?.name}
        )
            .awaitMapping { it.to$className() }

    override suspend fun findBy${properties.getOrNull(2)?.name?.capitalize()}(${properties.getOrNull(2)?.name}: ${properties.getOrNull(2)?.type}) =
        db.query(FIND_BY_${properties.getOrNull(2)?.name?.toSnakeCase()?.toUpperCase()}_QUERY,
            ${properties.getOrNull(2)?.name}
        )
            .awaitMapping { it.to$className() }


    private fun RowData.to$className() =
        $className(
${
    properties
        .joinToString("\n") { "            ${it.name} = get${it.type}(${it.name.toSnakeCase().toUpperCase()})${if (it.optional) "" else "!!"}," }
        .dropLast(1)
}
        )


    companion object {
        private const val TABLE_NAME = "${className.toSnakeCase()}"
${properties.joinToString("\n") { "        " + it.toConstVal() }}

        private val PROJECTION = ${"\"\"\""}
${
    properties
        .joinToString("\n") { "        | $" + it.name.toSnakeCase().toUpperCase() + ","}
        .dropLast(1)
}
        ${"\"\"\""}.trimMargin()

        private val INSERT_QUERY = ${"\"\"\""}
        |INSERT INTO ${"$"}TABLE_NAME (${"$"}PROJECTION)
        |VALUES (${"?, ".repeat(properties.size).dropLast(2)})
        ${"\"\"\""}.trimMargin()

        private val UPDATE_QUERY = ${"\"\"\""}
        |UPDATE ${"$"}TABLE_NAME SET
${
    properties.drop(1)
        .joinToString("\n") { "        | $" + it.name.toSnakeCase().toUpperCase() + " = ?," }
        .dropLast(1)
}
        | WHERE ${properties.first().name.toSnakeCase().toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()

        private val FIND_QUERY = ${"\"\"\""}
        |SELECT ${"$"}PROJECTION FROM ${"$"}TABLE_NAME
        | WHERE ${properties.first().name.toSnakeCase().toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()

        private val FIND_BY_${properties.getOrNull(1)?.name?.toSnakeCase()?.toUpperCase()}_QUERY = ${"\"\"\""}
        |SELECT ${"$"}PROJECTION FROM ${"$"}TABLE_NAME
        | WHERE ${properties.getOrNull(1)?.name?.toSnakeCase()?.toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()

        private val FIND_BY_${properties.getOrNull(2)?.name?.toSnakeCase()?.toUpperCase()}_QUERY = ${"\"\"\""}
        |SELECT ${"$"}PROJECTION FROM ${"$"}TABLE_NAME
        | WHERE ${properties.getOrNull(2)?.name?.toSnakeCase()?.toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()
    }
}
    """

    output.writeText(daoClass)

    println("File $outFile successfully generated")
}

private fun Property.toConstVal(): String =
    """private const val ${this.name.toSnakeCase().toUpperCase()} = "${this.name.toSnakeCase()}" """


private fun String.toSnakeCase(): String =
    this.map { if (it.isUpperCase()) "_${it.toLowerCase()}" else "$it" }
        .joinToString("")
        .removePrefix("_")

data class Property(
    val name: String,
    val optional: Boolean,
    val default: String? = null,
    val type: String
)
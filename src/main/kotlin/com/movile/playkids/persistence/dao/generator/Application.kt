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
        runFileLessJob()
    }
}

fun runFileLessJob() { }

fun runFileJob(fileName: String, outFile: String) {
    val file = File(fileName)
    val output = File(outFile).createNewFile()

    val lines = file.readLines()

    val header = lines
        .takeWhile { it.removePrefix("data class") != it }
        .joinToString { "\n" }

    val className = lines
        .dropWhile { it.removePrefix("data class") == it }
        .first()
        .removePrefix("data class ")
        .substringBefore("(")

    val properties = lines
        .dropWhile { it.removePrefix("data class") == it }
        .drop(1)
        .takeWhile { it.removePrefix("val ") != it }
        .map { it.removePrefix("val ") }
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

    val daoClass = """
$header
class ${className}PostgreSQLDAO(private val db: Db): ${className}DAO {

    override suspend fun insert(${className.decapitalize()}: $className) {
        db.query(INSERT_QUERY,
${
    properties
        .map { "            " + it.name + ","}
        .joinToString { "\n" }
        .dropLast(1)
}
        ).await()
    }

    override suspend fun update(${className.decapitalize()}: $className) {
        db.query(UPDATE_QUERY,
${
    properties
        .drop(1)
        .map { "            " + it.name + ","}
        .joinToString { "\n" }
}
            // WHERE
            ${properties.first().name}
        ).await()
    }

    companion object {
        private const val TABLE_NAME = "${className.toSnakeCase()}"
        ${properties.map { it.toConstVal() }.joinToString { "\n" }}

        private val PROJECTION = ${"\"\"\""}
${
    properties
        .map { "        | $" + it.name.toSnakeCase().toUpperCase() + ","}
        .joinToString { "\n" }
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
        .map { "        | $" + it.name.toSnakeCase().toUpperCase() + " = ?," }
        .joinToString { "\n" }
        .dropLast(1)
}
        | WHERE ${properties.first().name.toSnakeCase().toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()

        private val FIND_QUERY = ${"\"\"\""}
        |SELECT ${"$"}PROJECTION FROM ${"$"}TABLE_NAME
        | WHERE ${properties.first().name.toSnakeCase().toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()

        private val FIND_BY_${properties[1].name.toSnakeCase().toUpperCase()}_QUERY = ${"\"\"\""}
        |SELECT ${"$"}PROJECTION FROM ${"$"}TABLE_NAME
        | WHERE ${properties[1].name.toSnakeCase().toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()

        private val FIND_BY_${properties[2].name.toSnakeCase().toUpperCase()}_QUERY = ${"\"\"\""}
        |SELECT ${"$"}PROJECTION FROM ${"$"}TABLE_NAME
        | WHERE ${properties[2].name.toSnakeCase().toUpperCase()} = ?
        ${"\"\"\""}.trimMargin()
    }
}
    """
}

private fun Property.toConstVal(): String =
    """private const val ${this.name.toSnakeCase().toUpperCase()} = "${this.name.toSnakeCase()}" """


private fun String.toSnakeCase(): String =
    this.map { if (it.isUpperCase()) "_${it.toLowerCase()}" else "$it" }
        .joinToString()
        .drop(1)

data class Property(
    val name: String,
    val optional: Boolean,
    val default: String? = null,
    val type: String
)
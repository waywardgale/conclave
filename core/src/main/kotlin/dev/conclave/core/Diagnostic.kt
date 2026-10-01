package dev.conclave.core

data class SourceLocation(
    val file: String,
    val line: Int = 1,
    val column: Int = 1,
    val path: String = "$",
) {
    fun field(name: String) = copy(path = "$path.$name")

    fun index(index: Int) = copy(path = "$path[$index]")
}

data class Diagnostic(val code: String, val message: String, val source: SourceLocation)

sealed interface Validation<out T> {
    data class Valid<T>(val value: T) : Validation<T>

    data class Invalid(val diagnostics: List<Diagnostic>) : Validation<Nothing> {
        init {
            require(diagnostics.isNotEmpty())
        }
    }
}

internal class InvalidInput(val diagnostic: Diagnostic) : RuntimeException(diagnostic.code)

internal fun invalid(code: String, message: String, at: SourceLocation): Nothing =
    throw InvalidInput(Diagnostic(code, message, at))

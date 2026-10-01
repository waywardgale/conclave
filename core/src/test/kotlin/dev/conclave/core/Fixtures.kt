package dev.conclave.core

import kotlin.test.assertIs

internal fun timedManifest(id: String = "test", duration: String = "1s") =
    """
    schema: 1
    encounter:
      id: $id
      start: waiting
      phases:
        - id: waiting
          duration: $duration
          success: {complete: true}
"""
        .trimIndent()

internal fun catalog(id: String = "test", duration: String = "1s"): CompiledCatalog =
    assertIs<Validation.Valid<CompiledCatalog>>(
            CatalogCompiler()
                .compile(listOf(SourceDocument("test.yaml", timedManifest(id, duration))))
        )
        .value

internal fun diagnostic(result: Validation<*>): Diagnostic =
    assertIs<Validation.Invalid>(result).diagnostics.first()

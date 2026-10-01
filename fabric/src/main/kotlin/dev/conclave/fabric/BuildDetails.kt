package dev.conclave.fabric

import dev.conclave.core.BuildIdentity
import java.util.Properties

internal object BuildDetails {
    val identity: BuildIdentity by lazy {
        val properties = Properties()
        checkNotNull(BuildDetails::class.java.getResourceAsStream("/conclave-build.properties")) {
                "Conclave build identity is missing; rebuild the distribution"
            }
            .use(properties::load)
        BuildIdentity(properties.getProperty("version"), properties.getProperty("fingerprint"))
    }

    fun describe(identity: BuildIdentity = this.identity): String =
        "${identity.version} (${identity.fingerprint.take(12)})"
}

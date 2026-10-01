# Conclave

Conclave is a Kotlin framework for Minecraft Fabric 26.2. Its design lets authors create encounters with readable YAML, reusable mechanics, sequential phases, and in-game editing and publishing. Encounters are authored separately from the framework.

Implementation is in progress. **This is a development build, not the complete v1 release.** Minecraft can run published arena pairings with timed phases, capture objectives, block interaction objectives and their compositions through `/conclave start <arena> <encounter>`. Admission captures participants and a content revision, reserves durable ownership and completion capacity, and waits for entity-ticking chunks. Other native mechanic adapters remain in development. `/conclave` opens the authoring editor; saved drafts can be validated and published for future attempts. GM commands include stop and restart with current-attempt completion suggestions. Native execution requires an encounter `recovery.location` bound to an arena location. Recovery uses captured destinations, health and hunger; `/conclave recovery` shows pending obligations.

Reusable `mechanic` definitions support typed `parameters` and `use`/`with` bindings. Each occurrence retains its own progress. Scalar, spatial, player-selection and matcher configuration parameters are implemented; `sequence`, `parallel`, `repeat` and `layers` provide private child state and typed public events through `export.events`.

Block `interact` targets use arena location bindings and Minecraft's configured Use control. Instant uses, individual holds, cooldowns, distinct contributors and optional native-use consumption are connected. The server checks aim, reach, obstruction and block replacement; the HUD shows the holder's own progress. NPC interaction targets remain unavailable.

Native revival uses the captured global policy, with a constrained grave camera during the revival opportunity. After expiry, players use the configured teammate or free view. Living returnees whose reconnect grace expired can choose Watch and Leave while retaining observer status. Viewing controls default to V and can be rebound in Minecraft. GMs can revive an online roster member with `/conclave revive <attempt> <player>`; this uses the current death's captured policy and preserves participation.

Read [implementation status](docs/implementation-status.md) for exact support and limitations. The [confirmed design](docs/design-interview.md#shared-understanding-confirmation), [delivery stages](docs/framework-delivery-and-extensions.md), and [domain vocabulary](CONTEXT.md) describe the full target. Design documents can describe capabilities that have not been implemented.

## Developer build

Use Java 25. The Gradle wrapper downloads the pinned Gradle 9.7.0 distribution and verifies its checksum.

```sh
./gradlew build
```

The mod artifact is `fabric/build/libs/conclave-0.1.0-dev.1.jar`. It includes core, storage, SnakeYAML Engine, SQLite JDBC, and the Z3 geometry solver. Install matching copies on client and server together with Fabric API and Fabric Language Kotlin.

| Dependency | Build version |
| --- | --- |
| Minecraft Java | 26.2 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.2 |
| Fabric Language Kotlin | 1.14.1+kotlin.2.4.20 |
| Kotlin | 2.4.20 |
| Fabric Loom | 1.18.2 |
| SnakeYAML Engine | 3.1.1 |
| SQLite JDBC | 3.53.4.0 |
| Z3 TurnKey | 4.14.1 |

`./gradlew :core:test` checks core behavior. `./gradlew build` also tests storage, compiles both Fabric environments, tests native buffers and metadata, and packages the distribution. `./gradlew :fabric:runGameTest` runs native contracts in a disposable Minecraft world. `./gradlew :fabric:runClientGameTest` verifies the editor through a real client and integrated server, and captures screenshots. Test worlds are disposable build outputs. These are framework development commands; authoring and administration operate inside Minecraft.

## Code layout

- `core`: typed YAML compilation, base mechanics, conditions, rules, scoped state, exact geometry, arena reservations, attempt coordination, revisions and compatibility messages.
- `storage`: transactional content, GM/audit state, attempt/resource recovery, named collaborative drafts, completion records and reward accounting.
- `fabric`: common/client entrypoints, native login and authority adapters, world sessions, chunk claims, authoring transport/editor and native game tests.
- `docs`: accepted behavior, research, design decisions, and current implementation status.

The development interfaces are experimental. The stable Kotlin extension API remains to be implemented. Test manifests are internal fixtures; the distribution contains no playable encounter bundle.

Licensed under [MPL-2.0](LICENSE).

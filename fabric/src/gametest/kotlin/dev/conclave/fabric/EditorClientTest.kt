package dev.conclave.fabric

import dev.conclave.fabric.client.AuthoringClient
import dev.conclave.fabric.client.AuthoringScreen
import dev.conclave.fabric.client.YamlEditor
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import org.lwjgl.glfw.GLFW

class EditorClientTest : FabricClientGameTest {
    override fun runTest(context: ClientGameTestContext) {
        context.worldBuilder().create().use { world ->
            world.connection.waitForChunksRender()
            val player =
                world.server.computeOnServer<UUID, RuntimeException> {
                    world.connection.serverPlayer.uuid
                }
            world.server.runOnServer<RuntimeException> { server ->
                server.playerList.op(
                    net.minecraft.server.players.NameAndId(
                        server.playerList.getPlayer(player)!!.gameProfile
                    )
                )
            }
            world.server.runOnServer<RuntimeException> { server ->
                checkNotNull(ServerSession.get(server))
                    .authoring
                    .open(checkNotNull(server.playerList.getPlayer(player)))
            }
            context.waitForScreen(AuthoringScreen::class.java)
            context.runOnClient<RuntimeException> { client ->
                val screen = client.gui.screen() as AuthoringScreen
                screen.children().filterIsInstance<EditBox>().single().value = "Editor validation"
            }
            context.clickScreenButton("Create")
            context.waitFor { client ->
                !AuthoringClient.busy &&
                    (client.gui.screen() as? AuthoringScreen)
                        ?.children()
                        ?.filterIsInstance<EditBox>()
                        ?.size == 2
            }
            context.runOnClient<RuntimeException> { client ->
                (client.gui.screen() as AuthoringScreen)
                    .children()
                    .filterIsInstance<EditBox>()
                    .first()
                    .value = "encounters/ritual.yaml"
            }
            context.clickScreenButton("New file")
            val source =
                """
                # Client-authored validation fixture, never shipped as encounter content.
                schema: 1
                encounter:
                  id: editor_fixture
                  start: first
                  phases:
                    - id: first
                      duration: 1s
                      success: {complete: true}
                """
                    .trimIndent()
            context.runOnClient<RuntimeException> { client ->
                val editor =
                    (client.gui.screen() as AuthoringScreen)
                        .children()
                        .filterIsInstance<YamlEditor>()
                        .single()
                editor.replace(source)
                check(editor.find("editor_fixture"))
                editor.goTo(1)
            }
            context.takeScreenshot("conclave-yaml-editor")
            context.clickScreenButton("Save")
            context.waitFor { !AuthoringClient.busy }
            context.clickScreenButton("Validate")
            context.waitFor { !AuthoringClient.busy }
            context.clickScreenButton("Publish")
            context.takeScreenshot("conclave-publication-review")
            context.clickScreenButton("Publish this version")
            context.waitFor { !AuthoringClient.busy }
            world.server.runOnServer<RuntimeException> { server ->
                check(
                    checkNotNull(ServerSession.get(server)).catalog?.encounters?.keys?.any {
                        it.name == "editor_fixture"
                    } == true
                ) {
                    "Editor publication did not install its validated catalog"
                }
            }
            context.runOnClient<RuntimeException> { client ->
                val editor =
                    (client.gui.screen() as AuthoringScreen)
                        .children()
                        .filterIsInstance<YamlEditor>()
                        .single()
                check(editor.value == source) { "Saving rewrote raw YAML" }
                editor.replace(source + "\n# Unsaved note")
                val modifier =
                    if (System.getProperty("os.name").startsWith("Mac")) GLFW.GLFW_MOD_SUPER
                    else GLFW.GLFW_MOD_CONTROL
                editor.isFocused = true
                check(editor.keyPressed(KeyEvent(GLFW.GLFW_KEY_Z, 0, modifier)))
                check(editor.value == source) { "Editor undo did not restore the original source" }
            }
            // Closing and immediately reopening uses the ordered disk queue, without a sleep.
            val recoveredSource = source + "\n# Recover this unsaved note"
            context.runOnClient<RuntimeException> { client ->
                (client.gui.screen() as AuthoringScreen)
                    .children()
                    .filterIsInstance<YamlEditor>()
                    .single()
                    .replace(recoveredSource)
            }
            context.clickScreenButton("Close")
            world.server.runOnServer<RuntimeException> { server ->
                checkNotNull(ServerSession.get(server))
                    .authoring
                    .open(checkNotNull(server.playerList.getPlayer(player)))
            }
            context.waitForScreen(AuthoringScreen::class.java)
            context.clickScreenButton("Editor validation")
            context.waitFor { client ->
                (client.gui.screen() as? AuthoringScreen)
                    ?.children()
                    ?.filterIsInstance<YamlEditor>()
                    ?.singleOrNull()
                    ?.value == recoveredSource
            }
            val exportDirectory =
                context.computeOnClient<java.nio.file.Path, RuntimeException> { client ->
                    client.gameDirectory.toPath().resolve("conclave/authoring")
                }
            val importPath = exportDirectory.resolve("imported.yaml")
            Files.createDirectories(exportDirectory)
            Files.writeString(importPath, "# imported Unicode\nname: Привет\n")
            val existing = Files.list(exportDirectory).use { it.toList().toSet() }
            context.clickScreenButton("Export")
            // A following worker read settles only after the export that the UI queued.
            LocalAuthorFiles.shared.importFiles(listOf(importPath)).get(5, TimeUnit.SECONDS)
            val exported =
                Files.list(exportDirectory).use {
                    it.toList().filterNot(existing::contains).single()
                }
            check(Files.readString(exported) == recoveredSource) {
                "Export lost the raw unsaved buffer"
            }
            context.runOnClient<RuntimeException> { client ->
                (client.gui.screen() as AuthoringScreen).onFilesDrop(listOf(importPath))
            }
            context.waitFor { client -> client.gui.screen()?.title?.string == "Import YAML?" }
            context.clickScreenButton("Confirm")
            context.waitFor { client ->
                (client.gui.screen() as? AuthoringScreen)
                    ?.children()
                    ?.filterIsInstance<YamlEditor>()
                    ?.singleOrNull()
                    ?.value == "# imported Unicode\nname: Привет\n"
            }
            context.takeScreenshot("conclave-editor-recovered-import")
            world.server.runOnServer<RuntimeException> { server ->
                server.playerList.deop(
                    net.minecraft.server.players.NameAndId(
                        server.playerList.getPlayer(player)!!.gameProfile
                    )
                )
            }
            context.runOnClient<RuntimeException> {
                check(AuthoringClient.request(AuthorRequest.ListDrafts))
            }
            context.waitFor { !AuthoringClient.busy }
            context.runOnClient<RuntimeException> { client ->
                check(
                    (client.gui.screen() as AuthoringScreen)
                        .children()
                        .filterIsInstance<net.minecraft.client.gui.components.Button>()
                        .first { it.message.string == "Publish" }
                        .active
                        .not()
                ) {
                    "Revocation must disable publication in the editor"
                }
            }
            context.takeScreenshot("conclave-editor-revoked")
        }
    }
}

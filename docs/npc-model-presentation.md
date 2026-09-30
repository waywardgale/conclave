# NPC equipment visuals and model fallback

Status: Q162-Q163 are accepted. Custom NPC model definitions, native equipment slots, native gameplay, independent physical size, asset publication, and client readiness are accepted. These contracts specify how an installed NPC presentation adapter should handle equipment and missing resources; no adapter has been implemented or verified in Conclave.

## Q162: explicit equipment attachments on custom NPC models

Accepted: add optional `attachments` to a `model` definition. Map supported native equipment slots to named bones in the imported geometry. Start with `mainhand` and `offhand` item attachments. The binding reads the NPC's current real equipment; it does not spawn a second item, create inventory, or copy weapon properties into visual data.

```yaml
attachments:
  mainhand:
    bone: right_hand
  offhand:
    bone: left_hand
```

This fragment belongs to the model definition. Equipment is still authored on the NPC with Q144's `equipment` map. An omitted attachment draws no separate item for that slot on the custom model. The model geometry may already contain a weapon or armor appearance. Keeping equipment visually absent does not remove its native gameplay effects, and the preview must show that distinction clearly.

Use the named bone's animated transform and the adapter's documented item display context. Validate that the bone exists and that the selected holder and adapter support the binding. Do not guess a right-hand bone from its position or silently turn a left-handed NPC's `mainhand` into `offhand`. These are explicit model attachments; authors choose the bones for the intended appearance. Show the current equipped item and animation in the in-game preview.

Keep the first binding shape small. Authors can position attachment bones in the imported model. Additional typed offsets or visual controls require documented adapter fields rather than an unrestricted transform expression. A hand attachment is visual only and does not alter attack reach, projectile origin, collision, or damage timing.

Native armor, saddles, emissive eyes, and other type-specific rendering layers are not automatically preserved when replacing an entity renderer. An adapter can expose a verified layer profile, but an unsupported requested layer must fail validation. Initial generic hand attachments do not imply that ordinary armor meshes fit arbitrary custom geometry. All native equipment slots remain available for supported gameplay under Q144 regardless of whether this custom renderer draws their contents.

Preserve ordinary supported nameplate visibility, invisibility, and other common entity presentation behavior. Do not make a named NPC visible through walls or reveal an invisible entity merely because it uses a custom model. Type-specific layers and renderer interoperability need explicit verification. The original entity type continues to own its server AI, equipment, health, and collision. A visual adapter must not replace the server entity to obtain the appearance.

Keep the model, attachment bindings, and their supported dependencies on the consumer's captured revision. Ordinary world NPCs of the same base type retain their native renderer. The client chooses the appropriate presentation for each entity; installing Conclave must not turn every zombie into a particular authored boss. [Presentation asset research](presentation-assets-research.md) distinguishes the library's available facilities from Conclave's remaining integration work.

## Q163: visible native fallback when a custom model is unavailable

Accepted: render the NPC's ordinary base-type appearance for a viewer who cannot display its captured custom model. Use the existing entity's current state and native renderer rather than making it invisible or borrowing a different model revision. This is a specific, documented fallback to that NPC's native presentation.

The common case is an outside viewer whose active attempt still pins an older asset set. Preserve that viewer's active resource set and use native presentation for the other NPC until its correct captured resources become available through the accepted publication workflow. Do not force a resource reload during an attempt, fetch unapproved assets, or use the newest model under the same logical ID as a substitute.

This fallback does not satisfy asset readiness for a new attempt. Selected participants must still have that attempt's required applied resources before it can start. Invalid authored geometry, missing required bones, and unsupported adapter features are publication errors, not a reason to accept broken content because a native renderer exists.

For an unexpected cosmetic rendering failure, retain the native view and report a bounded diagnostic identifying the affected model, revision, and consumer to authorized administrators. A required presentation failure still follows its existing error contract. The fallback never marks failed resources as successfully applied or promises that a required clue or animation was delivered. It preserves ordinary world visibility while those outcomes are handled.

Use native nameplate and invisibility rules in the fallback. Keep the same NPC identity, actual equipment, physical size, hitbox, position, server AI, health, and group membership. An appearance-only `scale` multiplier belongs to the unavailable custom appearance; the native view follows native physical scaling. The fallback does not spawn a duplicate, respawn the NPC, or reset gameplay. It may look different from the intended custom model, which the editor and diagnostics should state plainly.

When the correct resources can legally become active, present the NPC's current supported model state. Do not replay old animation cues or restart server actions. Retained revisions and audience/privacy rules remain in force. This policy is an explicit exception to the general prohibition on silently substituting presentation assets; it does not authorize fallback sounds, dialogue changes, or arbitrary model substitution.

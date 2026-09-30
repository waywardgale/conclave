# Native item-model persistence and stacking

Checked 2026-09-26 for Minecraft Java 26.2 by static inspection of the SHA-1-verified [official client artifact](https://piston-data.mojang.com/v1/objects/2dc72797acbc1b63fc16a11c4ac393605f453754/client.jar), digest `2dc72797acbc1b63fc16a11c4ac393605f453754`. These findings support the accepted Q232-Q233 contracts. No Conclave archive, inventory adapter, or live restart test exists.

## Saved and copied item data

`DataComponents.ITEM_MODEL` registers an `Identifier` with persistent serialization and network synchronization. `ItemStack.CODEC` stores the item, count, and `DataComponentPatch`. A nondefault model override therefore survives ordinary codec-based saving and loading. Native consumers include `ItemEntity.addAdditionalSaveData` / `readAdditionalSaveData` and `ContainerHelper.saveAllItems` / `loadAllItems`. Unmodified item defaults come from the registered prototype rather than necessarily appearing as explicit saved overrides.

`ItemStack.copy` copies the patched component map. `copyWithCount` uses that copy; `split` creates the resulting stack through `copyWithCount` and shrinks the original. A model override is retained by these operations on the resulting nonempty stacks. This is not a statement that every mod or every native transformation must copy all components.

## Stack equality and ordinary transformations

`ItemStack.isSameItemSameComponents` compares the item and its complete effective component map. Different effective model IDs fail this comparison, even if their resources draw the same picture. The same item, model ID, and other components pass this comparison. Count is separate from component equality.

Actual merging still applies ordinary capacity and stackability checks. Inspected consumers include `Inventory.hasRemainingSpaceForItem`, `AbstractContainerMenu.moveItemStackTo`, and `ItemEntity.areMergable`. Passing component equality does not make normally non-stackable swords merge or bypass an inventory's capacity.

Copying and splitting are different from constructing a recipe result. For example, `ShapedRecipe.assemble` builds its configured result `ItemStackTemplate`; it does not copy arbitrary ingredient components. Other transformations require their own verified behavior. Conclave must not infer universal model inheritance through crafting from the ordinary stack-copy path.

## Asset identifiers are external references

`ItemModelResolver.appendItemLayers` reads the model identifier and resolves the corresponding client model. The component contains no model JSON, textures, captured archive, or automatically generated content hash. Mojang's [item-model documentation](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-4) describes how an identifier selects the client item definition under `assets/<namespace>/items/`.

Retaining an item identifier while replacing or deleting the corresponding resource does not preserve the old appearance. That is an inference from the separate native item-data and resource-resolution paths. Conclave's immutable identifiers, asset archive, dependency capture, and base-model fallback remain additional integration requirements, not services provided automatically by the native component.

## Limits of live reference scans

`PlayerList.getPlayers` returns the server's current player list. `PlayerDataStorage` separately reads and writes saved player data by UUID. `ServerLevel.getAllEntities` queries its runtime entity getter; `SerializableChunkData.write` separately serializes block entities. A live entity/online-player view is therefore not an inventory of every saved item.

Nested items also carry components. The inspected `BundleContents` and `ItemContainerContents` store `ItemStackTemplate` values whose codec includes component patches. Looking only at top-level inventory slots misses nested model references.

The resulting inference is narrow: scanning loaded entities and online inventories cannot certify the absence of references in offline player data, unloaded containers, or nested contents. These findings establish no universal inventory census, no atomic snapshot across saves, and no safe automatic deletion policy for archived art. Q232 accepts conservative retention instead of attempting that census.

## Integration boundaries

Supported native copy, save, load, and comparison paths are established by source inspection. Cross-mod inventory storage, arbitrary component edits, imported worlds, backups restored at different times, resource reload failures, and migration between Minecraft versions need their own compatibility and recovery handling. Neither an appearance reference nor an archive entry proves item ownership, a unique item identity, or a previous reward-delivery receipt.

---
status: accepted
---

# Separate encounter definitions, arenas, and attempts

An encounter definition is reusable across arenas, each arena permits one active attempt at a time, and different arenas can host concurrent attempts of the same encounter. YAML names an arena and locates its boundaries in an existing Minecraft build; the arena owns its locations, Location anchors, and areas, which the encounter references by logical IDs. Arena copying is deferred to v2, so reuse in the initial framework does not require the framework to create a world copy for every group.

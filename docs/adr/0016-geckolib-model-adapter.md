---
status: accepted
---

# Use a GeckoLib adapter for authored animated models

Conclave will support custom animated NPC appearances through an adapter using GeckoLib's model and animation formats, after research verified a Fabric 26.2 release and MIT licensing. This reuses an existing rendering and animation library while allowing authors to publish supported appearances as resource-pack data without a renderer or entity registration for every boss. Server gameplay remains separate, and the exact dependency pin and safe reload/version-retention behavior still require an implementation proof.

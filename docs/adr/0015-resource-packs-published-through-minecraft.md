---
status: accepted
---

# Publish authored resource packs through the Minecraft connection

Conclave uses resource packs for authored assets and transfers them through the existing Minecraft connection, with the required mod providing the authoring interface, staging, and activation checks. This adds custom delivery work instead of relying on an external pack URL so authors can publish entirely inside Minecraft without another upload service or server port. Versioned resources remain available to their existing consumers, and pack activation waits until the receiving player's active attempt ends.

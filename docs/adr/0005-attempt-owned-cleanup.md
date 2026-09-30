---
status: accepted
---

# Clean up changes owned by an attempt

Conclave tracks attempt-owned entities, timers, temporary effects, and block changes so normal completion or a wipe can remove temporary state and restore its block edits. Phase-started mechanics and their temporary state end with the phase by default, while explicit encounter scope permits persistence across phases; cleanup does not count as natural aura expiry. Full snapshot restoration remains outside this cleanup contract; [Q120](../cleanup-and-restart.md#q120-interrupted-attempts-after-a-server-restart) accepts cleanup and recovery of interrupted attempts instead of mid-attempt resume. [ADR-0013](0013-encounter-state-preserved-minecraft-rules.md) supersedes the earlier automatic arena building/mining protection; ordinary player changes remain outside attempt ownership, and [Q119](../cleanup-and-restart.md#q119-restoring-blocks-after-ordinary-world-changes) preserves subsequent player or unrelated-system edits during restoration.

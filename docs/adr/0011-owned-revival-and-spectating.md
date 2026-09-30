---
status: accepted
---

# Own revival and spectating in Conclave

Conclave will implement its own revival and spectating modules because the investigated Fabric 26.2 revival mods do not expose supported controls for the complete grave, expiry, respawn, camera, and per-attempt policy requirements. This takes on lifecycle and client-camera maintenance in exchange for direct control and avoids relying on another mod's private implementation. Revival owns eligibility and recovery, while spectating owns viewing behavior and information access; both follow the attempt's starting gameplay policy.

See the [dependency investigation](../revival-dependency-research.md) for the considered alternatives. No external revival or spectating mod is selected as a dependency.

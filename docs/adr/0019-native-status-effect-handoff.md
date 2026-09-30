# Native status-effect handoff

Minecraft merges effects of one type without retaining removable source ownership, so automatic cleanup could erase an unrelated potion or restore obsolete state. Conclave hands explicit native status-effect applications to Minecraft for merging and lifetime management, while its aura modifiers retain owned cleanup. This permits a native effect to outlive its originating phase or attempt, and explicit removal clears the whole named native effect.

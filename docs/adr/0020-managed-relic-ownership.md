# Managed relic ownership

Relics need one authoritative holder and encounter-owned return and cleanup behavior, while ordinary inventory items participate in slot operations and item-transfer systems. Conclave tracks relic ownership separately from inventory, preserving normal slots and applying its explicit drop, delivery, and respawn policies to managed world objects. This gives up automatic participation in crafting and container transfer to keep relic lifetimes independent of ordinary possessions and vanilla death drops.

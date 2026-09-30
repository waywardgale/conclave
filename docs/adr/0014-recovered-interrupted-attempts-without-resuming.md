---
status: accepted
---

# Recover interrupted attempts without resuming them

After a server restart or crash, Conclave v1 ends interrupted attempts, reconciles owned resources, and completes pending player recovery using retained definitions and ownership records. It does not resume mechanics mid-phase or replay rewards and death consequences, because doing so would require reliable persistence and restoration of every mechanic, AI state, and external side effect. Player-owned auras and outside-attempt graves retain their separately defined lifetimes.

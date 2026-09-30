---
status: accepted
---

# Run gameplay timers on the server simulation clock

Capture progress, phase timers, and aura durations use a shared server-owned simulation clock. If simulation progress stops, these timers stop too, preserving the gameplay time participants have to respond instead of consuming it during a server stall. Client displays follow server time; publishing timeouts and disconnect grace remain separate operational policies.

---
status: accepted
---

# Require the client mod and keep gameplay decisions on the server

Conclave requires the same supported core release on the server and every connecting client, extending the original participating-client requirement under [Q268](../code-compatibility.md#q268-require-matching-core-code-before-world-entry) because global revival and grave cameras also operate outside encounters. The server owns gameplay decisions and validates compatible code before world entry; client presentation does not grant gameplay authority. This accepts a server-wide installation requirement, while asset refusal or application failure retains the separate ordinary-play and encounter-readiness policies.

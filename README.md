# jit

A git implementation in Java, with a gRPC backend for `jit push`.

- `client/` — the `jit` CLI (`git.Main`)
- `server/` — gRPC push server (`jit.JitServer`) backed by Postgres

Both share the same `git.proto` (copied into each `src/main/proto/`).

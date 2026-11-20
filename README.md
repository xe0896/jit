# jit

A git implementation in Java, with a gRPC backend for `jit push`.

## Layout

| Module    | Artifact       | What                                              |
|-----------|----------------|---------------------------------------------------|
| `proto/`  | `git-proto`    | `git.proto` + generated gRPC stubs (shared)       |
| `client/` | `git-client`   | the `jit` CLI (`git.Main`)                        |
| `server/` | `git-platform` | gRPC push server (`git.GitServer`), Postgres      |

## Build

```sh
mvn package                         # everything
mvn -pl client -am package          # client + proto only
mvn -pl server -am package          # server + proto only
```

## Run the server

```sh
cp server/.env.example server/.env  # fill in DB credentials (gitignored)
server/run.sh
```

## Workflow (local + VM)

GitHub is the source of truth; both machines are clones.

- Pull before you start, push when you're done, on whichever machine you're on.
- Deploy: on the VM, `git pull && server/run.sh`.
- Never copy source with scp; never commit `server/.env`.

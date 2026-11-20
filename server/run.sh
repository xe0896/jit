#!/usr/bin/env bash
# Build proto + server and start the gRPC server. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f server/.env ]]; then
  set -a; source server/.env; set +a
fi
mvn -q -pl server -am install -DskipTests
mvn -q -pl server exec:java

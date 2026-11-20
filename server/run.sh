#!/usr/bin/env bash
# Build and start the gRPC server. Run from anywhere.
set -euo pipefail
cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a; source .env; set +a
fi
mvn -q package -DskipTests
mvn -q exec:java

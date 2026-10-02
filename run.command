#!/bin/sh
set -eu
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "JDK 25 or newer is required. Install a Java JDK, then run this file again."
  read -r -p "Press Return to close... " _ || true
  exit 1
fi
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/storage-cleanup.XXXXXX")
trap 'rm -rf "$build_dir"' EXIT HUP INT TERM
find src/main/java -name '*.java' -print0 | xargs -0 javac --release 25 -d "$build_dir"
java -cp "$build_dir" dev.storagecleanup.StorageCleanup

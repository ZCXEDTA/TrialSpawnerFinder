#!/usr/bin/env bash
# TrialSpawnerFinder CLI (CUDA-accelerated) launcher.
# Usage: ./run-cuda.sh --seed 188188 --search-radius 10000 ...
set -euo pipefail
cd "$(dirname "$0")"

if [ -f ".runtime/build-java-home.txt" ]; then
    export JAVA_HOME="$(cat .runtime/build-java-home.txt)"
fi

./gradlew run --args="$*"

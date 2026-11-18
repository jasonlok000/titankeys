#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
bun test tests/support-worker/worker.test.js

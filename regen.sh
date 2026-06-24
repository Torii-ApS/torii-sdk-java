#!/usr/bin/env bash
# Regenerate the generated REST client under src/main/kotlin/so/torii/backend/generated/
# from spec/server-v1.json. Delegates to the org.openapi.generator Gradle plugin
# task, which generates into build/openapi-staging/ and syncs only the Kotlin
# sources. Idempotent; safe to re-run after a spec bump.
set -euo pipefail
cd "$(dirname "$0")"

./gradlew regenerateOpenApi

echo "✓ regenerated so/torii/backend/generated/ from spec/server-v1.json"

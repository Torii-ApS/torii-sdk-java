#!/usr/bin/env bash
# Set the published package version. Called by the torii release train (and
# `just sdk-release`) right before tagging. release.yml asserts the tag against
# Gradle's resolved `version` property; build.gradle.kts sets it (and shadows
# gradle.properties), so bump BOTH to keep them consistent.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${1:?usage: ./bump.sh <version>  (e.g. 0.0.5)}"
VERSION="${VERSION#v}"
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.]+)?$ ]]; then
	echo "✗ invalid version: '$VERSION'" >&2
	exit 1
fi

perl -i -pe 's/^version=.*/version='"$VERSION"'/' gradle.properties
perl -i -pe 'if (!$done && s/^(version\s*=\s*")[^"]*(")/${1}'"$VERSION"'${2}/) { $done = 1 }' build.gradle.kts
grep -q "^version=$VERSION\$" gradle.properties     || { echo "✗ gradle.properties not bumped to $VERSION" >&2; exit 1; }
grep -q "^version = \"$VERSION\"" build.gradle.kts   || { echo "✗ build.gradle.kts not bumped to $VERSION" >&2; exit 1; }
echo "✓ torii-sdk-java -> $VERSION (gradle.properties + build.gradle.kts)"

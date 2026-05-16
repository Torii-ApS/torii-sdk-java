# Contributing

Thanks for your interest in `torii-sdk-java`!

## Reporting bugs

Open an issue with:

- The version of `so.torii:torii-backend` you're using.
- A minimal reproduction — a few lines that exhibit the bug.
- What you expected to happen vs. what actually happened.

For security-sensitive issues (anything that could let an attacker forge or bypass token verification), please email **security@torii.so** instead of filing a public issue.

## Development

```sh
git clone https://github.com/Torii-ApS/torii-sdk-java
cd torii-sdk-java
./gradlew build
```

The REST client lives under `src/main/kotlin/so/torii/backend/generated/` and is produced by the [`org.openapi.generator`](https://openapi-generator.tech/) Gradle plugin from `spec/server-v1.json`. It **is** checked in (matching the layout of the other torii SDKs; the client is browsable on GitHub). Don't hand-edit it. Regenerate after a spec bump by running:

```sh
./gradlew regenerateOpenApi
```

That task generates into `build/openapi-staging/` and syncs only the Kotlin sources into `src/main/kotlin/so/torii/backend/generated/`. Commit the result.

The hand-written surface lives under `src/main/kotlin/so/torii/backend/` (auth helpers, REST client wrapper, error types). That's where bug reports and PRs typically land.

## Pull requests

1. Open an issue first for non-trivial changes so we can discuss the shape.
2. Branch off `main`, name it `fix/<short>` or `feat/<short>`.
3. Run `./gradlew build test` before pushing — CI checks the same.
4. Keep PRs small and focused. One concern per PR.
5. Update `README.md` if you change the public surface.

## Releases

Tagged off `main`. Bump the version in `build.gradle.kts` and any references in `README.md`, then:

```sh
git tag v0.0.2
git push origin v0.0.2
```

Consumers pick up the new version from Maven Central as `so.torii:torii-backend:0.0.2`.

## Code of Conduct

Be kind. Disagreements happen; argue the position, not the person.

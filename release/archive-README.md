# jarproof

Find JAR hell before production. jarproof verifies that a set of Java
artifacts will actually work together at runtime: missing classes and methods,
classpath conflicts, bytecode-level mismatches, `ServiceLoader` wiring, and
module descriptors — with the exact runtime error each break would throw.

This archive contains the standalone native binary for one platform. It needs
no JVM, no build tool, and no network.

## Run it

```sh
./jarproof check --application app.jar --classpath "lib/*" --target-java 17
./jarproof inspect app.jar
./jarproof explain JP1003
./jarproof explain          # lists every diagnostic code
```

Exit codes: `0` clean, `1` findings at or above `--fail-on` (default `error`),
`2` invocation error.

**macOS note:** if you downloaded this archive through a browser and extracted
it in Finder, clear the quarantine mark once before running:
`xattr -d com.apple.quarantine ./jarproof`. Downloading with `curl` and
extracting with `tar` needs nothing.

## Verify the download

Check the archive against `SHA256SUMS` from the same GitHub release:

```sh
shasum -a 256 -c SHA256SUMS --ignore-missing
```

## Licensing

jarproof is Apache-2.0 (`LICENSE`, `NOTICE`). This binary redistributes
third-party components; their required notices are in
`THIRD_PARTY_NOTICES.md`.

## Everything else

Documentation, source, issues, and newer releases:
https://github.com/zoltsh/jarproof

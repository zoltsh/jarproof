<h1 align="center">jarproof</h1>

<p align="center">
  <strong>Find JAR hell before production.</strong>
</p>

<p align="center">
  Verify the Java artifacts you will actually ship before the JVM finds their
  incompatibilities at runtime.
</p>

## Use

```sh
jarproof check --application app.jar --classpath "lib/*" --target-java 17
jarproof inspect app.jar
jarproof explain JP1003
```

`check` reports the affected symbol and artifacts, the JVM error the failure
would cause, and exits nonzero when findings meet the configured threshold.
That makes it suitable for local release checks and CI.

## What it finds

- Missing or incompatible classes, methods, and fields
- Access changes and class-to-interface or static-to-instance mismatches
- Duplicate classes, split packages, and classpath conflicts
- Unsupported bytecode, broken `ServiceLoader` providers, and module issues

For example, an application can compile against version 2 of a library but ship
version 1. Jarproof finds the missing version 2 method before that code path
throws `NoSuchMethodError` in production.

## How it works

Jarproof reads JAR files, class directories, and bytecode without loading or
executing analyzed classes. Analysis is deterministic and does not use
reflection or network access.

## Development

Jarproof is built with [Zolt](https://github.com/zoltsh/zolt).

```sh
scripts/check
```

The check resolves the locked workspace, runs unit and integration tests,
checks coverage and architecture rules, and packages every member.

## License

Apache-2.0. See [LICENSE](./LICENSE).

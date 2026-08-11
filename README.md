<h1 align="center">jarproof</h1>

<p align="center">
  <strong>Find JAR hell before production.</strong>
</p>

<p align="center">
  Verify the Java artifacts you will actually ship before the JVM finds their
  incompatibilities at runtime.
</p>

<p align="center">
  <a href="https://github.com/zoltsh/jarproof/actions/workflows/ci.yml">
    <img src="https://github.com/zoltsh/jarproof/actions/workflows/ci.yml/badge.svg" alt="CI">
  </a>
</p>

<p align="center">
  <a href="#use">Use</a>
  <span> · </span>
  <a href="#output">Output</a>
  <span> · </span>
  <a href="#finds">Finds</a>
  <span> · </span>
  <a href="#model">Model</a>
  <span> · </span>
  <a href="#development">Development</a>
</p>

<br />

## Use

```sh
jarproof check --application app.jar --classpath "lib/*" --target-java 17
```

| Command | Purpose |
| :--- | :--- |
| `check` | Verify an application against its runtime classpath |
| `baseline` | Accept existing findings and fail only on new ones |
| `inspect` | Show the layout and bytecode facts in one artifact |
| `explain` | Explain a diagnostic such as `JP1003` |

## Output

```console
$ jarproof check --application app.jar --classpath "lib/*" --target-java 17
error JP1003: referenced method is not declared anywhere above the resolved class
  symbol:      com.example.OrderPolicy.describe(String, int)
  at runtime:  NoSuchMethodError

next: Restore the method with the descriptor the call site compiled against, or align the versions.

1 error, 1 finding
```

Jarproof names the affected symbol, the artifacts involved, the JVM error the
failure would cause, and the next step. It exits nonzero when findings meet the
configured threshold, so the same command works locally and in CI.

## Finds

<table>
  <tr>
    <td width="50%" valign="top">
      <strong>Linkage</strong><br>
      Missing or incompatible classes, methods, and fields.
    </td>
    <td width="50%" valign="top">
      <strong>Classpath</strong><br>
      Duplicate classes, split packages, and ambiguous wildcard order.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <strong>Runtime</strong><br>
      Unsupported bytecode, access changes, and class-shape mismatches.
    </td>
    <td width="50%" valign="top">
      <strong>Services and modules</strong><br>
      Broken providers, module conflicts, and invalid declarations.
    </td>
  </tr>
</table>

## Model

```txt
application + runtime classpath + target Java
                     ↓
                  jarproof
                     ↓
        human report · JSON · SARIF · exit status
```

Jarproof reads JAR files, class directories, and bytecode without loading or
executing analyzed classes. Analysis is deterministic and does not use
reflection or network access.

## Development

Jarproof is built with [Zolt](https://github.com/zoltsh/zolt).

```sh
scripts/check
```

The check resolves the locked workspace, runs unit, integration, and Smoque
smoke tests, checks coverage and architecture rules, and packages every member.

## License

Apache-2.0. See [LICENSE](./LICENSE).

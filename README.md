# learn-png

A dependency-free PNG codec written in Scala 3, accompanied by a practical book that builds the
format from first principles.

The implementation favors small, colocated domain modules, explicit errors, immutable values, and
Scala 3 features such as opaque types, enums, extension methods, and exhaustive pattern matching.

## Development

Install [Scala CLI](https://scala-cli.virtuslab.org/), then run:

```console
scala-cli test .
scala-cli fmt .
```

The book starts at [`docs/README.md`](docs/README.md).


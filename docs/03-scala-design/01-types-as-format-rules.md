# Scala 3 Types as Format Rules

## Goal

Move checks from scattered `if` statements into domain constructors.

Binary codecs often become a large parser full of primitive integers and strings. That structure
makes it easy to validate a rule once and accidentally bypass it elsewhere. Instead, identify
values that carry invariants.

## Opaque chunk types

A chunk name is not any string. It is four ASCII letters whose third letter is uppercase. An opaque
type gives compile-time separation without wrapper allocation:

```scala
opaque type ChunkType = String

object ChunkType:
  def fromString(value: String): Either[PngError, ChunkType] = ...
```

Only the companion can manufacture a `ChunkType`; extension methods expose semantic bits such as
`isAncillary` and `isSafeToCopy`.

## Enums for closed tables

Color types and errors are closed sets, so Scala 3 enums fit directly. A `ColorType` case carries
its wire code, channel count, and legal depths. Exhaustive matching forces sample decoding to handle
new cases if the model changes.

`PngError` is also an enum. Malformed input is expected when parsing files, so public operations
return `Either[PngError, A]`. Exceptions remain reserved for programmer errors such as indexing an
`Image` outside its dimensions.

## Smart constructors

`Rgba.apply` returns `Either` because external channel values may be invalid. `Rgba.unsafe` is
package-private and used only after bytes have already established the 0–255 invariant. The name
makes every bypass visible during review.

Arrays require special care: a `val Array[Byte]` is not immutable. Clone at construction and when
returning public data. `Chunk` does both and implements value equality using `Arrays.equals`.

## Colocation

Keep a concept's representation, constructor, serialization, and focused tests near one another:

```text
src/png/chunk/Chunk.scala   src/png/chunk/Chunk.test.scala
src/png/header/Header.scala src/png/header/Header.test.scala
src/png/filter/Filter.scala src/png/filter/Filter.test.scala
src/png/adam7/Adam7.scala   src/png/adam7/Adam7.test.scala
```

The top-level codec orchestrates them; it does not duplicate their validation rules.

Scala CLI recognizes the `.test.scala` suffix as test scope, so production and test compilation
remain correctly separated even though the files are physically adjacent. This is the recommended
convention in the [Scala CLI test documentation](https://scala-cli.virtuslab.org/docs/commands/test/).

## Exercise

Model `BitDepth` as an opaque type. Compare the result with the current design where legality
depends on the `(ColorType, Int)` pair. Which design makes invalid combinations hardest to express?

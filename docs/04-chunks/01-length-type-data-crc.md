# The Four Fields of a Chunk

## Goal

Parse one physical chunk, reproduce it byte for byte, and reject corruption before interpreting
the payload.

After the signature, PNG is a sequence of chunks. Every chunk has the same framing, specified by
[PNG §5.3](https://www.w3.org/TR/png-3/#5Chunk-layout):

```text
length: 4 bytes | type: 4 bytes | data: length bytes | CRC: 4 bytes
```

`length` counts only data. It does not count the type or CRC. The legal PNG maximum is `2^31 - 1`,
even though the field is an unsigned 32-bit integer. This is convenient on the JVM, but never cast a
read `Long` to `Int` before checking the upper bound.

## Parse in dependency order

The parser has a natural `Either` comprehension:

```scala
for
  length   <- cursor.uint32
  _        <- validateLength(length)
  rawType  <- cursor.take(4)
  kind     <- ChunkType.fromBytes(rawType)
  data     <- cursor.take(length.toInt)
  expected <- cursor.uint32
  actual    = crc(rawType ++ data)
  _        <- requireCrc(expected, actual)
yield Chunk(kind, data)
```

Every step depends on earlier validated information. A truncated payload never reaches CRC logic;
an invalid type never becomes a domain value.

## Why the CRC excludes length

PNG protects type and data. If a length bit changes, the parser will usually select the wrong
payload and CRC location, causing validation to fail anyway. Including type prevents corruption
from turning an ancillary chunk into a critical one or IDAT into another meaning.

The algorithm is CRC-32 with the polynomial and complement rules in
[PNG §5.5](https://www.w3.org/TR/png-3/#5CRC-algorithm). `java.util.zip.CRC32` is suitable, but a
learning implementation should still build the lookup table once to understand the bit recurrence.

## Independent known answer

Do not test only `parse(serialize(chunk))`. If both operations omit the type from CRC, they agree on
the same invalid format. IEND's complete bytes are fixed:

```text
00 00 00 00 49 45 4e 44 ae 42 60 82
```

This proves length, type encoding, empty data, and CRC against an external known answer.

## Ownership

`Chunk` accepts and returns arrays, so it clones at both boundaries. Test this explicitly:

1. construct a chunk from an array;
2. mutate the original array;
3. retrieve `chunk.data` and mutate that;
4. retrieve it again and verify neither mutation changed the chunk.

## Exercises

1. Parse a chunk whose declared length is one larger than available data. Record the exact offset.
2. Flip each of the four type bytes and confirm CRC failure.
3. Implement CRC-32 from the sample code in the specification and compare it with `CRC32` for 1,000
   deterministic random byte arrays.


# 2. Binary Values and Chunks

Read [PNG integers and byte order](https://www.w3.org/TR/png-3/#7Integers-and-byte-order), then
[`Binary.scala`](../src/main/scala/png/Binary.scala). PNG uses unsigned integers, while the JVM's
integer types are signed. The representation strategy is simple:

- store `uint8` in an `Int`;
- store `uint32` in a `Long`;
- mask a byte with `& 0xff` before widening it;
- shift the most significant byte first.

The `Cursor` owns a clone of the input and advances only after a successful read. This makes a
truncation error precise: it contains the failing offset, required size, and remaining size.

## Chunk types are data with behavior

[Chunk naming conventions](https://www.w3.org/TR/png-3/#5Chunk-naming-conventions) assign meaning to
the case of each ASCII letter. `ChunkType` is an opaque type: callers cannot confuse an arbitrary
`String` with a validated type, but there is no wrapper allocation at runtime.

The third letter is reserved and must be uppercase. The first says whether a chunk is ancillary,
which lets the decoder reject unknown critical chunks while safely skipping unknown ancillary
chunks.

## CRC-32

The CRC covers the four type bytes followed by data—not the length. Java's `CRC32` implements the
polynomial and initialization described by [PNG §5.5](https://www.w3.org/TR/png-3/#5CRC-algorithm).
Test against a published complete chunk, not merely against your own encoder: IEND always ends in
`AE 42 60 82`. A self-round-trip alone can preserve the same mistake in both directions.

## Exercise

Change one payload bit in a serialized chunk and parse it. The parser must return `CrcMismatch`,
not partial data and not an uncaught exception.


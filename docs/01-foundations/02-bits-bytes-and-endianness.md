# Bits, Signed Bytes, and Endianness

## Goal

Read and write PNG integers without accidental sign extension or byte reversal.

## New words in this chapter

- **bit**: one 0-or-1 digit;
- **byte**: a group of eight bits;
- **unsigned**: interpreting stored bits as zero or a positive number;
- **big-endian**: placing the most significant byte first;
- **cursor**: a small reader that remembers the current position in an array.

PNG's [integer representation](https://www.w3.org/TR/png-3/#7Integers-and-byte-order) is unsigned
and big-endian. The JVM gives us signed `Byte`, `Int`, and `Long`; signedness is a property of the
operation, not of the eight stored bits.

```scala
val byte: Byte = 0xff.toByte // numerically -1
val unsigned: Int = byte & 0xff // 255
```

Without the mask, widening `byte.toInt` preserves the sign and yields `-1`.

## Writing uint32

For `0x89abcdef`, take bits 31–24, 23–16, 15–8, and 7–0:

```scala
Array(24, 16, 8, 0).map(shift => (value >>> shift).toByte)
```

The unsigned shift `>>>` is intentional. Store a PNG uint32 in `Long`, because valid values above
`2^31 - 1` do not fit a positive JVM `Int`.

## Reading uint32

Fold from the most significant byte:

```scala
bytes.foldLeft(0L)((result, byte) =>
  (result << 8) | (byte & 0xffL)
)
```

Try removing `& 0xffL` and use a byte above `0x7f`. The sign bits contaminate the result.

## A transactional cursor

Binary parsers become difficult to reason about when failed reads partially advance. Our cursor
first checks `remaining >= count`, then copies, then advances. Its error records offset, requested
count, and available count. The input array is cloned so a caller cannot mutate bytes while parsing.

This is a small example of a recurring design rule: make the lowest-level primitive own the
invariant so every higher-level parser benefits automatically.

## Exercises

1. Implement `uint16` reading and test `0x0000`, `0x7fff`, `0x8000`, and `0xffff`.
2. Add a test proving a failed `take` leaves `offset` unchanged.
3. Explain why a negative `count` should be rejected even if `remaining < count` is false.

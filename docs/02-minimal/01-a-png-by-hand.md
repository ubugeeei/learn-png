# Build a 1×1 PNG by Hand

## Goal

Produce a real image before building reusable abstractions. Our first image is one opaque red pixel.

A PNG datastream is signature + IHDR + IDAT + IEND. The fixed signature is:

```text
89 50 4e 47 0d 0a 1a 0a
```

It combines a high-bit byte, ASCII `PNG`, DOS and Unix line endings, and a control character. These
bytes detect text-mode transfer damage and common wrong-file mistakes; see
[PNG §5.2](https://www.w3.org/TR/png-3/#5PNG-file-signature).

## IHDR payload

For width 1, height 1, depth 8, RGBA (type 6), no interlace:

```text
00 00 00 01  width
00 00 00 01  height
08           bit depth
06           color type
00           compression method
00           filter method
00           interlace method
```

IHDR is a chunk, so wrap these 13 bytes with length, ASCII `IHDR`, and CRC.

## Raw image data

The single row is five bytes: filter `None` followed by red, green, blue, alpha.

```text
00 ff 00 00 ff
```

Compress those five bytes as a **zlib stream**, not raw Deflate, and place the result in IDAT. Java's
`DeflaterOutputStream` produces the correct zlib wrapper by default.

IEND has no payload. Its complete bytes are a valuable known-answer test:

```text
00 00 00 00 49 45 4e 44 ae 42 60 82
```

## Why begin with hard-coded bytes?

The minimal file gives every later abstraction an observable purpose. `Header` replaces the 13
manual bytes. `Chunk` replaces repeated framing. `Filter` generalizes the leading zero. `Samples`
replaces manual RGBA ordering. If we begin with abstractions, those boundaries can feel arbitrary.

## Checkpoint

Write the file, open it in an image viewer, and inspect it with `xxd`. Then change only alpha to
zero. The RGB values remain red even though a compositor will display transparency.


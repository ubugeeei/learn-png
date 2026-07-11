# 4. Filters and Compression

Filtering and compression solve different problems. A PNG filter transforms a row into differences
that Deflate can compress more effectively; it does not reduce information itself. Read
[filter algorithms](https://www.w3.org/TR/png-3/#9Filter-types) and
[compression](https://www.w3.org/TR/png-3/#10Compression).

For byte `x`, filters predict from already known neighbors:

| Filter | Predictor |
|---|---|
| None | `0` |
| Sub | byte `bpp` positions to the left |
| Up | byte above |
| Average | floor of `(left + above) / 2` |
| Paeth | nearest of left, above, upper-left |

Subtraction during encoding and addition during decoding are modulo 256. Scala/JVM `Byte` is
signed, so arithmetic first uses `& 0xff`, then `.toByte` deliberately keeps the low eight bits.

`bpp` means bytes per complete pixel for filtering, rounded up to at least one. It is not the image
width and not always the sample count. For packed one-bit grayscale it is one; for RGBA8 it is four.

## Adaptive selection

Each row may use a different filter. The encoder tries all five and selects the smallest sum of
absolute signed filtered bytes, a common low-cost heuristic. Correctness never depends on this
choice: tests run every filter over empty, small, long, and high-bit rows with several `bpp` values.

## The zlib boundary

IDAT payloads concatenate into one zlib stream. They are not independently compressed blocks.
Conversely, compressed bytes may be split across any number of consecutive IDAT chunks.

The decoder knows the exact decompressed size from IHDR: `(rowBytes + 1) * height`. Its bounded
output stream aborts if zlib attempts to produce more. This is an important defense against tiny
inputs that expand into excessive memory.


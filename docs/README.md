# Building a PNG Codec in Scala 3

This book develops a Portable Network Graphics codec from the bytes upward. It assumes ordinary
programming experience, but no prior knowledge of image formats or compression.

PNG is standardized by the W3C in the
[PNG Third Edition](https://www.w3.org/TR/png-3/) and registered as ISO/IEC 15948. Every chapter
links to the normative section it implements. The specification is the authority; this book
explains how to turn it into executable Scala.

## Reading path

1. [A map of the format](01-format-map.md)
2. Binary values and byte order
3. Chunks and CRC-32
4. The image model and color types
5. Scanline filters
6. Deflate and zlib
7. Encoding
8. Decoding and validation
9. Interlacing with Adam7
10. Testing a binary format

Each chapter follows the same loop: read the relevant requirement, model its invariants, implement
the smallest useful unit, and test both valid and hostile input.


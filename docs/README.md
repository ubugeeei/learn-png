# Building a PNG Codec in Scala 3

This book develops a Portable Network Graphics codec from the bytes upward. It assumes ordinary
programming experience, but no prior knowledge of image formats or compression.

PNG is standardized by the W3C in the
[PNG Third Edition](https://www.w3.org/TR/png-3/) and registered as ISO/IEC 15948. Every chapter
links to the normative section it implements. The specification is the authority; this book
explains how to turn it into executable Scala.

## Reading path

1. [A map of the format](01-format-map.md)
2. [Binary values, chunks, and CRC-32](02-binary-and-chunks.md)
3. [The image model, color types, and IHDR](03-image-and-header.md)
4. [Scanline filters and zlib](04-filters-and-compression.md)
5. [Encoding step by step](05-encoding.md)
6. [Defensive decoding](06-decoding.md)
7. [Extending the codec with Adam7](07-adam7.md)
8. [Testing a binary format](08-testing.md)

Each chapter follows the same loop: read the relevant requirement, model its invariants, implement
the smallest useful unit, and test both valid and hostile input.

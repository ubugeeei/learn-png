# Building a PNG Codec in Scala 3

This book develops a Portable Network Graphics codec from the bytes upward. It assumes ordinary
programming experience, but no prior knowledge of image formats or compression.

PNG is standardized by the W3C in the
[PNG Third Edition](https://www.w3.org/TR/png-3/) and registered as ISO/IEC 15948. Every chapter
links to the normative section it implements. The specification is the authority; this book
explains how to turn it into executable Scala.

The first version of this repository was a codec accompanied by eight short notes. This edition is
being rebuilt as an implementation book: self-contained background, small working increments,
normative links, executable tests, and a path from one red pixel to a hardened codec.

## Part 0 — About the book

1. [Why build PNG?](00-about/01-purpose.md)
2. [How to use this book](00-about/02-how-to-read.md)

## Part 1 — Foundations

3. [Pixels, samples, and scanlines](01-foundations/01-pixels-samples-scanlines.md)
4. [Bits, signed bytes, and endianness](01-foundations/02-bits-bytes-and-endianness.md)

## Part 2 — The smallest working PNG

5. [Build a 1×1 PNG by hand](02-minimal/01-a-png-by-hand.md)

## Part 3 — Designing the codec in Scala

6. [Types as format rules](03-scala-design/01-types-as-format-rules.md)

## Part 4 — Chunks

7. [Length, type, data, and CRC](04-chunks/01-length-type-data-crc.md)
8. [Type bits and datastream ordering](04-chunks/02-type-bits-and-ordering.md)

## Part 5 — Scanline transformation

9. [Filters as reversible predictors](05-filters/01-reversible-predictors.md)

## Part 6 — Decoding

10. [From bytes to pixels](06-decoder/01-from-bytes-to-pixels.md)

## Part 7 — Interlacing

11. [Deriving Adam7](07-interlace/01-derive-adam7.md)

## Original implementation notes

12. [A map of the format](01-format-map.md)
13. [Binary values, chunks, and CRC-32](02-binary-and-chunks.md)
14. [The image model, color types, and IHDR](03-image-and-header.md)
15. [Scanline filters and zlib](04-filters-and-compression.md)
16. [Encoding step by step](05-encoding.md)
17. [Defensive decoding](06-decoding.md)
18. [Adam7](07-adam7.md)
19. [Testing a binary format](08-testing.md)

Each chapter follows the same loop: read the relevant requirement, model its invariants, implement
the smallest useful unit, and test both valid and hostile input.

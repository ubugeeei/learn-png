# Why Build PNG?

If words such as “byte” or “pixel” are new, begin with
[An image is a grid of numbers](00-no-prerequisites.md). This chapter describes the destination and
therefore mentions features that later chapters explain one at a time.

This is not primarily a book about calling an image library. It is a book about turning a binary
standard into a small, trustworthy program.

By the end, you should be able to:

- explain a PNG file from its fixed opening bytes to its end marker;
- derive row sizes for every legal color type and bit depth;
- implement CRC-32, all five filters, packed samples, palettes, transparency, and Adam7;
- explain which part belongs to PNG and which part belongs to its compression library;
- design a decoder that treats hostile input as data rather than as exceptional chaos;
- use Scala 3 types to make invalid states difficult to represent;
- read the normative [PNG Third Edition](https://www.w3.org/TR/png-3/) without being intimidated.

The goal is not memorization. The goal is to develop a repeatable method for implementing any
binary format: isolate a rule, model its invariant, write an independent oracle, then compose it.

## Who this is for

You need to be able to read ordinary Scala expressions and collections. You do not need prior image
processing, compression, binary arithmetic, or format-design experience. When the book uses a term
such as *sample*, *scanline*, *network byte order*, or *predictor*, it introduces the idea before
depending on it.

Experienced readers can skip background sections and follow the implementation checkpoints.
Beginners should type the small examples, including the intentionally broken ones.

## What “from scratch” means here

We implement the PNG container, validation, sample packing, filtering, interlacing, and image model.
We use the JDK's zlib implementation for Deflate. Deflate is a separate format standardized by
[RFC 1951](https://www.rfc-editor.org/rfc/rfc1951), wrapped by zlib as specified by
[RFC 1950](https://www.rfc-editor.org/rfc/rfc1950). Reimplementing its LZ77 and Huffman machinery
would be another book and would distract from the PNG boundary. A later appendix maps that boundary
precisely so there is no magic hand-wave.

## Learning in working increments

Every milestone leaves the program in a working state:

1. recognize a PNG signature;
2. construct a valid 1×1 file;
3. read and write one chunk;
4. validate CRC independently;
5. model IHDR combinations;
6. reverse one filter, then all five;
7. decode RGBA8;
8. generalize to every color type and depth;
9. add transparency and palettes;
10. scatter and gather Adam7 passes;
11. harden sizes, ordering, and unknown chunks;
12. expose a practical API and command-line tool.

At no point should you need hundreds of unfinished lines before seeing a result.

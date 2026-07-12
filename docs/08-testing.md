# 8. Testing a Binary Format

Binary codec tests need several independent oracles.

## Layer tests

Test byte order, cursor truncation, chunk-type validation, a known CRC, every filter, header
combinations, palette bounds, and image invariants separately. A failure then points to one concept.

## Round trips

For valid images, assert `decode(encode(image)) == image` across dimensions and channel patterns.
Round trips are broad but not sufficient: an encoder and decoder can agree on the same wrong format.

## Independent interoperability

The suite feeds encoded output to Java ImageIO and compares pixels. This provides an implementation
independent of our parser.

## Run an external format corpus

The repository vendors the 15 basic images from
[Willem van Schaik's PngSuite](https://www.schaik.com/pngsuite/). Together they exercise every legal
color type and bit-depth combination from 1-bit grayscale through 16-bit RGBA. Each fixture must
decode through both the convenient 8-bit API and the lossless 16-bit API.

For fixtures where Java ImageIO exposes stored samples directly, the test compares every ARGB pixel.
ImageIO applies color conversion or different 16-to-8 rounding to some grayscale, alpha, and 16-bit
fixtures. Comparing ARGB there would test presentation policy rather than parsing, so those cases
receive independent dimension checks and lossless decoder coverage instead.

Fixtures live in `testdata/pngsuite`, next to the upstream permission notice. Keeping them in the
repository makes CI deterministic and avoids trusting a live download server during every run.

## Mutation tests

Starting from a valid file, mutate one invariant at a time:

- signature byte;
- chunk CRC;
- chunk length;
- IHDR method or color/depth pair;
- filter byte;
- palette index;
- chunk ordering;
- bytes after IEND;
- compressed output beyond the computed limit.

Assert the error category and relevant metadata, not only `isLeft`. This makes error quality part of
the API contract.

## Final checklist

- Every normative rule has a nearby specification link.
- Arrays are cloned at public immutable boundaries.
- Length arithmetic widens before multiplication.
- Allocation follows validation.
- Unknown critical chunks fail; unknown ancillary chunks do not.
- IDAT chunks are consecutive and treated as one zlib stream.
- No implementation file grows beyond the project's roughly 350-line review budget.

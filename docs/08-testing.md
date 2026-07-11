# 8. Testing a Binary Format

Binary codec tests need several independent oracles.

## Layer tests

Test byte order, cursor truncation, chunk-type validation, a known CRC, every filter, header
combinations, palette bounds, and image invariants separately. A failure then points to one concept.

## Round trips

For valid images, assert `decode(encode(image)) == image` across dimensions and channel patterns.
Round trips are broad but not sufficient: an encoder and decoder can agree on the same wrong format.

## Independent interoperability

The suite feeds encoded output to Java ImageIO and compares a selected ARGB pixel. This provides an
implementation independent of our parser. A larger conformance suite can use the
[PngSuite corpus](http://www.schaik.com/pngsuite/) and compare every pixel.

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


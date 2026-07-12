# Deriving Adam7 Instead of Memorizing It

## Goal

Decode and encode seven sparse images while reusing ordinary row, filter, and sample logic.

Adam7 progressively fills a grid. The normative diagram is in
[PNG §8.2](https://www.w3.org/TR/png-3/#8Interlace). Represent each pass by start and step:

```scala
Pass(number, xStart, yStart, xStep, yStep)
```

For one dimension, positions are `start, start + step, ... < size`. The count is:

```text
0                                      when size <= start
(size - start + step - 1) / step       otherwise
```

This ceiling-division form prevents negative or phantom extents.

## Empty passes are ordinary

A 1×1 image has data only in pass 1. Other passes may have nonzero width but zero height, or the
reverse. A pass contributes bytes only when both are nonzero. Special-casing by pass number tends to
produce bugs; deriving width and height uniformly does not.

## Decoding one pass

Treat a pass as an image with `passWidth` and `passHeight`:

1. calculate its packed row bytes from pass width;
2. reset `previous` to empty;
3. read filter byte and row bytes;
4. reverse the filter;
5. decode samples for `passWidth` pixels;
6. repeat for pass height.

Then scatter pass pixel `(px, py)` into the final raster:

```text
x = xStart + px × xStep
y = yStart + py × yStep
```

Encoding gathers pixels using the same coordinates, filters the gathered rows, and concatenates all
passes before zlib compression.

## Three strong invariants

Geometry tests do not need PNG fixtures:

1. For every positive width and height, pass coordinates are unique.
2. Their union equals every image coordinate exactly once.
3. Derived inflated size equals the sum of encoded pass byte lengths.

The suite checks the first two for all dimensions 1 through 17, crossing several 8-pixel period
boundaries. Codec tests then round-trip 1×1, narrow, square, and irregular Adam7 images.

## Why filter history resets

The “previous row” is previous within the current pass, not the previous full-image y coordinate and
not the last row of the previous pass. Passes are independently filtered subimages. Forgetting the
reset can make your own encoder and decoder agree while producing files incompatible with every
other implementation.

## Exercises

1. Print a 16×16 grid labeled with pass numbers using `Pass.coordinates`.
2. Prove the 1×1 expected pass extents by hand before looking at `Adam7.test.scala`.
3. Feed an Adam7 file emitted by this encoder into ImageIO and compare every ARGB pixel.


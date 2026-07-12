# Filters as Reversible Predictors

## Goal

Implement all five filters from one predictor equation and prove reversal for arbitrary byte rows.

Deflate finds repeated byte patterns. Adjacent image pixels often change gradually but their raw
channel values may be large and varied. PNG transforms each byte into the difference from a
prediction. Flat regions then contain many zeros and small repeated differences.

For raw byte `x` and predictor `p`:

```text
filtered = (x - p) mod 256
raw      = (filtered + p) mod 256
```

The five predictors are specified by [PNG §9.2](https://www.w3.org/TR/png-3/#9Filter-types):

- None: zero;
- Sub: reconstructed byte `bpp` positions left;
- Up: byte at the same position in the previous row;
- Average: floor of `(left + up) / 2`;
- Paeth: choose left, up, or upper-left nearest to `left + up - upperLeft`.

## The crucial asymmetry

Encoding uses bytes from the original current row as `left`. Decoding uses bytes already
reconstructed into the output row. Using the still-filtered left byte is a common bug that may pass
tests containing only the None and Up filters.

At the left edge, missing left and upper-left values are zero. On the first scanline, all previous
row values are zero. For Adam7, “first scanline” applies separately to every pass.

## What exactly is bpp?

The specification defines `bpp` as the number of bytes needed for one complete pixel, rounded up to
at least one. Examples:

| Format | Bits per pixel | Filter bpp |
|---|---:|---:|
| grayscale1 | 1 | 1 |
| indexed4 | 4 | 1 |
| truecolor8 | 24 | 3 |
| RGBA8 | 32 | 4 |
| truecolor16 | 48 | 6 |

Packed samples still use `bpp = 1`; filters operate on packed bytes, not logical samples.

## Signed Byte as a scoring heuristic

Our adaptive encoder tries every filter and minimizes the sum of absolute signed byte values. This
does not affect correctness; any filter is reversible. It approximates a preference for differences
near zero without performing five Deflate trials per row.

Be precise about the dual interpretations: filter arithmetic uses unsigned values modulo 256, while
the scoring heuristic intentionally interprets the stored byte as signed `-128..127`.

## Property-shaped testing

For each filter, row, previous row, and bpp:

```scala
decode(encode(row, previous, bpp), previous, bpp) == row
```

Include empty rows, high-bit bytes, rows shorter than bpp, and lengths not divisible by bpp. This
single law covers far more cases than five hand-calculated examples, while a few normative examples
still serve as independent checks.

## Exercises

1. Intentionally decode Sub from the filtered left byte and find the smallest failing row.
2. Compare compressed sizes produced by always-None and adaptive selection for a gradient image.
3. Add a strategy enum allowing None-only, fixed filter, and adaptive encoding.


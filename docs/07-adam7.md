# 7. Extending the Codec with Adam7

[Adam7](https://www.w3.org/TR/png-3/#8Interlace) transmits an image in seven sparse passes so a
decoder can display a coarse preview early. Each pass has an `(xStart, yStart, xStep, yStep)`:

| Pass | Start | Step |
|---|---|---|
| 1 | (0, 0) | (8, 8) |
| 2 | (4, 0) | (8, 8) |
| 3 | (0, 4) | (4, 8) |
| 4 | (2, 0) | (4, 4) |
| 5 | (0, 2) | (2, 4) |
| 6 | (1, 0) | (2, 2) |
| 7 | (0, 1) | (1, 2) |

To implement it safely:

1. Compute each pass width and height with ceiling division; skip empty passes.
2. Compute pass row bytes from pass width, color channels, and bit depth.
3. Reset the previous row to empty at the beginning of every pass.
4. Reverse filters within the pass.
5. Decode its samples with the same `Samples.decodeRow` used by ordinary rows.
6. Scatter pixel `(px, py)` to `(xStart + px*xStep, yStart + py*yStep)`.
7. Require that the seven passes consume the inflated stream exactly.

This design reuses sample and filter logic and colocates only pass geometry in a new `Adam7`
module. Tests should include images narrower and shorter than eight pixels because empty passes are
the usual source of off-by-one errors.


# 3. Images, Samples, and IHDR

Start with [image layout](https://www.w3.org/TR/png-3/#4Concepts.FormatPixels) and the
[IHDR definition](https://www.w3.org/TR/png-3/#11IHDR). The public model is deliberately smaller
than the file model:

```scala
val red = Rgba(255, 0, 0).toOption.get
val image = Image(width = 2, height = 1, Vector(red, red))
```

`Rgba` validates channel bounds. `Image` validates positive dimensions, overflow, and exact pixel
count. Once constructed, later code can rely on those invariants without defensive repetition.

## Why normalize to RGBA?

PNG can store grayscale, truecolor, indexed color, and two alpha-bearing variants, at depths from
1 to 16 bits. Exposing all combinations would spread packing logic into every caller. The decoder
instead expands them to eight-bit RGBA. The encoder currently chooses truecolor with alpha (type
6, depth 8), a lossless representation for the public model.

`ColorType` is a Scala 3 enum whose cases carry their numeric code, channel count, and legal bit
depths. This turns the table in the specification into executable data. Adding a made-up
truecolor/depth-4 header fails at construction rather than much later in encoding.

## Packed samples

Depths 1, 2, and 4 pack multiple samples into each byte, most significant bits first. Rows are
padded to a whole byte, but the padding is not a sample. Sixteen-bit samples are big-endian. When
normalizing, this project uses the high byte, the exact scaling rule recommended for reducing 16
bits to 8 bits in [sample depth scaling](https://www.w3.org/TR/png-3/#13Sample-depth-scaling).

Indexed pixels are addresses into PLTE. A palette address outside the palette is malformed data,
not a transparent or black pixel.


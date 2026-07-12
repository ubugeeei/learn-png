# 5. Encoding, Step by Step

Follow [`Png.encode`](../src/png/Png.scala) while consulting the
[encoder recommendations](https://www.w3.org/TR/png-3/#13Encoders).

1. Construct IHDR for the chosen lossless representation: RGBA, depth 8, no interlace.
2. Convert each row from pixels to `R, G, B, A` bytes.
3. Evaluate all five filters against the previous unfiltered row.
4. Prefix the selected filter byte to its transformed row.
5. Compress the complete sequence as one zlib stream.
6. Create IHDR, IDAT, and empty IEND chunks.
7. Prefix the fixed eight-byte PNG signature.

The resulting shape is minimal and broadly interoperable:

```text
signature | IHDR | IDAT | IEND
```

## A runnable use

```scala
import png.*
import java.nio.file.Files

val pixels = Vector(
  Rgba(255, 0, 0).toOption.get,
  Rgba(0, 255, 0).toOption.get,
  Rgba(0, 0, 255).toOption.get,
  Rgba(0, 0, 0, 0).toOption.get
)
val image = Image(2, 2, pixels).toOption.get
val bytes = Png.encode(image).toOption.get
Files.write(java.nio.file.Path.of("example.png"), bytes)
```

Production code should keep the `Either` and report `PngError.message`; `.toOption.get` is used
only to keep this construction example compact.

## Why one IDAT?

Chunk splitting changes no image semantics. A streaming encoder would emit multiple bounded IDAT
chunks to limit memory. This educational encoder uses one chunk so the transformation is visible
without a streaming abstraction obscuring it.

# learn-png

A dependency-free PNG codec written in Scala 3, accompanied by a practical book that builds the
format from first principles. Start with [`Png.scala`](src/png/Png.scala); its tests are colocated in
[`Png.test.scala`](src/png/Png.test.scala).

## Start here

```scala
import png.*
import java.nio.file.Path

val image: Either[PngError, Image] = Png.read(Path.of("input.png"))
val copy: Either[PngError, Path] = image.flatMap(Png.write(Path.of("output.png"), _))
```

Explore a file from the command line:

```console
scala-cli run . -- info input.png
scala-cli run . -- copy input.png output.png --interlace
```

Source map:

- `src/png/Png.scala` — public encode/decode/read/write facade;
- `src/png/PngCli.scala` — executable entry point;
- `src/png/Image.scala` — public immutable raster and RGBA pixel;
- `src/png/EncoderOptions.scala` and `PngError.scala` — public configuration and errors;
- `src/png/*/` — internal concepts, each beside its `.test.scala` suite.

The implementation favors small, colocated domain modules, explicit errors, immutable values, and
Scala 3 features such as opaque types, enums, extension methods, and exhaustive pattern matching.

## Development

Install [Scala CLI](https://scala-cli.virtuslab.org/), then run:

```console
scala-cli test .
scala-cli fmt .
```

Run the book locally with navigation and search:

```console
npm ci
npm run book:dev
```

The book starts at [`docs/README.md`](docs/README.md).

## Supported format

The encoder writes lossless eight-bit RGBA PNGs, optionally with Adam7. The decoder accepts all five
standard color types and every legal bit depth, Adam7 interlacing, PLTE/tRNS transparency, multiple
consecutive IDAT chunks, and unknown ancillary chunks.

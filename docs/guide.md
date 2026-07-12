# Book and source map

The [complete table of contents](README.md) is the canonical reading order.

## Public entry points

- [`Png`](https://github.com/ubugeeei/learn-png/blob/main/src/png/Png.scala): encode, decode, read, and write
- [`PngCli`](https://github.com/ubugeeei/learn-png/blob/main/src/png/PngCli.scala): runnable `info` and `copy` commands
- [`Image` and `Rgba`](https://github.com/ubugeeei/learn-png/blob/main/src/png/Image.scala): immutable public raster
- [`EncoderOptions`](https://github.com/ubugeeei/learn-png/blob/main/src/png/EncoderOptions.scala): Adam7, compression, and IDAT sizing
- [`DecoderOptions`](https://github.com/ubugeeei/learn-png/blob/main/src/png/DecoderOptions.scala): resource policy
- [`PngError`](https://github.com/ubugeeei/learn-png/blob/main/src/png/PngError.scala): exhaustive failure model

## How source and tests are colocated

Scala CLI treats `.test.scala` as test scope, so each concept is reviewed in one directory:

```text
src/png/filter/Filter.scala
src/png/filter/Filter.test.scala
```

The same pattern is used for binary primitives, chunks, headers, filters, Adam7, and public APIs.

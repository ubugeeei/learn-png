# Metadata Without Silent Data Loss

## Goal

Choose explicitly between a pixel-only operation and a document-preserving operation.

An image decoder and an image editor have different contracts. A renderer needs pixels. A
transcoder is expected to preserve author, density, color, and private workflow information where
the PNG safe-to-copy rules permit it. Returning only `Image` makes accidental loss unavoidable.

The public API therefore has two levels:

```scala
val pixels: Either[PngError, Image] = Png.decode(bytes)
val document: Either[PngError, PngDocument] = Png.decodeDocument(bytes)
```

`PngDocument` couples the normalized raster to `PngMetadata`. The metadata model currently covers
gAMA, sRGB rendering intent, pHYs density, tEXt entries, and unknown ancillary chunks whose
safe-to-copy bit is set.

Each type has a smart constructor. `TextEntry` enforces the 1–79 byte keyword limit, Latin-1, NUL
exclusion, and spacing rules from
[PNG §11.3.4.3](https://www.w3.org/TR/png-3/#11tEXt). `PixelDensity` retains the unsigned 32-bit
range as `Long`. Gamma converts to and from the integer `value × 100000` representation.

## Ordering still matters

Metadata is not an unordered map. gAMA and sRGB must precede PLTE and IDAT. pHYs and tRNS must
precede IDAT. Singleton chunks may not silently use “last one wins.” The strict decoder rejects
duplicates and misplaced chunks before interpreting pixels.

The encoder places portable metadata immediately after IHDR, satisfying those constraints for its
RGBA8 output. Text entries stay ordered because repeated keywords can be meaningful.

## Safe-to-copy is conditional preservation

The fourth type bit tells an editor whether an unknown ancillary chunk may be copied after critical
image data changes. Unknown unsafe-to-copy chunks are omitted during re-encoding. Unknown critical
chunks fail decoding because their semantics may affect pixels.

Compressed and international text, ICC profiles, chromaticities, EXIF, significant bits, suggested
palettes, and timestamps still need typed models. The API therefore documents its supported set
instead of claiming byte-for-byte preservation.


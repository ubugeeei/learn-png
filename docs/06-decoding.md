# 6. Defensive Decoding

A decoder processes untrusted lengths, compressed data, and indices. Its job is validation as much
as reconstruction. Read [decoder behavior](https://www.w3.org/TR/png-3/#13Decoders) and
[chunk ordering](https://www.w3.org/TR/png-3/#5ChunkOrdering).

The implementation proceeds in gates:

1. Compare all eight signature bytes.
2. Parse each chunk with bounds and CRC checks through IEND.
3. Reject bytes after IEND.
4. Require exactly one first IHDR, consecutive IDAT chunks, and one empty final IEND.
5. Reject unknown critical chunks; ignore unknown ancillary chunks.
6. Parse and validate IHDR before allocating image storage.
7. Build PLTE and apply tRNS alpha entries when present.
8. Concatenate IDAT data and inflate it into a bounded output.
9. Reverse each row filter using the previous reconstructed row.
10. Unpack samples and normalize them into RGBA pixels.

Errors are an enum, so applications can distinguish corruption (`CrcMismatch`), unsupported data
(`UnsupportedFeature`), and truncation (`UnexpectedEnd`) without parsing messages.

## Transparency

tRNS has color-type-specific meaning. For indexed images its bytes are palette alpha values, with
missing entries implicitly opaque. For grayscale and truecolor it names one fully transparent
sample or sample triple. Color types already carrying alpha may not use it; stricter ancillary
validation is a natural extension exercise.

## Current boundary

Adam7 interlacing is recognized in IHDR but intentionally returned as `UnsupportedFeature` rather
than decoded incorrectly. The pass geometry is described in the next chapter as the main advanced
extension. The codec otherwise accepts all legal non-interlaced color-type/bit-depth combinations.


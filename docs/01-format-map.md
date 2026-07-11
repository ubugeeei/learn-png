# 1. A Map of the Format

The normative starting points are [file structure](https://www.w3.org/TR/png-3/#5PNG-file-format)
and [the chunk layout](https://www.w3.org/TR/png-3/#5Chunk-layout). A PNG datastream is:

```text
8-byte signature
IHDR chunk
zero or more ancillary chunks
one or more IDAT chunks
IEND chunk
```

A chunk is a length-prefixed record:

```text
4-byte length | 4-byte type | length bytes of data | 4-byte CRC
```

All multi-byte integers use network byte order (most significant byte first). The CRC protects the
type and data, but not the length. These tiny details are exactly where a codec should encode
knowledge in types and constructors instead of scattering checks through parsing code.

## Our architecture

The implementation is organized by concept rather than by technical layer. A domain value lives
beside its constructors, validation, and focused tests. Encoding and decoding depend on those
validated values; they do not recreate their rules.

The pipeline is:

```text
Image -> scanlines -> filters -> zlib -> chunks -> bytes
bytes -> chunks -> zlib -> filters -> samples -> Image
```

Keeping each arrow independently testable is the main design decision in this project.


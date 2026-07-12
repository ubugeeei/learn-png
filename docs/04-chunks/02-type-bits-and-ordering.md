# Chunk Type Bits and Datastream Ordering

## Goal

Decide whether an unknown chunk is safe to ignore, then validate the global chunk state machine.

Chunk type letters carry bits in their ASCII case, described by
[PNG §5.4](https://www.w3.org/TR/png-3/#5Chunk-naming-conventions):

| Letter | Uppercase (bit 5 = 0) | Lowercase (bit 5 = 1) |
|---|---|---|
| 1 | critical | ancillary |
| 2 | public | private |
| 3 | reserved; must be uppercase | invalid today |
| 4 | unsafe to copy | safe to copy |

The decoder primarily needs the first bit. An unknown critical chunk means the pixel interpretation
may depend on semantics we do not know, so decoding must stop. An unknown ancillary chunk can be
ignored while reconstructing pixels.

## Local validity is not global validity

Each chunk may have a valid frame and CRC while the datastream remains invalid. Ordering rules from
[PNG §5.6](https://www.w3.org/TR/png-3/#5ChunkOrdering) include:

- IHDR occurs exactly once and first;
- PLTE occurs at most once and before IDAT;
- indexed-color requires PLTE;
- one or more IDAT chunks occur consecutively;
- IEND occurs exactly once, is empty, and is last.

Think of validation as a state machine:

```text
Start -> AfterIHDR -> InIDAT -> AfterIDAT -> End
```

Ancillary chunks can occupy permitted gaps. Once an IDAT run ends, a later IDAT is invalid even if
all intervening chunks are ancillary.

## Why consecutive IDAT matters

All IDAT payloads concatenate into one zlib stream. Physical chunk boundaries have no compression
meaning. Requiring consecutive chunks lets streaming decoders consume that stream without buffering
arbitrary later chunks or guessing whether more compressed bytes will appear.

The encoder exposes `maximumIdatPayload` to demonstrate this distinction. Setting it to one creates
many one-byte IDAT chunks but still exactly one zlib stream. The round-trip test exercises chunk
ordering and compression continuity simultaneously.

## Error granularity

Return an `InvalidChunkOrder` that names the violated invariant. “Invalid PNG” is technically true
but educationally and operationally weak. Good binary errors make corrupted fixtures debuggable and
let applications decide which failures should be reported to users.

## Exercises

Construct valid chunks in each invalid order below and predict the first error:

1. IDAT, IHDR, IEND;
2. IHDR, IDAT, text, IDAT, IEND;
3. IHDR, PLTE, PLTE, IDAT, IEND;
4. IHDR, IDAT, non-empty IEND;
5. IHDR, an unknown critical chunk, IDAT, IEND.


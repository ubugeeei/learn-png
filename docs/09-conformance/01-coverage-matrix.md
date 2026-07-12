# PNG Third Edition Coverage Matrix

This matrix compares the project with the
[PNG Third Edition, W3C Recommendation 24 June 2025](https://www.w3.org/TR/png-3/). “Decode” means
the semantic information is available to callers, not merely ignored without crashing. “Encode”
means callers can construct that representation through a validated public API.

Status meanings:

- ✅ implemented and covered by focused tests;
- 🟡 partially implemented or normalized with documented information loss;
- ❌ not implemented yet.

## Core datastream

| Specification area | Decode | Encode | Evidence / remaining work |
|---|:---:|:---:|---|
| Signature, chunk framing, CRC | ✅ | ✅ | `Binary`, `Chunk`, known IEND CRC, generated-payload properties |
| Chunk type property bits | ✅ | ✅ | `ChunkType` opaque type and reserved-bit validation |
| IHDR combinations | ✅ | 🟡 | all combinations decode; encoder currently emits RGBA8 only |
| PLTE indexed color | ✅ | ❌ | packed indices decode; no palette-producing encoder |
| Multiple consecutive IDAT | ✅ | ✅ | configurable physical payload size |
| IEND and global ordering | ✅ | ✅ | strict ordering validation |
| Unknown critical chunks | ✅ | n/a | rejected as required |
| Unknown ancillary chunks | 🟡 | 🟡 | safe-to-copy chunks preserved; unsafe chunks intentionally dropped after rewrite |

## Pixels and transformations

| Specification area | Decode | Encode | Evidence / remaining work |
|---|:---:|:---:|---|
| Grayscale depths 1, 2, 4, 8 | ✅ | ❌ | packed sample decoder |
| Indexed depths 1, 2, 4, 8 | ✅ | ❌ | palette lookup and tRNS alpha |
| Truecolor 8 | ✅ | ❌ | normalized to RGBA8; encoder does not select RGB automatically |
| Grayscale+alpha 8 | ✅ | ❌ | normalized to RGBA8 |
| Truecolor+alpha 8 | ✅ | ✅ | default public raster and encoder |
| 16-bit samples | 🟡 | ❌ | decoded by dropping low 8 bits; not PNG-lossless |
| tRNS | ✅ | ❌ | all permitted color types decode; strict length/type validation |
| Filters 0–4 | ✅ | ✅ | known cases plus generated inverse-law testing |
| zlib/Deflate envelope | ✅ | ✅ | completion, dictionary, trailing data, and limits checked |
| Adam7 | ✅ | ✅ | geometry coverage properties and varied-size round trips |
| Progressive row/pass delivery | ❌ | ❌ | current public API buffers the raster |

## Color-space information

| Chunk | Decode | Encode | Notes |
|---|:---:|:---:|---|
| cHRM | ❌ | ❌ | chromaticity model required |
| gAMA | ✅ | ✅ | validated positive fixed-point value |
| iCCP | ❌ | ❌ | profile name, zlib profile, conflict rules required |
| sBIT | ❌ | ❌ | color-type-dependent lengths required |
| sRGB | ✅ | ✅ | all four rendering intents |
| cICP | ❌ | ❌ | Third Edition video/HDR code points required |
| mDCV | ❌ | ❌ | mastering display volume model required |
| cLLI | ❌ | ❌ | content light level model required |

## Text, physical, and editing information

| Chunk | Decode | Encode | Notes |
|---|:---:|:---:|---|
| tEXt | ✅ | ✅ | Latin-1 keyword/value validation |
| zTXt | ❌ | ❌ | compressed Latin-1 text required |
| iTXt | ✅ | ✅ | UTF-8, language tag, translated keyword; compressed input accepted |
| bKGD | ❌ | ❌ | color-type-dependent representation required |
| hIST | ❌ | ❌ | palette-dependent histogram required |
| pHYs | ✅ | ✅ | unitless and pixels-per-metre forms |
| sPLT | ❌ | ❌ | repeated named suggested palettes required |
| eXIf | ✅ | ✅ | opaque profile preserved with defensive copying |
| tIME | ✅ | ✅ | calendar fields validated through `LocalDateTime` |

## Animation (APNG)

| Area | Decode | Encode | Notes |
|---|:---:|:---:|---|
| acTL animation control | ❌ | ❌ | frame/play counts |
| fcTL frame control | ❌ | ❌ | sequence, rectangle, delay, dispose, blend |
| fdAT frame data | ❌ | ❌ | sequence validation and per-frame zlib stream |
| Frame composition | ❌ | ❌ | SOURCE/OVER and NONE/BACKGROUND/PREVIOUS operations |
| Static fallback image | ✅ | ✅ | handled only as ordinary PNG, animation ignored as unknown ancillary data |

## Operational completeness

| Area | Status | Notes |
|---|:---:|---|
| Typed errors and configurable limits | ✅ | file, chunks, dimensions, pixels, inflation |
| Caller-owned stream lifetime | ✅ | consume/flush, never close |
| Transactional path replacement | ✅ | forced temporary sibling and atomic move preference |
| Property-based tests | 🟡 | filters and chunks; sample packing and Adam7 byte streams need generators |
| Independent decoder interoperability | 🟡 | Java ImageIO smoke test only |
| PNGSuite corpus | ❌ | positive and intentionally-invalid corpus integration required |
| Fuzzing | ❌ | coverage-guided fuzz target required |
| Benchmarks | ❌ | throughput and allocation baselines required |
| Published binary/API compatibility | ❌ | no artifact versioning or MiMa policy yet |

## Definition of “comprehensive” for this project

The project may call itself comprehensive only when:

1. every conforming static PNG preserves all 1–16 bit reference-image samples through an
   appropriate public raster;
2. every standardized chunk is parsed, validated, documented, and either semantically exposed or
   explicitly represented as preserved opaque data according to editor rules;
3. APNG frames decode, compose, encode, and validate sequence numbers;
4. positive and negative conformance corpora run in CI;
5. buffering, streaming, security, privacy, and color-management limitations are documented with no
   unsupported implicit claims.

# APNG: Frames Are Rectangles on a Canvas

APNG keeps an ordinary PNG static image for non-animation-aware readers, then adds animation chunks.

```mermaid
flowchart LR
  acTL["acTL: frame/play counts"] --> fcTL1["fcTL: frame 1"]
  fcTL1 --> IDAT["IDAT: first frame or fallback"]
  IDAT --> fcTL2["fcTL: next frame"]
  fcTL2 --> fdAT["fdAT: sequence + compressed rows"]
```

Each fcTL describes a rectangle inside the IHDR canvas, a display delay, a disposal operation, and
a blend operation. Sequence numbers across fcTL and fdAT start at zero and increase without gaps.

## Decode frames with the static codec

Frame compressed bytes use the same color type, bit depth, palette, transparency, filters, and
interlace method as the static PNG. The APNG decoder constructs a validated frame header and reuses
the lossless `Codec16` path rather than maintaining a second sample decoder.

## Blend before disposal

SOURCE replaces canvas pixels. OVER performs straight-alpha composition in 16-bit integer
arithmetic. The displayed frame is captured after blending and before disposal.

```mermaid
flowchart TD
  Existing["existing canvas"] --> Blend
  Rectangle["decoded frame rectangle"] --> Blend{"SOURCE or OVER"}
  Blend --> Displayed["capture displayed canvas"]
  Displayed --> Dispose{"disposal"}
  Dispose -->|NONE| Keep["keep canvas"]
  Dispose -->|BACKGROUND| Clear["clear frame rectangle"]
  Dispose -->|PREVIOUS| Restore["restore saved canvas"]
```

The first frame may not use PREVIOUS because no earlier canvas state exists. A zero delay denominator
means 100, as required by APNG, rather than division by zero.

## Public result

`Png.decodeAnimation` returns `PngAnimation`: canvas dimensions, play count, composed `Image16`
frames, static fallback, and whether that fallback participates as the first animation frame.

## Encode rectangles, not screenshots

`Png.encodeAnimation` accepts an `AnimationSource`. Its frames are the uncomposited rectangles that
will be placed on the canvas, not a sequence of already-composited screenshots. This distinction
keeps small changes small: a blinking one-pixel cursor needs a one-pixel frame.

```scala
val first = AnimationSourceFrame(fullCanvas).toOption.get
val cursor = AnimationSourceFrame(
  image = onePixel,
  xOffset = 12,
  yOffset = 4,
  delayNumerator = 1,
  delayDenominator = 10,
  blend = BlendOperation.Source
).toOption.get

val animation = AnimationSource(
  canvasWidth = fullCanvas.width,
  canvasHeight = fullCanvas.height,
  plays = 0, // repeat forever
  frames = Vector(first, cursor)
).toOption.get

val bytes = Png.encodeAnimation(animation).toOption.get
```

The smart constructors reject rectangles outside the canvas, unsigned fields outside their wire
ranges, an incomplete first fallback frame, and PREVIOUS disposal on the first frame. The encoder
then assigns one continuous sequence across every fcTL and fdAT chunk. IDAT belongs only to the first
frame; later frames use fdAT, whose first four bytes are the sequence number.

The [APNG specification](https://wiki.mozilla.org/APNG_Specification) defines these additional chunks
and the exact ordering rules. Static PNG structure still follows
[PNG Third Edition](https://www.w3.org/TR/png-3/).

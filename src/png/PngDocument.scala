package png

/** Decoded pixels together with portable PNG metadata.
  *
  * Pixel-only callers can continue using [[Png.decode]]. Editors and transcoders should use the document API
  * so metadata is not silently discarded.
  */
final case class PngDocument(
    image: Image,
    metadata: PngMetadata = PngMetadata.empty,
    extendedMetadata: ExtendedMetadata = ExtendedMetadata.empty,
    colorMetadata: ColorMetadata = ColorMetadata.empty
)

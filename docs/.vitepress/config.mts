import { defineConfig } from "vitepress";

export default defineConfig({
  title: "learn-png",
  description: "Build a PNG codec in Scala 3, step by step",
  cleanUrls: true,
  themeConfig: {
    nav: [
      { text: "Book", link: "/00-about/01-purpose" },
      { text: "Source map", link: "/guide" },
      { text: "PNG specification", link: "https://www.w3.org/TR/png-3/" }
    ],
    sidebar: [
      {
        text: "About",
        items: [
          { text: "Why build PNG?", link: "/00-about/01-purpose" },
          { text: "How to read", link: "/00-about/02-how-to-read" }
        ]
      },
      {
        text: "Foundations",
        items: [
          { text: "Pixels and samples", link: "/01-foundations/01-pixels-samples-scanlines" },
          { text: "Bits and byte order", link: "/01-foundations/02-bits-bytes-and-endianness" }
        ]
      },
      {
        text: "Minimum PNG",
        items: [{ text: "A 1×1 PNG by hand", link: "/02-minimal/01-a-png-by-hand" }]
      },
      {
        text: "Scala design",
        items: [{ text: "Types as rules", link: "/03-scala-design/01-types-as-format-rules" }]
      },
      {
        text: "Chunks",
        items: [
          { text: "Chunk framing and CRC", link: "/04-chunks/01-length-type-data-crc" },
          { text: "Type bits and ordering", link: "/04-chunks/02-type-bits-and-ordering" }
        ]
      },
      {
        text: "Filters",
        items: [{ text: "Reversible predictors", link: "/05-filters/01-reversible-predictors" }]
      },
      {
        text: "Decoder",
        items: [{ text: "Bytes to pixels", link: "/06-decoder/01-from-bytes-to-pixels" }]
      },
      {
        text: "Interlacing",
        items: [{ text: "Deriving Adam7", link: "/07-interlace/01-derive-adam7" }]
      }
    ],
    socialLinks: [{ icon: "github", link: "https://github.com/ubugeeei/learn-png" }],
    search: { provider: "local" },
    outline: { level: [2, 3] },
    footer: { message: "Learn the format by building it." }
  }
});

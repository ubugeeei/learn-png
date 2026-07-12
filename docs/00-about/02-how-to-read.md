# How to Use This Book

Keep three windows open: this book, the source tree, and a terminal. The prose explains *why* a
piece exists. The source shows the complete current implementation. Tests provide small executable
examples and specify edge behavior more precisely than prose can.

## The chapter loop

Each implementation chapter follows the same rhythm:

1. **Goal** — one observable capability.
2. **Domain model** — vocabulary and the relevant bytes.
3. **Normative rules** — direct links to the exact specification sections.
4. **Small implementation** — the least code that achieves the goal.
5. **Tests** — happy path, boundary, and malformed input.
6. **Connection** — how this unit plugs into the codec.
7. **Exercises** — changes that deepen the model rather than add busywork.

Run the suite after every change:

```console
scala-cli test . --server=false
```

Format and check the complete tree before committing:

```console
scala-cli fmt .
scala-cli fmt --check .
```

## Do not copy first

For each chapter, first write down the input type, output type, and failure modes. Implement the
obvious version. Only then compare it with the repository. The educational value lies in noticing
why the obvious version fails on signed bytes, overflow, empty Adam7 passes, or a truncated chunk.

## Reading the specification

Normative language is deliberate:

- **shall** or **must**: rejecting a violation is part of correctness;
- **should**: follow it unless you have a reason and document the tradeoff;
- **may**: optional behavior or representation;
- **critical chunk**: a decoder cannot understand the image without it;
- **ancillary chunk**: a decoder can still reconstruct pixels without it.

Keep the [PNG Third Edition](https://www.w3.org/TR/png-3/) open. This book interprets it, but does
not replace it.

## Completion levels

The book has three useful stopping points:

- **Panorama:** after RGBA8 decoding, you understand the complete pipeline.
- **Codec:** after color types and Adam7, you can decode ordinary real-world PNG files.
- **Production boundary:** after defensive limits and conformance testing, you understand what a
  library must promise before processing untrusted files.


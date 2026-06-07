# GTNH MCP

A **client-side** Minecraft 1.7.10 mod for [GregTech New Horizons](https://github.com/GTNewHorizons) that embeds a local
[Model Context Protocol (MCP)](https://modelcontextprotocol.io) server. With it connected, an AI assistant (e.g. Claude
Code / Claude Desktop) can **read your running game state** and help you answer questions like *"why won't this multiblock
form?"* — by looking at the controller you're pointing at and the blocks you've actually built.

Everything it exposes is **read-only** and the server **binds to `127.0.0.1` only**.

## How it works

The mod hosts an MCP server *inside the Minecraft client*, over the MCP "Streamable HTTP" transport. Claude connects to
it directly — there is no separate bridge process to run.

```
Minecraft client (GTNH)
  └─ gtnhmcp mod ── embedded HTTP server on 127.0.0.1:25590  (MCP Streamable HTTP, /mcp)
                     ├─ initialize · tools/list · tools/call
                     └─ each tool call hops onto the Minecraft client thread, reads state, returns JSON

Claude Code ── claude mcp add --transport http gtnh http://127.0.0.1:25590/mcp
```

Notes on the design:

- The official MCP Java SDK requires Java 17+, which a 1.7.10 (Java-8-bytecode) mod can't use, so the small, stable MCP
  request/response subset is implemented by hand on the JDK's built-in `com.sun.net.httpserver`.
- GregTech is read via **reflection** (long-stable fields/methods like `mMachine`, `getRepairStatus`, …). This means the
  mod has **no compile-time GregTech dependency**, compiles as a plain Minecraft mod, and works against whatever GregTech
  version you actually have. The generic tools work in any 1.7.10 instance; multiblock diagnosis activates when GregTech
  is present.
- Game state is read on the Minecraft client thread (via `Minecraft`'s scheduled-task queue) so there are no data races.

## Tools exposed

| Tool | What it does |
| --- | --- |
| `get_target` | Details about the block under your crosshair: name, registry id, metadata, tile entity, and GregTech machine identity. |
| `diagnose_multiblock` | The headline tool. For the targeted (or specified) GregTech multiblock controller: structure-formed? maintenance problems + which tools fix them, idle/active, recipe progress, efficiency, energy buffer, the in-game scanner lines, and a plain-language summary of likely causes. |
| `scan_blocks` | Counts every block type in a cube around you (or a point) and lists tile entities (GregTech machines/hatches annotated). A quick overview of what's nearby. |
| `read_region` | Returns the **exact** block at every coordinate in a box (id, metadata, display name, GregTech annotation), volume-capped. Use this — not `scan_blocks` — to verify a multiblock cell-by-cell against its required structure. |
| `inspect_object` | Generic **read-only** reflection dump of a tile entity: all fields (incl. private) and every no-arg getter-style method (`get/is/has/can/are/size/count`); for GregTech it also dumps the meta tile entity. Reads state no other tool exposes (e.g. `isAllowedToWork`, `motorTier`). Never calls mutating methods. |
| `get_player` | Position, dimension, health/food, held item, and what you're looking at. |
| `get_inventory` | Held item, hotbar/main inventory, and armor, with each stack's name, id, count, and metadata. |

All tools are advertised as read-only (`readOnlyHint`).

## Building

This is a standard GTNH mod built with [GTNHGradle](https://github.com/GTNewHorizons/GTNHGradle).

```bash
./gradlew build      # compiles, runs the unit tests, and produces the jar in build/libs/
```

The build uses a Java toolchain provisioned by Gradle (Azul Zulu, the GTNH-pinned vendor); `enableModernJavaSyntax = jabel`
keeps the output as Java 8 bytecode for 1.7.10. If you build on a machine that has no JDK on `PATH`, point Gradle at one
and let it auto-detect/provision toolchains as usual.

> **Versioning:** the jar version comes from a Git tag. In a fresh checkout with no tags the jar is named
> `gtnhmcp-NO-GIT-TAG-SET.jar`. Run `git init && git add -A && git commit -m "init" && git tag 0.1.0` to get a versioned
> jar (`gtnhmcp-0.1.0.jar`).

## Installing into your GTNH client

1. Build (above), or grab the jar from `build/libs/`. Use the plain `gtnhmcp-<version>.jar` (the `-dev` and `-sources`
   jars are for development).
2. Drop it into your GTNH instance's `mods/` folder.
3. Launch the client. On first run a config file is written (see below) and the server starts listening on
   `127.0.0.1:25590`. The log line `GTNH MCP server listening on http://127.0.0.1:25590/mcp` confirms it.

## Connecting Claude

With the game running:

```bash
claude mcp add --transport http gtnh http://127.0.0.1:25590/mcp
```

(If you set an auth token in the config, add `--header "Authorization: Bearer <token>"`.) Then just ask, for example:

- *"I'm looking at my EBF and it won't turn on — diagnose it."*  → `diagnose_multiblock`
- *"My Large Chemical Reactor won't form. What's wrong with the structure?"* → `diagnose_multiblock`, then `scan_blocks`
  around the controller to find the misplaced/missing block.
- *"What am I pointing at?"* → `get_target`
- *"Do I have the soldering iron on me?"* → `get_inventory`

The tools only return data while you're in a world; at the main menu they report that no world is loaded.

## Configuration

Written to `config/gtnhmcp.cfg` in your instance on first launch:

| Option | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch for the embedded server. |
| `bindHost` | `127.0.0.1` | Host to bind. **Keep this loopback** — changing it exposes game state to your network. |
| `port` | `25590` | TCP port; endpoint is `http://<bindHost>:<port>/mcp`. |
| `authToken` | *(empty)* | When set, clients must send `Authorization: Bearer <token>`. |
| `httpWorkerThreads` | `2` | HTTP request worker threads. |
| `clientThreadTimeoutMs` | `5000` | How long a request waits for the client thread before returning a timeout error. |

## Security

- Binds to `127.0.0.1` only — not reachable from other machines.
- All tools are **read-only**; the mod never modifies your world, inventory, or any machine.
- Optional bearer token for defense in depth.

## Limitations

- Some GregTech values (structure-formed flag, maintenance, progress, efficiency) are synced to the client most reliably
  while the controller's **GUI is open**. `diagnose_multiblock` includes a `freshnessNote` saying so; if a value looks
  stale, open the controller GUI once and re-run.
- The energy figures in `diagnose_multiblock` are the controller tile's own buffer; a multiblock's real energy lives in
  its energy hatches and may read as `0`. `scan_blocks` + the scanner lines give the fuller picture.

## Project layout

- `mcp/json` — a tiny, dependency-free JSON parser/writer (so the protocol layer is testable without a JSON library).
- `mcp/protocol` — the MCP/JSON-RPC dispatcher, tool interface, registry, and argument validation (no Minecraft deps).
- `mcp/http` — the loopback HTTP server (`com.sun.net.httpserver`).
- `mcp/tools` — the five read-only tools and their JSON schemas.
- `client` — `ClientThreadExecutor` (main-thread marshalling), `GameIntrospector` (vanilla reads), `MultiblockInspector`
  + `Reflect` (GregTech-by-reflection).

Unit tests cover the JSON codec, the protocol dispatcher, argument validation, the reflection-based diagnosis (against a
fake GregTech-shaped object), and the HTTP transport (a real loopback round-trip). Run them with `./gradlew test`.

# Changelog

## 1.4.0 — Sorting, links, facades and polish

**Control**
- **Distribution modes** on every Input end: *Round-robin*, *Nearest first* or *Random*.
- **Exact match** toggle (book button) on every filter: off ignores enchantments, damage and contents; on must match exactly.
- **Reaper Tag Filter:** goes in any filter slot and matches by rule: `#c:ingots`, `#c:ores/*`, `@mekanism`, `*_log`. Shows what it matches while you type. Works for fluids too.
- **Pipe overview:** sneak-click a pipe with an empty hand to see every end of that block at once. Click an end to open it, right-click to switch Input / Output. End screens opened from here get a Back button.
- **Reaper Imprint:** sneak-click an end to copy its settings, click other ends to paste them.

**New items and blocks**
- **Soul Link:** joins two pipes in the same dimension into one network with no pipes in between. Reusable. Servers can limit its range (`soulLinkRange`).
- **Facades:** sneak-right-click a pipe with a full block to hide the pipe inside it. The wrench takes the block back.
- **Reaper Field Guide:** a 10-page in-game book.

**Polish**
- Pipes that are moving something show a subtle flowing glow. It's part of the chunk mesh, so it costs nothing per frame.
- **Jade** and **WTHIT** tooltips: strands, and the settings of the end you're looking at.
- **JEI** info pages for every item.
- **Server speeds sync to players:** the screens now show the server's configured speeds, not your local config.

**Grim Soul Industries**
- With Grim Soul Industries installed: craft the Soulforged card from a Grim Soul speed card, and the Soul Link from a Reaper's Lantern.
- Liquid Experience from XP Tanks moves through Fluid and Omni pipes (checked on a real server).

**Compatibility**
- Checked on a real server with Create, AE2, Refined Storage, Mekanism and Grim Soul Industries loaded together: items, fluids and energy go in and out of their storage and machines.

## 1.3.1 — Wrench fix

- **Fixed:** sneak-clicking with the Reaper Wrench did nothing in-game, so you couldn't **disconnect a pipe end from a block you don't want it attached to**, or take a single strand out of a bundle. Minecraft skips a block's click while you sneak with an item unless the item allows it; the wrench now does, on pipes only.
- Wrenches from other mods (`c:tools/wrench`) get the same sneak-click behavior on pipes.
- New tests click through Minecraft's real sneak rule, so this can't silently break again.

## 1.3.0 — Redstone, priority and output filters

- **Redstone control on Input ends:** Always on, Needs signal, or Stops with signal. Applies to pulling and to anything pushed into the end.
- **Output ends have their own screen** (empty-hand right-click): a **priority** number (higher fills first, equal priorities share round-robin; Shift for ±10) and a **Whitelist / Blacklist** filter for items and fluids.
- Priority also applies to energy and chemicals.
- **Mekanism support verified** on a real server with Mekanism 10.7: hydrogen through the Chemical Pipe and oxygen through the Omni Pipe, tank to tank.

## 1.2.0 — First public release

**Pipes**
- Five pipes: Reaper Item, Fluid, Energy, Chemical (Mekanism, optional) and Omni (all four on one pipe).
- Stack any mix of them in one block. Every strand connects to the same face of a block on its own.
- One uniform casing size for every pipe; shared strands show as neat colored bands, so bundles line up with single pipes.
- Pipes store nothing and move items instantly, round-robin across all outputs.
- Works with anything using NeoForge item, fluid and energy capabilities, plus Mekanism chemicals.

**Reaper Wrench**
- Click an end to switch Output / Input, sneak-click to disconnect, click a core to reconnect, sneak-click a core to remove one strand.
- Targets the exact band you click. Wrenches from other mods (`c:tools/wrench`) work too.

**Input ends**
- Empty-hand right-click an Input end to open its screen: speed card slot, 9-slot filter, Whitelist / Blacklist / Void.
- Buckets filter fluids. Filters also apply to things machines push into the pipe.
- Reaper, Vanilla and Dark screen styles, like Grim Soul Industries.

**Speed cards**
- Whispering, Restless, Wraithbound and Soulforged cards, only needed in Input ends, never in the pipes in between.
- The card's tier color shows on the end plate.

**Performance**
- Pipe blocks never tick; one manager per world runs whole networks, cached and rebuilt only where something changed.
- Idle ends back off automatically; pipes are part of the chunk mesh, so there is no per-frame cost.
- About 0.03 ms per tick for a 1,024-pipe network with 32 ends.

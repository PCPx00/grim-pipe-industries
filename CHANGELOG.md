# Changelog

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

package com.xtri6.grimpipes.pipe;

import java.util.EnumSet;
import java.util.Set;

/**
 * The five pipe strands. Several different types can share one block (a "bundle"); each type forms its
 * own networks and only links to the same type. The Omni pipe carries every resource kind on one strand.
 */
public enum PipeType {
    ITEM("item", EnumSet.of(ResourceKind.ITEM)),
    FLUID("fluid", EnumSet.of(ResourceKind.FLUID)),
    ENERGY("energy", EnumSet.of(ResourceKind.ENERGY)),
    CHEMICAL("chemical", EnumSet.of(ResourceKind.CHEMICAL)),
    OMNI("omni", EnumSet.allOf(ResourceKind.class));

    public static final PipeType[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String name;
    private final Set<ResourceKind> kinds;

    PipeType(String name, Set<ResourceKind> kinds) {
        this.name = name;
        this.kinds = kinds;
    }

    public String id() {
        return name;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean carries(ResourceKind kind) {
        return kinds.contains(kind);
    }

    public Set<ResourceKind> kinds() {
        return kinds;
    }

    public boolean isIn(int mask) {
        return (mask & bit()) != 0;
    }

    public static PipeType byIndex(int i) {
        return VALUES[Math.floorMod(i, COUNT)];
    }
}

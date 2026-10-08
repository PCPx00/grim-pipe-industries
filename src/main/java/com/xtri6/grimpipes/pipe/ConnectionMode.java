package com.xtri6.grimpipes.pipe;

/**
 * How one strand treats one side of its block.
 * <ul>
 *     <li>OUTPUT (default): the pipe delivers into the block on that side.</li>
 *     <li>INPUT: the pipe pulls out of the block on that side (and accepts anything the block pushes in).</li>
 *     <li>DISABLED: no connection on that side, even to another pipe.</li>
 * </ul>
 */
public enum ConnectionMode {
    OUTPUT,
    INPUT,
    DISABLED;

    public static final ConnectionMode[] VALUES = values();

    public static ConnectionMode byIndex(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : OUTPUT;
    }
}

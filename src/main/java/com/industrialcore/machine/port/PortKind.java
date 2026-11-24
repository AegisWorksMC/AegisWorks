package com.industrialcore.machine.port;

/**
 * What a port is meant to connect.
 *
 * <p>The kind describes a <em>connector family</em>, not a capability: {@link #ANY} accepts every
 * cable or pipe, while {@link #POWER}, {@link #ITEM} and {@link #FLUID} only accept their own
 * family. The registry uses this for lookups and for the socket that is drawn around the port; the
 * transport itself keeps using the NeoForge capability queries, so existing factories never lose a
 * connection because of a missing port declaration.</p>
 */
public enum PortKind {
    /** Accepts any connector; used by machines whose face serves several systems. */
    ANY,
    POWER,
    ITEM,
    FLUID;

    /** Whether a connector of {@code connector} may sit on this port. */
    public boolean accepts(PortKind connector) {
        return this == ANY || connector == ANY || this == connector;
    }
}

package com.industrialcore.client;

/**
 * What a pipe or cable arm is meeting on one face.
 *
 * <p>The connection boolean in the block state already says <em>whether</em> a face connects (it is
 * capability driven, so it is true for chests, furnaces and other mods as well as for our
 * machines). This enum only answers <em>what kind of surface</em> the arm ends on, which decides
 * how the connector is drawn:</p>
 *
 * <ul>
 *   <li>{@link #PIPE} - another pipe, cable or conveyor. The two block models already meet at the
 *       boundary, so no connector is drawn.</li>
 *   <li>{@link #PORT} - an Industrial Core machine that declares a port on that face. The connector
 *       aims at the port centre, which may be shifted inside the face, and never enters the
 *       machine.</li>
 *   <li>{@link #FLUSH} - anything else that accepts a connection: a chest, a furnace, a hopper or a
 *       machine from another mod. The connector sits on the face centre and lies flat on the
 *       surface, so it fits a block whose model knows nothing about pipes.</li>
 * </ul>
 */
public enum ConnectorKind {
    PIPE,
    PORT,
    FLUSH
}

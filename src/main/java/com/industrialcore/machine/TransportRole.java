package com.industrialcore.machine;

/**
 * The two roles a transporter can have on its channel (Phases 26-29).
 *
 * <p>A device has exactly one role at a time. That single rule is what keeps the power network
 * acyclic: every channel edge runs SEND -> RECEIVE, and a device can never be both ends of an
 * edge, so FE can never circulate inside the channel network. A player who wants to reverse the
 * direction has to reconfigure one of the two devices, which breaks the old edge before the new
 * one exists.</p>
 */
public enum TransportRole {
    /** The device takes items/FE out of its own buffer and sends them to one receiver. */
    SEND,
    /** The device accepts channel transfers into its own buffer; pipes drain it. */
    RECEIVE;

    public TransportRole opposite() {
        return this == SEND ? RECEIVE : SEND;
    }
}

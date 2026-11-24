package com.industrialcore.level;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Common contract for electric machines that track their placing player ({@code ownerUuid})
 * for the Phase 84 machine level system.
 */
public interface IOwnedMachine {
    /** Placing player's UUID, or {@code null} for unowned/test/command-placed machines. */
    @Nullable
    UUID getOwnerUuid();

    void setOwnerUuid(@Nullable UUID ownerUuid);

    /** Skill-tree machine id (e.g. {@code "crusher"}, {@code "quarry"}, {@code "crafter"}). */
    String getMachineProgressId();
}

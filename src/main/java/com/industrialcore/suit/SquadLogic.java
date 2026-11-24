package com.industrialcore.suit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Phase 100: Pure-Java proximity squad selection and telemetry math for the tactical
 * Virtual Aegis AR HUD (self + up to 2 nearby teammates within 96 blocks in the same dimension).
 */
public final class SquadLogic {
    public static final double MAX_SQUAD_RADIUS = 96.0D;
    public static final double MAX_SQUAD_RADIUS_SQ = MAX_SQUAD_RADIUS * MAX_SQUAD_RADIUS;
    public static final int MAX_NEARBY_MATES = 2;

    public record SquadCandidate(
            UUID uuid,
            String name,
            String dimensionId,
            double x,
            double y,
            double z,
            float hp,
            float maxHp,
            boolean suitEquipped,
            long suitEnergy,
            long suitCapacity
    ) {}

    public record SquadMember(
            UUID uuid,
            String name,
            float hp,
            float maxHp,
            boolean suitEquipped,
            long suitEnergy,
            long suitCapacity,
            double x,
            double y,
            double z
    ) {
        public float hpRatio() {
            if (maxHp <= 0.0F) {
                return 0.0F;
            }
            return Math.max(0.0F, Math.min(1.0F, hp / maxHp));
        }

        public double energyRatio() {
            if (!suitEquipped || suitCapacity <= 0L) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, (double) suitEnergy / (double) suitCapacity));
        }

        public double distanceTo(double sx, double sy, double sz) {
            double dx = x - sx;
            double dy = y - sy;
            double dz = z - sz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private SquadLogic() {}

    /**
     * Selects up to {@link #MAX_NEARBY_MATES} (2) closest other players in the same dimension
     * within {@link #MAX_SQUAD_RADIUS} (96 blocks), sorted ascending by distance (UUID tie-break).
     */
    public static List<SquadMember> selectNearbySquad(UUID selfId,
                                                      String selfDimension,
                                                      double selfX,
                                                      double selfY,
                                                      double selfZ,
                                                      List<SquadCandidate> candidates) {
        if (selfId == null || selfDimension == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        record Scored(SquadMember member, double distSq) {}
        List<Scored> matches = new ArrayList<>();
        for (SquadCandidate c : candidates) {
            if (c == null || c.uuid() == null || c.uuid().equals(selfId)) {
                continue;
            }
            if (!selfDimension.equals(c.dimensionId())) {
                continue;
            }
            double dx = c.x() - selfX;
            double dy = c.y() - selfY;
            double dz = c.z() - selfZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > MAX_SQUAD_RADIUS_SQ) {
                continue;
            }
            float safeMaxHp = Math.max(1.0F, c.maxHp());
            float safeHp = Math.max(0.0F, Math.min(safeMaxHp, c.hp()));
            long safeCap = c.suitEquipped() ? Math.max(0L, c.suitCapacity()) : 0L;
            long safeEnergy = c.suitEquipped() ? Math.max(0L, Math.min(safeCap, c.suitEnergy())) : 0L;
            SquadMember member = new SquadMember(
                    c.uuid(),
                    c.name() == null ? "Player" : c.name(),
                    safeHp,
                    safeMaxHp,
                    c.suitEquipped(),
                    safeEnergy,
                    safeCap,
                    c.x(),
                    c.y(),
                    c.z());
            matches.add(new Scored(member, distSq));
        }

        matches.sort(Comparator.comparingDouble(Scored::distSq)
                .thenComparing(s -> s.member().uuid()));

        int limit = Math.min(MAX_NEARBY_MATES, matches.size());
        List<SquadMember> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            out.add(matches.get(i).member());
        }
        return List.copyOf(out);
    }
}

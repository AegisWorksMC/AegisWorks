package com.industrialcore.raid;

import com.industrialcore.raid.RiftArenaLogic.PillarSpec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Phase 120: Lightweight procedural builder for the enemy-favored floating Void Arena
 * inside {@code industrialcore:rift_arena} centered at {@code (centerX, ARENA_Y, 0)}:
 * <ul>
 *   <li>Central obsidian &amp; energy-inlay combat core (radius 11).</li>
 *   <li>4 narrow 4-block-wide stone bridge corridors (N/S/E/W chokepoints favoring {@code phase_juggernaut}).</li>
 *   <li>Outer obsidian walkway ring (radius 23..28) spanning the open void abyss.</li>
 *   <li>4 elevated sniper pillars (+6..+9 height at NE/NW/SE/SW) favoring {@code void_sentinel} &amp; Boss Anchor Crystals.</li>
 * </ul>
 */
public final class RiftArenaGenerator {
    private RiftArenaGenerator() {}

    public static void generateArena(ServerLevel level, int centerX, int centerY, int centerZ) {
        BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
        BlockState crying = Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockState blackstone = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState trim = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        BlockState energy = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        int rMax = RiftArenaLogic.ARENA_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. Clear airspace and lay central deck (r <= 11), 4 narrow 4-wide bridges (|x|<=2 or |z|<=2),
        //    and outer ring (23 <= r <= 28) over the open void abyss
        for (int dx = -rMax; dx <= rMax; dx++) {
            for (int dz = -rMax; dz <= rMax; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > rMax + 0.5D) {
                    continue;
                }
                boolean inCenter = dist <= 11.4D;
                boolean inOuterRing = dist >= 22.8D && dist <= rMax + 0.2D;
                boolean inBridge = dist > 11.0D && dist < 23.2D
                        && ((Math.abs(dx) <= 1 && Math.abs(dz) >= 11) || (Math.abs(dz) <= 1 && Math.abs(dx) >= 11)
                            || (dx >= -2 && dx <= 1 && Math.abs(dz) >= 11) || (dz >= -2 && dz <= 1 && Math.abs(dx) >= 11));

                if (!inCenter && !inOuterRing && !inBridge) {
                    continue;
                }

                // Clear vertical column above platform (y+1..y+12)
                for (int dy = 1; dy <= 12; dy++) {
                    pos.set(centerX + dx, centerY + dy, centerZ + dz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2);
                    }
                }

                // Solid obsidian underside support (y-1)
                pos.set(centerX + dx, centerY - 1, centerZ + dz);
                level.setBlock(pos, obsidian, 2);

                // Arena floor surface (y) with glowing purple/cyan energy-line geometry
                BlockState floor;
                if (inCenter && dist <= 2.4D) {
                    floor = (dx == 0 && dz == 0) ? energy : amethyst;
                } else if (Math.abs(dist - 10.5D) < 0.75D || Math.abs(dist - 23.4D) < 0.75D || Math.abs(dist - 27.6D) < 0.75D) {
                    floor = ((dx + dz) & 3) == 0 ? energy : crying;
                } else if (inBridge && (dx == -2 || dx == 1 || dz == -2 || dz == 1)) {
                    floor = ((dx + dz) & 1) == 0 ? crying : trim;
                } else if (dx == 0 || dz == 0) {
                    floor = ((Math.abs(dx) + Math.abs(dz)) % 4 == 0) ? energy : crying;
                } else {
                    floor = ((dx + dz) & 1) == 0 ? blackstone : obsidian;
                }
                pos.set(centerX + dx, centerY, centerZ + dz);
                level.setBlock(pos, floor, 2);
            }
        }

        // 2. Build the 4 elevated sniper pillars (+6..+9 height) with 3x3 obsidian/amethyst tops
        for (PillarSpec p : RiftArenaLogic.SNIPER_PILLARS) {
            int px = centerX + p.dx();
            int pz = centerZ + p.dz();
            for (int dy = -2; dy <= p.height(); dy++) {
                int y = centerY + dy;
                boolean isTop = (dy == p.height());
                int span = isTop ? 2 : 1;
                for (int ox = -span; ox <= span; ox++) {
                    for (int oz = -span; oz <= span; oz++) {
                        if (isTop && Math.abs(ox) == 2 && Math.abs(oz) == 2) {
                            continue;
                        }
                        pos.set(px + ox, y, pz + oz);
                        BlockState b;
                        if (isTop) {
                            b = (ox == 0 && oz == 0) ? energy : ((Math.abs(ox) + Math.abs(oz) <= 1) ? amethyst : crying);
                        } else if (ox == 0 && oz == 0) {
                            b = obsidian;
                        } else {
                            b = (dy % 3 == 0) ? crying : blackstone;
                        }
                        level.setBlock(pos, b, 2);
                    }
                }
            }
        }
    }
}

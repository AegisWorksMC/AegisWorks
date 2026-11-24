package com.industrialcore.client;

import com.industrialcore.machine.ConnectorStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;

/**
 * Draws the connector that sits between a pipe/cable arm and whatever it meets.
 *
 * <p>The block model ends an arm flat on the block boundary and knows nothing about the neighbour.
 * That is deliberate: a machine keeps its own design, a chest keeps its own model, and the arm has
 * the same plain tube in every case. This class adds the few quads that turn the tube end into a
 * finished joint, and it changes shape with the {@link ConnectorKind}:</p>
 *
 * <ul>
 *   <li>against another pipe ({@link ConnectorKind#PIPE}) nothing is drawn - the two models meet,</li>
 *   <li>against a machine port ({@link ConnectorKind#PORT}) the joint bends to the port centre,
 *       which may be shifted inside the face, and stops on the machine surface,</li>
 *   <li>against any other connectable block ({@link ConnectorKind#FLUSH}) the joint is centred and
 *       lies on the surface.</li>
 * </ul>
 *
 * <p>The active {@link ConnectorStyle} decides the shape and whether anything crosses the boundary.
 * All three styles emit their geometry directly into the vertex consumer: no buffers, no
 * allocation, and no state kept between frames.</p>
 */
public final class ConnectorJoint {
    /** Start the sleeve a hair before the boundary so it cannot z-fight with the arm end face. */
    private static final double START_OVERLAP = 0.002;
    /** Inset of the accent bezel from the plate edge (1 model pixel). */
    private static final double BEZEL_INSET = 1.0 / 16.0;
    /** Width of the accent bezel line (0.25 model pixel). */
    private static final double BEZEL_WIDTH = 0.25 / 16.0;
    /** How far the bend of an offset joint dips back into the connector's own block. */
    private static final double SLEEVE_INSET = 0.085;
    /** Below this in-plane offset the joint counts as centred and stays straight. */
    private static final double OFFSET_EPSILON = 1.0e-4;
    /** Corner signs for the four corners of a cross-section. */
    private static final double[][] SIGNS = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
    /** Scratch for the two cross-sections of a sleeve. */
    private static final double[] START_CORNERS = new double[12];
    private static final double[] END_CORNERS = new double[12];
    private static final double[] MID_CORNERS = new double[12];
    /** Scratch for the eight corners of a connector box; rendering is single threaded. */
    private static final double[][] BOX_CORNERS = new double[8][3];
    /** Depth pair reused by {@link #box} so no array is allocated per face. */
    private static final double[] DEPTHS = new double[2];

    private ConnectorJoint() {
    }

    /**
     * Emits the joint of one face.
     *
     * @param direction face the arm leaves through
     * @param kind      what the arm meets on that face
     * @param anchorX   joint centre on the boundary plane, in this block's space
     * @param armHalf   half width of the arm, in block units
     * @param style     active connector design
     */
    public static void draw(VertexConsumer consumer, PoseStack.Pose pose, Direction direction,
                            ConnectorKind kind, double anchorX, double anchorY, double anchorZ,
                            double armHalf, ConnectorStyle style,
                            float red, float green, float blue, float alpha) {
        if (kind == ConnectorKind.PIPE) {
            return;
        }
        double nx = direction.getStepX();
        double ny = direction.getStepY();
        double nz = direction.getStepZ();

        // Where the baked arm ends: centred on the face, just inside the boundary.
        double sx = 0.5 + nx * (0.5 - START_OVERLAP);
        double sy = 0.5 + ny * (0.5 - START_OVERLAP);
        double sz = 0.5 + nz * (0.5 - START_OVERLAP);

        double offset = Math.abs(anchorX - sx) + Math.abs(anchorY - sy) + Math.abs(anchorZ - sz);
        if (offset > OFFSET_EPSILON) {
            drawSleeve(consumer, pose, direction, sx, sy, sz, anchorX, anchorY, anchorZ,
                    armHalf, red, green, blue, alpha);
        }

        double plateHalf = armHalf + style.plateExtra();
        box(consumer, pose, direction, anchorX, anchorY, anchorZ, plateHalf, plateHalf,
                -style.innerDepth(), style.outerDepth(), red, green, blue, alpha);

        // 1 px accent bezel inlaid just inside the plate edge: the one bright line that makes the
        // pad read as a socket instead of a plain plate. It never leaves the plate.
        double bezelHalf = plateHalf - BEZEL_INSET;
        if (bezelHalf > 0.0D) {
            ring(consumer, pose, direction, anchorX, anchorY, anchorZ,
                    bezelHalf, BEZEL_WIDTH, bezelHalf + BEZEL_WIDTH * 0.5D,
                    style.outerDepth() - 0.004D, style.outerDepth() + 0.004D,
                    style.accentRed(), style.accentGreen(), style.accentBlue(), alpha);
        }

        if (style.frameExtra() > 0.0D) {
            double frameHalf = armHalf + style.frameExtra();
            double band = (frameHalf - plateHalf) * 0.5D;
            double centre = (frameHalf + plateHalf) * 0.5D;
            // Four bars of the accent frame, all on the outer plane of the joint.
            frameBar(consumer, pose, direction, anchorX, anchorY, anchorZ,
                    centre, band, frameHalf, style, alpha);
        }
    }

    /** Two segments: out of the arm, then sideways on the boundary plane to the port centre. */
    private static void drawSleeve(VertexConsumer consumer, PoseStack.Pose pose, Direction direction,
                                   double sx, double sy, double sz,
                                   double ex, double ey, double ez,
                                   double armHalf,
                                   float red, float green, float blue, float alpha) {
        // The bend dips into our own block, so the sleeve never enters the neighbour.
        double mx = (sx + ex) * 0.5D - direction.getStepX() * SLEEVE_INSET;
        double my = (sy + ey) * 0.5D - direction.getStepY() * SLEEVE_INSET;
        double mz = (sz + ez) * 0.5D - direction.getStepZ() * SLEEVE_INSET;

        double px;
        double py;
        double pz;
        double qx;
        double qy;
        double qz;
        switch (direction.getAxis()) {
            case X -> {
                px = 0.0D;
                py = 1.0D;
                pz = 0.0D;
                qx = 0.0D;
                qy = 0.0D;
                qz = 1.0D;
            }
            case Y -> {
                px = 1.0D;
                py = 0.0D;
                pz = 0.0D;
                qx = 0.0D;
                qy = 0.0D;
                qz = 1.0D;
            }
            default -> {
                px = 1.0D;
                py = 0.0D;
                pz = 0.0D;
                qx = 0.0D;
                qy = 1.0D;
                qz = 0.0D;
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = SIGNS[i][0] * armHalf;
            double b = SIGNS[i][1] * armHalf;
            START_CORNERS[i * 3] = sx + px * a + qx * b;
            START_CORNERS[i * 3 + 1] = sy + py * a + qy * b;
            START_CORNERS[i * 3 + 2] = sz + pz * a + qz * b;
            MID_CORNERS[i * 3] = mx + px * a + qx * b;
            MID_CORNERS[i * 3 + 1] = my + py * a + qy * b;
            MID_CORNERS[i * 3 + 2] = mz + pz * a + qz * b;
            END_CORNERS[i * 3] = ex + px * a + qx * b;
            END_CORNERS[i * 3 + 1] = ey + py * a + qy * b;
            END_CORNERS[i * 3 + 2] = ez + pz * a + qz * b;
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            quad(consumer, pose,
                    START_CORNERS[i * 3], START_CORNERS[i * 3 + 1], START_CORNERS[i * 3 + 2],
                    START_CORNERS[j * 3], START_CORNERS[j * 3 + 1], START_CORNERS[j * 3 + 2],
                    MID_CORNERS[j * 3], MID_CORNERS[j * 3 + 1], MID_CORNERS[j * 3 + 2],
                    MID_CORNERS[i * 3], MID_CORNERS[i * 3 + 1], MID_CORNERS[i * 3 + 2],
                    red, green, blue, alpha);
            quad(consumer, pose,
                    MID_CORNERS[i * 3], MID_CORNERS[i * 3 + 1], MID_CORNERS[i * 3 + 2],
                    MID_CORNERS[j * 3], MID_CORNERS[j * 3 + 1], MID_CORNERS[j * 3 + 2],
                    END_CORNERS[j * 3], END_CORNERS[j * 3 + 1], END_CORNERS[j * 3 + 2],
                    END_CORNERS[i * 3], END_CORNERS[i * 3 + 1], END_CORNERS[i * 3 + 2],
                    red, green, blue, alpha);
        }
    }

    /**
     * Emits the accent frame: four bars around the plate, lying on the outer plane of the joint so
     * the ring reads as an inlaid line rather than another collar.
     */
    private static void frameBar(VertexConsumer consumer, PoseStack.Pose pose, Direction direction,
                                 double cx, double cy, double cz, double centre, double band,
                                 double frameHalf, ConnectorStyle style,
                                 float alpha) {
        ring(consumer, pose, direction, cx, cy, cz, centre, band, frameHalf,
                style.frameInner(), style.frameOuter(),
                style.accentRed(), style.accentGreen(), style.accentBlue(), alpha);
    }

    /**
     * Four bars forming one square outline in the face plane, centred on {@code centre} and
     * {@code band} wide, spanning {@code near..far} along the outward normal.
     */
    private static void ring(VertexConsumer consumer, PoseStack.Pose pose, Direction direction,
                             double cx, double cy, double cz, double centre, double band,
                             double frameHalf, double near, double far,
                             float accentRed, float accentGreen, float accentBlue, float alpha) {
        // Two bars along the first cross axis, two along the second.
        switch (direction.getAxis()) {
            case X -> {
                box(consumer, pose, direction, cx, cy + centre, cz, band, frameHalf, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy - centre, cz, band, frameHalf, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy, cz + centre, frameHalf, band, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy, cz - centre, frameHalf, band, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
            }
            case Y -> {
                box(consumer, pose, direction, cx + centre, cy, cz, band, frameHalf, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx - centre, cy, cz, band, frameHalf, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy, cz + centre, frameHalf, band, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy, cz - centre, frameHalf, band, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
            }
            default -> {
                box(consumer, pose, direction, cx + centre, cy, cz, band, frameHalf, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx - centre, cy, cz, band, frameHalf, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy + centre, cz, frameHalf, band, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
                box(consumer, pose, direction, cx, cy - centre, cz, frameHalf, band, near, far,
                        accentRed, accentGreen, accentBlue, alpha);
            }
        }
    }

    /**
     * One box centred on {@code (cx, cy, cz)} with the given half extents in the face plane, spanning
     * {@code depthNear..depthFar} along the outward normal (negative reaches into our own block).
     */
    private static void box(VertexConsumer consumer, PoseStack.Pose pose, Direction direction,
                            double cx, double cy, double cz, double halfA, double halfB,
                            double depthNear, double depthFar,
                            float red, float green, float blue, float alpha) {
        double nx = direction.getStepX();
        double ny = direction.getStepY();
        double nz = direction.getStepZ();
        double px;
        double py;
        double pz;
        double qx;
        double qy;
        double qz;
        switch (direction.getAxis()) {
            case X -> {
                px = 0.0D;
                py = 1.0D;
                pz = 0.0D;
                qx = 0.0D;
                qy = 0.0D;
                qz = 1.0D;
            }
            case Y -> {
                px = 1.0D;
                py = 0.0D;
                pz = 0.0D;
                qx = 0.0D;
                qy = 0.0D;
                qz = 1.0D;
            }
            default -> {
                px = 1.0D;
                py = 0.0D;
                pz = 0.0D;
                qx = 0.0D;
                qy = 1.0D;
                qz = 0.0D;
            }
        }
        double[][] corners = BOX_CORNERS;
        DEPTHS[0] = depthNear;
        DEPTHS[1] = depthFar;
        int index = 0;
        for (int d = 0; d < 2; d++) {
            double depth = DEPTHS[d];
            double bx = cx + nx * depth;
            double by = cy + ny * depth;
            double bz = cz + nz * depth;
            for (double[] sign : SIGNS) {
                corners[index][0] = bx + px * sign[0] * halfA + qx * sign[1] * halfB;
                corners[index][1] = by + py * sign[0] * halfA + qy * sign[1] * halfB;
                corners[index][2] = bz + pz * sign[0] * halfA + qz * sign[1] * halfB;
                index++;
            }
        }
        // Near face (towards our block), far face (towards the neighbour) and the four sides.
        face(consumer, pose, corners, 0, 1, 2, 3, red, green, blue, alpha);
        face(consumer, pose, corners, 7, 6, 5, 4, red, green, blue, alpha);
        face(consumer, pose, corners, 4, 5, 1, 0, red, green, blue, alpha);
        face(consumer, pose, corners, 5, 6, 2, 1, red, green, blue, alpha);
        face(consumer, pose, corners, 6, 7, 3, 2, red, green, blue, alpha);
        face(consumer, pose, corners, 7, 4, 0, 3, red, green, blue, alpha);
    }

    private static void face(VertexConsumer consumer, PoseStack.Pose pose, double[][] corners,
                             int a, int b, int c, int d,
                             float red, float green, float blue, float alpha) {
        quad(consumer, pose,
                corners[a][0], corners[a][1], corners[a][2],
                corners[b][0], corners[b][1], corners[b][2],
                corners[c][0], corners[c][1], corners[c][2],
                corners[d][0], corners[d][1], corners[d][2],
                red, green, blue, alpha);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz,
                             float red, float green, float blue, float alpha) {
        consumer.addVertex(pose, (float) ax, (float) ay, (float) az)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(pose, (float) bx, (float) by, (float) bz)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(pose, (float) cx, (float) cy, (float) cz)
                .setColor(red, green, blue, alpha);
        consumer.addVertex(pose, (float) dx, (float) dy, (float) dz)
                .setColor(red, green, blue, alpha);
    }
}

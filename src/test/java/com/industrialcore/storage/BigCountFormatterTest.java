package com.industrialcore.storage;

import com.industrialcore.storage.BigCountFormatter.ColorTier;
import com.industrialcore.storage.BigCountFormatter.UnitMode;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 89 unit tests for {@link BigCountFormatter}: EN/JP boundaries up to 10^68 (無量大数),
 * exact 3-digit comma formatting, and tier/prism/muryo color transitions.
 */
class BigCountFormatterTest {

    @Test
    void englishUnitBoundariesFormatCleanlyUpToMuryo() {
        assertEquals("0", BigCountFormatter.format(BigInteger.ZERO, UnitMode.ENGLISH));
        assertEquals("999", BigCountFormatter.format(BigInteger.valueOf(999), UnitMode.ENGLISH));
        assertEquals("1K", BigCountFormatter.format(BigInteger.valueOf(1_000), UnitMode.ENGLISH));
        assertEquals("65.5K", BigCountFormatter.format(BigInteger.valueOf(65_536), UnitMode.ENGLISH));
        assertEquals("10M", BigCountFormatter.format(BigInteger.valueOf(10_000_000), UnitMode.ENGLISH));
        assertEquals("1B", BigCountFormatter.format(BigInteger.TEN.pow(9), UnitMode.ENGLISH));
        assertEquals("100B", BigCountFormatter.format(BigInteger.valueOf(100_000_000_000L), UnitMode.ENGLISH));
        assertEquals("1T", BigCountFormatter.format(BigInteger.TEN.pow(12), UnitMode.ENGLISH));
        assertEquals("1Sx", BigCountFormatter.format(BigInteger.TEN.pow(21), UnitMode.ENGLISH));
        assertEquals("1Dc", BigCountFormatter.format(BigInteger.TEN.pow(33), UnitMode.ENGLISH));
        assertEquals("1Uvg", BigCountFormatter.format(BigInteger.TEN.pow(66), UnitMode.ENGLISH));
        assertEquals("1Muryo", BigCountFormatter.format(BigInteger.TEN.pow(68), UnitMode.ENGLISH));
        assertEquals("2.5Muryo", BigCountFormatter.format(
                BigInteger.TEN.pow(68).multiply(BigInteger.valueOf(25)).divide(BigInteger.TEN),
                UnitMode.ENGLISH));
    }

    @Test
    void japaneseUnitBoundariesFormatCleanlyUpToMuryoTaisu() {
        assertEquals("0", BigCountFormatter.format(BigInteger.ZERO, UnitMode.JAPANESE));
        assertEquals("999", BigCountFormatter.format(BigInteger.valueOf(999), UnitMode.JAPANESE));
        assertEquals("9999", BigCountFormatter.format(BigInteger.valueOf(9_999), UnitMode.JAPANESE));
        assertEquals("1万", BigCountFormatter.format(BigInteger.valueOf(10_000), UnitMode.JAPANESE));
        assertEquals("10億", BigCountFormatter.format(BigInteger.TEN.pow(9), UnitMode.JAPANESE));
        assertEquals("1兆", BigCountFormatter.format(BigInteger.TEN.pow(12), UnitMode.JAPANESE));
        assertEquals("1京", BigCountFormatter.format(BigInteger.TEN.pow(16), UnitMode.JAPANESE));
        assertEquals("10垓", BigCountFormatter.format(BigInteger.TEN.pow(21), UnitMode.JAPANESE));
        assertEquals("1澗", BigCountFormatter.format(BigInteger.TEN.pow(36), UnitMode.JAPANESE));
        assertEquals("1不可思議", BigCountFormatter.format(BigInteger.TEN.pow(64), UnitMode.JAPANESE));
        assertEquals("1無量大数", BigCountFormatter.format(BigInteger.TEN.pow(68), UnitMode.JAPANESE));
        assertEquals("1無量", BigCountFormatter.formatCompact(BigInteger.TEN.pow(68), UnitMode.JAPANESE));
    }

    @Test
    void exactFormattingInsertsThreeDigitCommasEvenForMuryo() {
        assertEquals("0", BigCountFormatter.formatExact(BigInteger.ZERO));
        assertEquals("999", BigCountFormatter.formatExact(BigInteger.valueOf(999)));
        assertEquals("1,000", BigCountFormatter.formatExact(BigInteger.valueOf(1_000)));
        assertEquals("1,234,567,890", BigCountFormatter.formatExact(BigInteger.valueOf(1_234_567_890L)));
        String muryoExact = BigCountFormatter.formatExact(BigInteger.TEN.pow(68));
        assertTrue(muryoExact.startsWith("100,000,000,"), "Unexpected muryo exact: " + muryoExact);
        assertEquals(69 + 22, muryoExact.length(), "10^68 has 69 digits + 22 commas");
    }

    @Test
    void colorTiersAndPrismMuryoAnimationsWorkInBothModes() {
        assertEquals(ColorTier.PLAIN, BigCountFormatter.colorTier(BigInteger.valueOf(999), UnitMode.ENGLISH));
        assertEquals(ColorTier.CYAN, BigCountFormatter.colorTier(BigInteger.valueOf(1_000), UnitMode.ENGLISH));
        assertEquals(ColorTier.EMERALD, BigCountFormatter.colorTier(BigInteger.TEN.pow(6), UnitMode.ENGLISH));
        assertEquals(ColorTier.GOLD, BigCountFormatter.colorTier(BigInteger.TEN.pow(9), UnitMode.ENGLISH));
        assertEquals(ColorTier.MAGENTA, BigCountFormatter.colorTier(BigInteger.TEN.pow(12), UnitMode.ENGLISH));
        assertEquals(ColorTier.SUNSET, BigCountFormatter.colorTier(BigInteger.TEN.pow(21), UnitMode.ENGLISH));
        assertEquals(ColorTier.PRISM, BigCountFormatter.colorTier(BigInteger.TEN.pow(33), UnitMode.ENGLISH));
        assertEquals(ColorTier.MURYO, BigCountFormatter.colorTier(BigInteger.TEN.pow(68), UnitMode.ENGLISH));

        assertEquals(ColorTier.PLAIN, BigCountFormatter.colorTier(BigInteger.valueOf(9_999), UnitMode.JAPANESE));
        assertEquals(ColorTier.CYAN, BigCountFormatter.colorTier(BigInteger.TEN.pow(4), UnitMode.JAPANESE));
        assertEquals(ColorTier.EMERALD, BigCountFormatter.colorTier(BigInteger.TEN.pow(8), UnitMode.JAPANESE));
        assertEquals(ColorTier.GOLD, BigCountFormatter.colorTier(BigInteger.TEN.pow(12), UnitMode.JAPANESE));
        assertEquals(ColorTier.MAGENTA, BigCountFormatter.colorTier(BigInteger.TEN.pow(16), UnitMode.JAPANESE));
        assertEquals(ColorTier.SUNSET, BigCountFormatter.colorTier(BigInteger.TEN.pow(20), UnitMode.JAPANESE));
        assertEquals(ColorTier.PRISM, BigCountFormatter.colorTier(BigInteger.TEN.pow(36), UnitMode.JAPANESE));
        assertEquals(ColorTier.MURYO, BigCountFormatter.colorTier(BigInteger.TEN.pow(68), UnitMode.JAPANESE));

        assertFalse(BigCountFormatter.isMuryo(BigInteger.TEN.pow(68).subtract(BigInteger.ONE)));
        assertTrue(BigCountFormatter.isMuryo(BigInteger.TEN.pow(68)));
        assertTrue(ColorTier.PRISM.isPrism() && !ColorTier.PRISM.isMuryo());
        assertTrue(ColorTier.MURYO.isPrism() && ColorTier.MURYO.isMuryo());

        int prism0 = BigCountFormatter.argbColor(BigInteger.TEN.pow(36), UnitMode.JAPANESE, 0L);
        int prism1 = BigCountFormatter.argbColor(BigInteger.TEN.pow(36), UnitMode.JAPANESE, 800L);
        assertNotEquals(prism0, prism1, "PRISM color must animate over time");
    }
}

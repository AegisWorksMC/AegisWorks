package com.industrialcore.machine;

import com.industrialcore.machine.VirtualUpgradeBank.SpecialCategory;

/**
 * Phase 113: Common server/client capability contract for any machine {@code BlockEntity}
 * supporting the unified 3-slot XP-linked Diminishing-Returns Virtual Upgrade Bank
 * ({@code motor}, {@code battery}, {@code specialization_module}).
 */
public interface IUpgradeableMachine {
    VirtualUpgradeBank getUpgradeBank();

    SpecialCategory getSpecialCategory();

    /** Machine ID tracked by {@code MachineSkillTree}, or registry path if passive/generator. */
    String getUpgradeMachineId();

    /** Count of legacy slot items (e.g. old Motor/Battery slots) already counted outside the bank. */
    default int getLegacyUpgradeCount() {
        return 0;
    }

    /** Effective total upgrade limit (4 at Lv.1 -&gt; 96 at Lv.99 -&gt; 1000 at Lv.1000 / Creative). */
    int getTotalUpgradeLimit();

    /** Hook invoked on server after upgrades change (e.g., to re-sync FE capacity). */
    default void onUpgradeBankChanged() {}
}

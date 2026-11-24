# AegisWorks

**AegisWorks** is a next-generation **Sci-Fi Industrial Automation, Tactical Factory Defense, and Machine Mastery RPG** mod for **Minecraft 1.21.1 (NeoForge)**.

Design living, high-detail industrial plants with exposed 3D moving machinery, scale up from conventional generators to multiblock Nuclear and Singularity Reactors, gear up in the Virtual Aegis powered armor with four modular electric weapons, and challenge tiered **Dimensional Rift Raids** inside a dedicated Void Arena dimension.

> **Current Version:** `0.1.3-beta` (Open Beta)  
> **Target Platform:** Minecraft `1.21.1` / NeoForge `21.1+` / Java `21`  
> **Mod ID:** `industrialcore` (preserved for full world & save compatibility)  
> **License:** [MIT License](LICENSE)

---

## Key Features

### Living Sci-Fi Industrial Automation
- **Unified Sci-Fi Design Language:** Exposed gunmetal frames, industrial amber safety accents, translucent smoked-glass chambers, and real-time 3D Block Entity animations (hydraulic metal presses, counter-rotating crusher rollers, robotic welding assemblers, wind/hydro turbines, and motion-synced audio).
- **4-Branch, 38-Machine Tech & Skill Tree:** Progress across Processing, Power & Reactors, Logistics & Quantum Storage, and Defense & Utility branches with interactive search, frontier highlighting, and 29 dedicated advancements.
- **Machine Mastery RPG & Infinite Upgrades:** Machines gain experience and levels as they work, pairing with **Motor**, **Battery**, and **Specialization** modules using a smooth diminishing-returns scaling curve.

### Multiblock Power & Quantum Storage
- **Variable Multiblock Reactors:** Construct `3x3x3`–`5x5x5` Water-Cooled Nuclear Reactors and `3x3x3`–`7x7x7` Singularity Reactors with an in-world **3D Layer-Slice Construction Guide**, risk-reward **Overdrive Mode**, and a dramatic convergence-to-shockwave startup ritual.
- **Infinite-Scale Quantum Storage:** Store massive item volumes across expandable Quantum Cores with full optional JEI crafting-grid transfer support.

### Virtual Aegis Powered Armor & Modular Electric Weapons
- **Virtual Aegis Exosuit:** FE-powered damage absorption, flight/mobility modules, and a 3D parallax AR HUD.
- **Four Modular Electric Weapons:** **Pulse Laser** (heat/charge beam with lock-on & prism chain), **Quantum Blade** (phase dash & armor-piercing melee), **Railgun** (high-velocity wave cannon), and **Energy Bow** (optical scope & multi-lock).

### Endgame: Dimensional Rift Raids (`industrialcore:rift_arena`)
- **Voluntary Tier 1–10 Boss Gauntlet (Zero Factory Griefing):** Launch challenges directly from a formed Singularity Reactor to teleport into an enemy-favored floating **Void Arena** with persistent safe-return coordinates, a live `MM:SS` countdown BossBar, and zero risk to your base.
- **Four Original Mecha-Style Dimensional Enemies with Clear Tactical Weaknesses:**
  - **Rift Stalker:** High-speed blink assassin vulnerable to rapid-hit Phase Jamming.
  - **Void Sentinel:** Hovering sniper pod with transforming 4-petal armor exposing a `2.5x` critical rail-cannon core.
  - **Phase Juggernaut:** Heavy bridge-blocker whose twin tower shields deflect `95%` of frontal ranged fire until shattered by a Quantum Blade Shield Break.
  - **Dimensional Sovereign:** Multi-phase endgame boss featuring orbiting Rift Anchor Crystals, Absolute Phase Barriers, crimson-gold Overdrive transformation, and custom 3D shockwave VFX & SFX.

### Modpack & Ecosystem Ready
- **Comprehensive TOML Balance Config:** Customize power generation, machine consumption, weapon damage scaling, and Dimensional Rift Raid difficulty/activation costs.
- **Optional Integrations (No Hard Dependencies):** Built-in soft support for **JEI** (custom recipe categories & Quantum Storage transfer) and **Jade** (live FE, progress, overclock, and clear one-word halt reasons).
- **Full Localization:** Complete English (`en_us`) and Japanese (`ja_jp`) translations.

---

## Building from Source

### Prerequisites
- **JDK 21** (Eclipse Temurin / Adoptium or equivalent)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/AegisWorksMC/AegisWorks.git
cd AegisWorks

# Compile, run the full unit test suite, and build the mod JAR
./gradlew build
```

On Windows PowerShell / Command Prompt:

```powershell
.\gradlew.bat build
```

The built JAR is output to `build/libs/industrialcore-<version>.jar` (and copied to `dist/`).  
Optional compile-only integrations (**JEI** from BlameJared Maven and **Jade** from Modrinth Maven) are resolved automatically by Gradle and are never bundled into the output JAR.

### Running Development Environments

```bash
./gradlew runClient   # Launch NeoForge dev client
./gradlew runServer   # Launch NeoForge dev server
./gradlew test        # Run JUnit 5 unit test suite
```

---

## Links & Community

- **Modrinth:** Coming soon *(link placeholder)*
- **CurseForge:** Coming soon *(link placeholder)*
- **Discord & Issue Tracker:** https://discord.gg/QKW9fhP2de

---

## Credits & License

Developed by **AegisWorks** (**Raitchi**).  
Licensed under the [MIT License](LICENSE). You are free to include **AegisWorks** in Modpacks and contribute or fork under the terms of the MIT License.

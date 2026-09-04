package example;

import arc.util.*;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.maps.*;
import mindustry.type.*;

import static mindustry.content.Planets.*;

public class CryonSectors {

    // 在这里声明你的 Sector
    // public static SectorPreset yourSector;

    public static void load() {
        Planet cryonPlanet = Vars.content.planet("cryon-cryon");
        if (cryonPlanet == null) {
            Log.warn("[CryonSectors] Cryon planet not found!");
            return;
        }

        registerSectors(cryonPlanet);
    }

    private static void registerSectors(Planet planet) {
        // ──────────────────────────────────────────────────────────
        // 示例：进攻图
        // ──────────────────────────────────────────────────────────
        // Sector sector = planet.sectors.get(300);
        // new SectorPreset("sector-name", planet, 300) {{
        //     difficulty = 8f;
        //     requireUnlock = false;
        //     credit = "YourName";
        // }};
        // sector.generateEnemyBase = true;

        // ──────────────────────────────────────────────────────────
        // 示例：生存图
        // ──────────────────────────────────────────────────────────
        // Sector sector = planet.sectors.get(301);
        // new SectorPreset("sector-name", planet, 301) {{
        //     difficulty = 5f;
        //     captureWave = 30;
        //     requireUnlock = false;
        //     credit = "YourName";
        // }};

        // ──────────────────────────────────────────────────────────
        // 扇区 38（进攻图）
        // ──────────────────────────────────────────────────────────
        Sector sector38 = planet.sectors.get(38);
        new SectorPreset("sector-38", planet, 38) {{
            sector.threat=8f;
            requireUnlock = false;
        }};
        sector38.generateEnemyBase = true;

    }
}
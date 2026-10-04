package riskod.world;

import arc.graphics.Color;
import mindustry.content.Planets;
import mindustry.game.Team;
import mindustry.graphics.g3d.HexMesh;
import mindustry.graphics.g3d.HexSkyMesh;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.type.Planet;
import riskod.content.RiskodCont;

/**
 * Visual planet only. Gameplay maps are {@link RiskodMaps} SectorPresets (premade .msav).
 * Numbered/auto-generated sectors cannot be landed on.
 */
public class RiskodPlanet {
    public static Planet riskod;
    public static Planet riskodMoon;

    public static void load() {
        riskod = new Planet("riskod", Planets.sun, 1.1f, 3) {{
            generator = new RiskodPlanetGenerator();
            meshLoader = () -> new HexMesh(this, 5);
            cloudMeshLoader = () -> new MultiMesh(
                    new HexSkyMesh(this, 2, 0.15f, 0.14f, 5, Color.valueOf("8da4e0").a(0.75f), 2, 0.42f, 1f, 0.43f),
                    new HexSkyMesh(this, 3, 0.6f, 0.15f, 5, Color.valueOf("b7c5ef").a(0.55f), 2, 0.42f, 1.2f, 0.45f)
            );

            launchCapacityMultiplier = 0.4f;
            sectorSeed = 7;
            allowWaves = false;
            allowSectorInvasion = false;
            allowLaunchSchematics = false;
            allowLaunchLoadout = false;
            allowLaunchToNumbered = false;
            enemyCoreSpawnReplace = true;
            clearSectorOnLose = true;
            tidalLock = false;
            defaultCore = RiskodCont.fakeCore;
            iconColor = Color.valueOf("6e85c7");
            atmosphereColor = Color.valueOf("3a4a7a");
            atmosphereRadIn = 0.02f;
            atmosphereRadOut = 0.28f;
            startSector = 0;
            alwaysUnlocked = true;
            landCloudColor = Color.valueOf("8da4e0");
            accessible = true;

            ruleSetter = r -> {
                r.waveTeam = Team.crux;
                r.placeRangeCheck = false;
                r.unitCap = 48;
                r.coreIncinerates = true;
                r.ghostBlocks = false;
                r.waves = true;
                r.waveTimer = false;
                r.winWave = 0;
                r.attackMode = true;
                r.enemyCoreBuildRadius = 0f;
                r.dropZoneRadius = 0f;
                r.coreDestroyClear = true;
                r.fog = false;
            };
        }};

        riskodMoon = new Planet("riskod-moon", riskod, 0.35f, 1) {{
            generator = new RiskodPlanetGenerator();
            meshLoader = () -> new HexMesh(this, 4);
            accessible = true;
            alwaysUnlocked = true;
            allowWaves = false;
            allowLaunchToNumbered = false;
            allowLaunchSchematics = false;
            allowLaunchLoadout = false;
            clearSectorOnLose = true;
            defaultCore = RiskodCont.fakeCore;
            iconColor = Color.valueOf("c8c4d8");
            atmosphereColor = Color.valueOf("222230");
            atmosphereRadIn = 0.01f;
            atmosphereRadOut = 0.12f;
            startSector = 0;
            tidalLock = true;
            orbitRadius = 5.2f;

            ruleSetter = r -> {
                r.waveTeam = Team.crux;
                r.waves = true;
                r.waveTimer = false;
                r.winWave = 0;
                r.attackMode = true;
                r.enemyCoreBuildRadius = 0f;
                r.unitCap = 48;
                r.fog = false;
            };
        }};
    }

    public static boolean onRiskod() {
        if (mindustry.Vars.state == null || mindustry.Vars.state.rules == null) return false;
        var sector = mindustry.Vars.state.rules.sector;
        if (sector == null || sector.planet == null) return false;
        return sector.planet == riskod || sector.planet == riskodMoon;
    }
}

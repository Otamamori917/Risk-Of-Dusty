package riskod.world;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.util.Tmp;
import arc.util.noise.Simplex;
import mindustry.content.Blocks;
import mindustry.maps.generators.PlanetGenerator;
import mindustry.world.Block;
import riskod.world.run.RunState;

/** Simple planet gen for Riskod sectors. */
public class RiskodPlanetGenerator extends PlanetGenerator {
    public RiskodPlanetGenerator() {
        baseSeed = 19;
    }

    @Override
    public boolean allowLanding(mindustry.type.Sector sector) {
        String runMap = RunState.runMap();
        if (runMap != null) {
            return sector.preset != null && sector.preset.name.equals(runMap);
        }
        return sector.preset != null && (sector.preset.alwaysUnlocked || sector.hasBase());
    }

    @Override
    public float getHeight(Vec3 position) {
        float noise = Simplex.noise3d(seed, 6, 0.5, 1.2, position.x, position.y, position.z);
        return Mathf.clamp((noise + 0.15f) * 0.55f);
    }

    public Color getColor(Vec3 position) {
        Block b = getBlock(position);
        return Tmp.c1.set(b.mapColor).a(1f - b.albedo);
    }

    Block getBlock(Vec3 pos) {
        float h = getHeight(pos);
        if (h < 0.12f) return Blocks.deepwater;
        if (h < 0.2f) return Blocks.water;
        if (h < 0.35f) return Blocks.sand;
        if (h < 0.55f) return Blocks.grass;
        if (h < 0.75f) return Blocks.stone;
        return Blocks.snow;
    }
}
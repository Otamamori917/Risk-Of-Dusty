package riskod.world.run;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.world.Block;
import mindustry.world.Tile;
import riskod.world.RiskodMaps;
import riskod.world.RiskodMaps.RiskodSector;
import riskod.world.RiskodPlanet;
import riskod.world.block.DroneChest;
import riskod.world.block.RelicChest;
import riskod.world.block.Shrine;
import riskod.world.block.Teleporter;

/**
 * Scatters chests, shrines, and one teleporter on Riskod sectors.
 * Per-map amounts: {@link RiskodSector#chests} / {@link RiskodSector#shrines}.
 */
public class InteractablesGenerator {
    public static final Seq<SpawnEntry> chests = new Seq<>();
    public static final Seq<SpawnEntry> shrines = new Seq<>();
    public static Teleporter teleporter;

    public static int minChests = 4;
    public static int maxChests = 18;
    public static int minShrines = 1;
    public static int maxShrines = 5;
    public static int marginTiles = 8;
    public static boolean spawnTeleporter = true;

    /// Rules tag set after the first scatter so a resumed save never gets a second set of chests and shrines.
    static final String GENERATED_TAG = "riskod.generated";

    public static class SpawnEntry {
        public Block block;
        public int rarity;
        public float weight;

        public SpawnEntry(Block block, int rarity, float weight) {
            this.block = block;
            this.rarity = Math.max(1, rarity);
            this.weight = weight;
        }
    }

    public static void register(Seq<SpawnEntry> list, Block block, int rarity, float weight) {
        if (block == null) return;
        list.add(new SpawnEntry(block, Math.max(1, rarity), weight));
    }

    public static void registerRelicChest(RelicChest chest) {
        if (chest == null) return;
        int r = Math.max(1, chest.chestRarity);
        register(chests, chest, r, 10f / r);
    }

    public static void registerDroneChest(DroneChest chest) {
        if (chest == null) return;
        int r = Math.max(1, chest.chestRarity);
        register(chests, chest, r, 8f / r);
    }

    public static void registerShrine(Shrine shrine) {
        if (shrine == null) return;
        int r = Math.max(1, shrine.shrineRarity);
        register(shrines, shrine, r, 6f / r);
    }

    public static void registerTeleporter(Teleporter block) {
        teleporter = block;
    }

    public static void generate() {
        if (!RiskodPlanet.onRiskod()) return;
        if (Vars.world == null) return;
        if (Vars.state.rules.tags.containsKey(GENERATED_TAG)) return;

        int w = Vars.world.width();
        int h = Vars.world.height();
        Team team = Vars.state.rules.defaultTeam;
        RiskodSector map = RiskodMaps.currentPreset();

        int cMin = pick(map == null ? -1 : map.minChests, minChests);
        int cMax = Math.max(cMin, pick(map == null ? -1 : map.maxChests, maxChests));
        int sMin = pick(map == null ? -1 : map.minShrines, minShrines);
        int sMax = Math.max(sMin, pick(map == null ? -1 : map.maxShrines, maxShrines));
        Teleporter tp = map != null && map.teleporterBlock != null ? map.teleporterBlock : teleporter;
        boolean placeTp = map == null ? spawnTeleporter : map.spawnTeleporter;

        if (placeTp && tp != null && !worldHas(Teleporter.class)) {
            Tile tile = findSpot(w, h, tp.size);
            if (tile != null) {
                tile.setNet(tp, team, 0);
                if (tile.build instanceof Teleporter.TeleporterBuild tb) {
                    RiskodMaps.applyTeleporter(tb);
                }
            }
        }

        scatter(chests, Mathf.random(cMin, cMax), w, h, team);
        scatter(shrines, Mathf.random(sMin, sMax), w, h, team);

        Vars.state.rules.tags.put(GENERATED_TAG, "1");
    }

    static int pick(int mapValue, int fallback) {
        return mapValue >= 0 ? mapValue : fallback;
    }

    static void scatter(Seq<SpawnEntry> list, int count, int w, int h, Team team) {
        if (list.isEmpty() || count <= 0) return;
        for (int i = 0; i < count; i++) {
            Block type = roll(list);
            if (type == null) continue;
            Tile tile = findSpot(w, h, type.size);
            if (tile == null) continue;
            tile.setNet(type, team, 0);
        }
    }

    static boolean worldHas(Class<? extends Block> type) {
        for (var b : Groups.build) {
            if (type.isInstance(b.block)) return true;
        }
        return false;
    }

    static Block roll(Seq<SpawnEntry> list) {
        float total = 0f;
        for (SpawnEntry e : list) total += spawnScore(e);
        if (total <= 0f) return null;

        float r = Mathf.random(total);
        float c = 0f;
        for (SpawnEntry e : list) {
            c += spawnScore(e);
            if (r <= c) return e.block;
        }
        return list.peek().block;
    }

    static float spawnScore(SpawnEntry e) {
        return e.weight / (float) Math.pow(e.rarity, 1.35f);
    }

    static Tile findSpot(int w, int h, int size) {
        for (int attempt = 0; attempt < 50; attempt++) {
            int tx = Mathf.random(marginTiles, Math.max(marginTiles + 1, w - marginTiles - size));
            int ty = Mathf.random(marginTiles, Math.max(marginTiles + 1, h - marginTiles - size));
            Tile tile = Vars.world.tile(tx, ty);
            if (tile == null) continue;
            if (!tile.floor().placeableOn || tile.floor().isLiquid) continue;
            if (tile.solid() || tile.block() != Blocks.air) continue;

            boolean ok = true;
            for (int dx = 0; dx < size && ok; dx++) {
                for (int dy = 0; dy < size && ok; dy++) {
                    Tile t = Vars.world.tile(tx + dx, ty + dy);
                    if (t == null || t.solid() || t.block() != Blocks.air || !t.floor().placeableOn) {
                        ok = false;
                    }
                }
            }
            if (ok) return tile;
        }
        return null;
    }
}
package riskod.world.run;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import riskod.world.RiskodPlanet;
import riskod.world.block.Teleporter;
import riskod.world.unit.PlayerCharUnitType;

public class HeroSpawner {

    public static void spawnOrReplace() {
        if (!RiskodPlanet.onRiskod()) return;
        if (!RunState.active() || RunState.current.heroType == null) return;
        if (Vars.net.client()) return;

        Team team = Vars.state.rules.defaultTeam;
        UnitType type = RunState.current.heroType;
        Player player = Vars.player;

        Tile t = spawnTile(5, 5);

        Unit existing = null;
        if (player != null && player.unit() != null && player.unit().type == type) {
            existing = player.unit();
        }
        if (existing == null) {
            existing = Groups.unit.find(u -> u.team == team && u.type == type && u.isValid());
        }
        if (existing == null) {
            if (t != null) {
                existing = type.spawn(team, t.worldx(), t.worldy());
            }
        }

        bindLoadout(existing);

        if (player != null) {
            player.unit(existing);
        }

        Unit finalExisting = existing;
        Groups.unit.each(u -> {
            if (u.team != team || u == finalExisting) return;
            if (u.spawnedByCore || u.isPlayer()) {
                if (!(u.type instanceof PlayerCharUnitType)) {
                    u.remove();
                }
            }
        });

        applyHealth(existing);
    }

    static Tile spawnTile(int marginX, int marginY) {
        Teleporter.TeleporterBuild tp = findTeleporter();
        if (tp == null) return randomTile(marginX, marginY);

        float minDst = Math.min(
                tp.useChargeRadius() + 8f * Vars.tilesize,
                0.4f * Math.min(Vars.world.width(), Vars.world.height()) * Vars.tilesize
        );

        Tile farthest = null;
        float farthestDst = -1f;
        for (int i = 0; i < 25; i++) {
            Tile t = randomTile(marginX, marginY);
            if (t == null) continue;
            float d = Mathf.dst(t.worldx(), t.worldy(), tp.x, tp.y);
            if (d >= minDst) return t;
            if (d > farthestDst) {
                farthestDst = d;
                farthest = t;
            }
        }
        return farthest;
    }

    static Teleporter.TeleporterBuild findTeleporter() {
        for (var b : Groups.build) {
            if (b instanceof Teleporter.TeleporterBuild tb) return tb;
        }
        return null;
    }

    static void bindLoadout(Unit unit) {
        PlayerLoadout pending = RunState.current.pendingLoadout;
        if (pending != null) {
            PlayerCharUnitType.loadouts.put(unit.id, pending);
            pending.recompute();
            RunState.current.pendingLoadout = null;
        } else {
            PlayerCharUnitType.loadout(unit);
        }
    }

    static void applyHealth(Unit unit) {
        PlayerLoadout l = PlayerCharUnitType.loadout(unit);
        if (l == null) return;
        float max = unit.type.health * l.healthMul;
        unit.maxHealth(max);
        if (unit.health > max || unit.health <= 0f) unit.health = max;
    }

    public static Tile randomTile(int marginX, int marginY) {
        int w = Vars.world.width();
        int h = Vars.world.height();
        for (int i = 0; i < 25; i++) {
            int tx = Mathf.random(marginX, Math.max(marginX + 1, w - marginX - 1));
            int ty = Mathf.random(marginY, Math.max(marginY + 1, h - marginY - 1));
            Tile t = Vars.world.tile(tx, ty);
            if (t != null && t.floor().placeableOn && !t.solid() && !t.isDeep()) return t;
        }
        return null;
    }
}
package riskod.world;

import arc.Events;
import mindustry.Vars;
import mindustry.content.StatusEffects;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import riskod.world.abilites.LootCarryAbility;
import riskod.world.block.Teleporter;
import riskod.world.meta.Meta;
import riskod.world.run.*;
import riskod.world.ui.AbilityBarHud;
import riskod.world.ui.HeroSelectUi;
import riskod.world.unit.CompanionDroneType;
import riskod.world.unit.HuntHeroAI;
import riskod.world.unit.PlayerCharUnitType;

public class RunHooks {
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;

        AbilityBarHud.register();
        HeroLock.register();
        HeroSelectUi.register();

        Events.on(EventType.ClientLoadEvent.class, e ->
                arc.Core.app.post(RiskodMaps::refreshStartUnlocks));

        Events.run(EventType.Trigger.update, Meta::update);

        Events.on(EventType.StateChangeEvent.class, e -> {
            if (e.to == mindustry.core.GameState.State.menu) {
                RiskodMaps.refreshStartUnlocks();
                Meta.flush();
            }
        });

        Events.on(EventType.UnitDamageEvent.class, e -> {
            if (e.unit == null || e.bullet == null || e.bullet.team == null) return;
            for (var a : e.unit.abilities) {
                if (a instanceof LootCarryAbility) {
                    LootCarryAbility.noteAttack(e.unit, e.bullet.team);
                    break;
                }
            }
        });

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if (e.unit != null) {
                PlayerCharUnitType.removeLoadout(e.unit);
                LootCarryAbility.lastAttackerTeam.remove(e.unit.id);
                LootCarryAbility.lastAttackTime.remove(e.unit.id);
            }
        });

        Events.run(EventType.Trigger.update, () -> {
            if (!RiskodPlanet.onRiskod()) return;
            if (RunState.active()) {
                RunState.current.update();
                EnemySpawnDirector.update();
                Groups.unit.each(u -> {
                    if (u.team == Vars.state.rules.waveTeam && u.isValid()) {
                        HuntHeroAI.apply(u);
                    }
                });
            }
        });

        Events.on(EventType.UnitDamageEvent.class, e -> {
            if (!RunState.active() || e.bullet == null) return;
            if (e.bullet.owner instanceof Unit u && u.type instanceof PlayerCharUnitType) {
                RunState.current.noteDamageDealt(e.bullet.damage);
            }
            if (e.unit != null && e.unit.type instanceof PlayerCharUnitType
                    && e.bullet.owner instanceof Unit src && src.team != e.unit.team) {
                RunState.current.lastHitBy = src.type.name;
            }
        });

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if (!RiskodPlanet.onRiskod() || e.unit == null) return;
            if (e.unit.type instanceof PlayerCharUnitType && RunState.active()) {
                RunState.current.onHeroDeath(e.unit);
                return;
            }
            if (RunState.active() && e.unit.team == Vars.state.rules.waveTeam) {
                RunState.current.addKillXp(false);
                Meta.enemyKilled(e.unit.type.name, e.unit.hasEffect(StatusEffects.boss));
            }
            CompanionDroneType.ActionState.map.remove(e.unit.id);
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            if (!RiskodPlanet.onRiskod()) return;
            arc.util.Time.run(20f, () -> {
                if (!RunState.active()) RunState.restore();

                InteractablesGenerator.generate();
                Groups.build.each(b -> {
                    if (b instanceof Teleporter.TeleporterBuild tb) {
                        RiskodMaps.applyTeleporter(tb);
                    }
                });

                if (RunState.active()) {
                    var p = RiskodMaps.currentPreset();
                    if (p != null && !RunState.usedMaps.contains(p.name)) {
                        RunState.usedMaps.add(p.name);
                    }
                    HeroSpawner.spawnOrReplace();
                } else {
                    HeroSelectUi.maybePrompt();
                }
                RunState.persist();
            });
        });


    }

    public static void applyLevelStats(Unit unit) {
        if (!RunState.active() || unit == null) return;
        PlayerLoadout l = PlayerCharUnitType.loadout(unit);
        if (l != null) {
            RunState.current.applyLevelToLoadout(l);
        }
    }
}
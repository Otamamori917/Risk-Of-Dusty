package riskod.world.run;

import arc.Events;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import riskod.world.unit.PlayerCharUnitType;

/**
 * During a run or mock run, puts the player back into their hero if they leave it (e.g. the respawn key) while it is still alive.
 */
public class HeroLock {
    static boolean registered;

    /// Last hero unit the player controlled during the current run.
    static Unit lastHero;

    public static void register() {
        if (registered) return;
        registered = true;

        Events.run(EventType.Trigger.update, HeroLock::update);
        Events.on(EventType.WorldLoadEvent.class, e -> lastHero = null);
    }

    static void update() {
        if (Vars.player == null || !(RunState.active() || MockRun.active)) {
            lastHero = null;
            return;
        }

        Unit now = Vars.player.unit();
        if (now != null && now.isValid() && now.type instanceof PlayerCharUnitType) {
            lastHero = now;
            return;
        }

        if (lastHero == null || !lastHero.isValid() || lastHero.dead) {
            lastHero = null;
            return;
        }
        if (lastHero.team != Vars.player.team()) return;

        if (now != null && now.isValid() && now.spawnedByCore) now.remove();
        Vars.player.unit(lastHero);
    }
}
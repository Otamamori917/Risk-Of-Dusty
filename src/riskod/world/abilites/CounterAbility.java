package riskod.world.abilites;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import riskod.world.run.PlayerLoadout;

/**
 * Counter window + consecutive perfects → Refresh.
 * Streak breaks on: failed window, idle timeout, or taking {@link #streakBreakDamage} damage.
 * Outer/inner rings drawn here (not in subclasses).
 */
public abstract class CounterAbility extends ChargedAbility {

    public int perfectsNeeded = 3;
    public float counterWindow = 22f;
    public float streakTimeout = 180f;
    /** Damage taken in one hit (or accumulated between checks) that breaks the streak. */
    public float streakBreakDamage = 15f;

    public float perfectHitSizeMul = 1.4f;
    public float perfectAimAngle = 50f;

    static final IntMap<Float> windowLeft = new IntMap<>();
    static final IntMap<Integer> streak = new IntMap<>();
    static final IntMap<Float> sincePerfect = new IntMap<>();
    static final IntMap<Boolean> scoredThisWindow = new IntMap<>();
    static final IntMap<Float> lastHealth = new IntMap<>();

    static final Seq<RingVis> rings = new Seq<>();
    static boolean drawHooked;

    static class RingVis {
        float x, y, outer, inner;
        boolean window;
        Color team;
    }

    public CounterAbility() {
        ensureDrawHook();
    }

    static void ensureDrawHook() {
        if (drawHooked || Vars.headless) return;
        drawHooked = true;
        Events.run(Trigger.draw, () -> {
            if (rings.isEmpty()) return;
            boolean hitbox = Core.settings.getBool("drawhitboxes");
            Draw.z(Layer.effect);
            for (RingVis v : rings) {
                if (hitbox) {
                    if (v.outer > 0f) {
                        Draw.color(Color.green, v.window ? 0.75f : 0.4f);
                        Lines.stroke(v.window ? 2f : 1.2f);
                        Lines.circle(v.x, v.y, v.outer);
                    }
                    Draw.color(Color.yellow, v.window ? 0.9f : 0.35f);
                    Lines.stroke(v.window ? 1.8f : 1.1f);
                    Lines.circle(v.x, v.y, v.inner);
                }
            }
            Draw.reset();
            rings.clear();
        });
    }

    int ckey(Unit unit) {
        return stateKey(unit);
    }

    public boolean isCounterWindowOpen(Unit unit) {
        return windowLeft.get(ckey(unit), 0f) > 0f;
    }

    public int counterStreak(Unit unit) {
        return streak.get(ckey(unit), 0);
    }

    public final void scorePerfect(Unit unit, PlayerLoadout loadout) {
        if (unit == null || loadout == null) return;
        int k = ckey(unit);
        if (windowLeft.get(k, 0f) <= 0f) return;

        scoredThisWindow.put(k, true);
        sincePerfect.put(k, 0f);

        int s = streak.get(k, 0) + 1;
        streak.put(k, s);
        toast("[stat]PERFECT " + s + "/" + perfectsNeeded + "[]");
        Sounds.drillImpact.at(unit.x, unit.y, 1f + s * 0.08f, 0.9f);

        if (s >= perfectsNeeded) {
            streak.put(k, 0);
            windowLeft.put(k, 0f);
            scoredThisWindow.put(k, false);
            toast("[stat]REFRESH[]");
            refreshAll(unit, loadout);
        }
    }

    public final void breakStreak(Unit unit) {
        int k = ckey(unit);
        if (streak.get(k, 0) > 0) toast("Counter streak lost");
        streak.put(k, 0);
        sincePerfect.put(k, 0f);
    }

    public boolean isIncomingBullet(Unit unit, Bullet b) {
        if (unit == null || b == null || !isCounterWindowOpen(unit)) return false;
        if (!b.within(unit, unit.hitSize * perfectHitSizeMul)) return false;
        return Angles.within(b.rotation(), b.angleTo(unit), perfectAimAngle);
    }

    public final boolean tryPerfectBullet(Unit unit, PlayerLoadout loadout, Bullet b) {
        if (!isIncomingBullet(unit, b)) return false;
        scorePerfect(unit, loadout);
        return true;
    }

    void checkDamageBreak(Unit unit) {
        if (unit == null || streakBreakDamage <= 0f) return;
        int k = ckey(unit);
        float prev = lastHealth.get(k, unit.health);
        float lost = prev - unit.health;
        lastHealth.put(k, unit.health);
        if (lost >= streakBreakDamage && streak.get(k, 0) > 0) {
            breakStreak(unit);
        }
    }

    void queueRings(Unit unit, PlayerLoadout loadout, int slot) {
        if (Vars.player == null || Vars.player.unit() != unit) return;
        RingVis v = new RingVis();
        v.x = unit.x;
        v.y = unit.y;
        v.outer = 0f;
        v.inner = unit.hitSize * perfectHitSizeMul;
        v.window = isCounterWindowOpen(unit);
        v.team = unit.team.color;
        rings.add(v);
    }

    @Override
    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        super.update(unit, loadout, slot);
        int k = ckey(unit);

        checkDamageBreak(unit);
        queueRings(unit, loadout, slot);

        if (streak.get(k, 0) > 0 && streakTimeout > 0f) {
            float idle = sincePerfect.get(k, 0f) + Time.delta;
            sincePerfect.put(k, idle);
            if (idle >= streakTimeout) {
                breakStreak(unit);
            }
        }

        float left = windowLeft.get(k, 0f);
        if (left <= 0f) return;

        left -= Time.delta;
        if (left > 0f) {
            windowLeft.put(k, left);
            return;
        }

        windowLeft.put(k, 0f);
        if (!scoredThisWindow.get(k, false)) {
            breakStreak(unit);
        }
        scoredThisWindow.put(k, false);
    }

    @Override
    public final boolean onActivate(Unit unit, PlayerLoadout loadout, int slot) {
        int k = ckey(unit);
        windowLeft.put(k, counterWindow);
        scoredThisWindow.put(k, false);
        lastHealth.put(k, unit.health);
        return onCounterActivate(unit, loadout, slot);
    }

    protected abstract boolean onCounterActivate(Unit unit, PlayerLoadout loadout, int slot);
}
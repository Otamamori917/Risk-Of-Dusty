package riskod.world.abilites;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import riskod.RiskOfDustryLoader;
import riskod.world.bullets.LensWarp;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.unit.ChantDroneType;
import riskod.world.unit.CompanionDroneType;

/**
 * Hold: ramping sonic beam (range uses {@link #rangeMul}).
 * Release: full-beam burst + delayed side waves; full charge focuses chanted attack drones.
 */
public class SustainAbility extends ChargedAbility {

    public float range = 95f;
    public float cone = 6f;
    public float baseDps = 55f;
    public float maxDpsMul = 5f;
    public float maxChannel = 320f;
    public float tick = 3f;
    public float moveMulWhileChannel = 0.55f;
    public float warpEvery = 2f;
    public float beamWidth = 6f;
    public float popBurstMul = 8.2f;
    public int popWaves = 6;
    public float popWaveDelayPer = 3.5f;
    public float popSideCone = 75f;
    public float focusDuration = 260f;
    public float focusReloadMul = 0.7f;
    public float fullChargeThresh = 0.98f;

    private static final IntMap<Float> channel = new IntMap<>();
    private static final IntMap<Float> tickAcc = new IntMap<>();
    private static final IntMap<Float> warpAcc = new IntMap<>();
    private static final IntMap<Boolean> active = new IntMap<>();

    static final Seq<BeamVis> beams = new Seq<>();
    static boolean drawHooked;

    static class BeamVis {
        float x, y, rot, range, cone, width;
    }

    public SustainAbility() {
        maxCharges = 1;
        cooldown = 20f;
        chargesOnReady = 1;
        ensureDrawHook();
    }

    static void ensureDrawHook() {
        if (drawHooked || Vars.headless) return;
        drawHooked = true;
        Events.run(Trigger.draw, () -> {
            if (!Core.settings.getBool("drawhitboxes") || beams.isEmpty()) {
                beams.clear();
                return;
            }
            Draw.z(Layer.effect);
            for (BeamVis b : beams) {
                Draw.color(Color.valueOf("#008000"), 0.60f);
                Lines.stroke(b.width);
                float x2 = b.x + Angles.trnsx(b.rot, b.range);
                float y2 = b.y + Angles.trnsy(b.rot, b.range);
                Lines.line(b.x, b.y, x2, y2);
                Draw.color(Color.valueOf("#82A67D"), 0.60f);
                Lines.stroke(1.2f);
                Lines.line(b.x, b.y,
                        b.x + Angles.trnsx(b.rot - b.cone, b.range),
                        b.y + Angles.trnsy(b.rot - b.cone, b.range));
                Lines.line(b.x, b.y,
                        b.x + Angles.trnsx(b.rot + b.cone, b.range),
                        b.y + Angles.trnsy(b.rot + b.cone, b.range));
                Draw.color(Color.valueOf("#023020"), 0.60f);
                Lines.arc(b.x, b.y, b.range, b.cone * 2f, b.rot - b.cone);
            }
            Draw.reset();
            beams.clear();
        });
    }

    int key(Unit unit) {
        return stateKey(unit);
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout loadout, int slot) {
        int k = key(unit);
        if (active.get(k, false)) return false;
        active.put(k, true);
        channel.put(k, 0f);
        tickAcc.put(k, 0f);
        warpAcc.put(k, 0f);
        return true;
    }

    @Override
    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        super.update(unit, loadout, slot);

        int k = key(unit);
        if (!active.get(k, false)) return;

        if (!RiskOfDustryLoader.slotDown(slot)) {
            endChannel(unit, loadout, slot, true);
            return;
        }

        float rng = range * rangeMul(loadout, slot);
        float t = channel.get(k, 0f) + Time.delta;
        channel.put(k, t);
        float ramp = Mathf.clamp(t / maxChannel);
        unit.vel.scl(Mathf.lerp(1f, moveMulWhileChannel, 0.2f));

        float dps = baseDps * Mathf.lerp(1f, maxDpsMul, ramp) * damageMul(loadout, slot);

        float acc = tickAcc.get(k, 0f) + Time.delta;
        if (acc >= tick) {
            acc -= tick;
            beamHit(unit, dps * (tick / 60f), null, rng);
            Sounds.shoot.at(unit.x, unit.y, 0.55f + ramp * 0.9f, 1.2f);
        }
        tickAcc.put(k, acc);

        float w = warpAcc.get(k, 0f) + Time.delta;
        if (w >= warpEvery) {
            w = 0f;
            for (int i = 1; i <= 3; i++) {
                float frac = i / 3.25f;
                Tmp.v1.trns(unit.rotation, rng * frac * (0.45f + ramp * 0.5f)).add(unit.x, unit.y);
                LensWarp.add(Tmp.v1.x, Tmp.v1.y, 24f + ramp * 30f, 0.4f + ramp * 0.5f, 7f,
                        unit.rotation + 90f, popSideCone * 0.6f);
                LensWarp.add(Tmp.v1.x, Tmp.v1.y, 24f + ramp * 30f, 0.4f + ramp * 0.5f, 7f,
                        unit.rotation - 90f, popSideCone * 0.6f);
            }
        }
        warpAcc.put(k, w);

        if (t >= maxChannel) {
            endChannel(unit, loadout, slot, true);
        }

        if (Vars.player != null && Vars.player.unit() == unit && Core.settings.getBool("drawhitboxes")) {
            BeamVis b = new BeamVis();
            b.x = unit.x;
            b.y = unit.y;
            b.rot = unit.rotation;
            b.range = rng;
            b.cone = cone;
            b.width = beamWidth;
            beams.add(b);
        }
    }

    void beamHit(Unit unit, float dmg, Seq<Unit> hitList, float rng) {
        float rot = unit.rotation;
        Tmp.v1.trns(rot, rng).add(unit.x, unit.y);
        float x2 = Tmp.v1.x, y2 = Tmp.v1.y;

        Units.nearbyEnemies(unit.team, unit.x, unit.y, rng + 24f, u -> {
            if (!Angles.within(rot, unit.angleTo(u), cone + 6f)) return;
            if (unit.dst(u) > rng + u.hitSize / 2f) return;
            if (distToSegment(u.x, u.y, unit.x, unit.y, x2, y2) > beamWidth + u.hitSize / 2f) return;

            u.damage(dmg);
            if (RunState.active()) RunState.current.noteDamageDealt(dmg);
            u.impulse(Tmp.v2.trns(rot, 2.2f));
            if (hitList != null) hitList.add(u);
        });
    }

    void endChannel(Unit unit, PlayerLoadout loadout, int slot, boolean pop) {
        int k = key(unit);
        if (!active.get(k, false)) return;

        float t = channel.get(k, 0f);
        float ramp = Mathf.clamp(t / maxChannel);
        active.put(k, false);
        channel.put(k, 0f);
        if (!pop) return;

        float rng = range * rangeMul(loadout, slot);
        float dps = baseDps * Mathf.lerp(1f, maxDpsMul, ramp) * damageMul(loadout, slot);
        float burst = dps * (tick / 60f) * popBurstMul;

        Seq<Unit> hit = new Seq<>();
        beamHit(unit, burst, hit, rng);

        float rot = unit.rotation;
        float ox = unit.x, oy = unit.y;
        int n = Math.max(3, popWaves);
        for (int i = 0; i < n; i++) {
            float frac = (i + 1f) / n;
            float delay = i * popWaveDelayPer;
            float px = ox + Angles.trnsx(rot, rng * frac);
            float py = oy + Angles.trnsy(rot, rng * frac);
            float rad = 34f + frac * 80f + ramp * 40f;
            float str = 1.0f + ramp * 1.3f + frac * 0.35f;
            float side = popSideCone;

            Time.run(delay, () -> {
                LensWarp.add(px, py, rad, str, frac*2, rot + 90f, side);
                LensWarp.add(px, py, rad, str, frac*2, rot - 90f, side);
            });
        }

        Sounds.shootMissileLong.at(unit.x, unit.y, 0.55f + ramp * 0.5f, 0.9f);

        if (ramp >= fullChargeThresh && hit.any()) {
            Unit focus = null;
            for (Unit u : hit) {
                if (u == null || !u.isValid()) continue;
                if (focus == null || u.health > focus.health) focus = u;
            }
            if (focus != null) orderDrones(unit, focus);
        }
    }

    void orderDrones(Unit hero, Unit target) {
        Groups.unit.each(u -> {
            if (u.team != hero.team || !u.isValid()) return;
            if (!(u.type instanceof ChantDroneType ct)) return;
            boolean offensive = ct.role == CompanionDroneType.Role.attack;
            if (!offensive && ct.role == CompanionDroneType.Role.gear) {
                var s = CompanionDroneType.ActionState.get(u);
                offensive = s != null && CompanionDroneType.isOffensiveGear(s.gear);
            }
            if (!offensive) return;
            CompanionDroneType.setFocus(u, target, focusDuration, focusReloadMul);
        });
        toast("[stat]Focus fire[]");
    }

    static float distToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 < 0.0001f) return Mathf.dst(px, py, x1, y1);
        float t = Mathf.clamp(((px - x1) * dx + (py - y1) * dy) / len2);
        return Mathf.dst(px, py, x1 + dx * t, y1 + dy * t);
    }
}
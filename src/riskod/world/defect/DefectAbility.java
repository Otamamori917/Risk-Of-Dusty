package riskod.world.defect;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import riskod.world.abilites.ChargedAbility;
import riskod.world.abilites.CounterAbility;
import riskod.world.abilites.SustainAbility;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;
import riskod.world.unit.PlayerCharUnitType;

public abstract class DefectAbility extends ChargedAbility {
    public float energyCost = 1f;
    public float value;
    public Unit parent;
    public boolean hit = false;

    public DefectAbility() {
        maxCharges = 4;
        chargesOnReady = 1;
        cooldown = 45f;
        ensureDrawHook();
    }

    static final Seq<BeamVis> beams = new Seq<>();
    static boolean drawHooked;

    static class BeamVis {
        float x, y, rot, range, cone, width;
    }

    static final Seq<RingVis> rings = new Seq<>();

    static class RingVis {
        float x, y, radius;
        boolean window;
        Color team;
    }

    public static boolean playerHasAbility() {
        if (Vars.player == null) return false;
        Unit u = Vars.player.unit();
        if (u == null || !u.isValid() || !(u.type instanceof PlayerCharUnitType)) return false;
        PlayerLoadout l = PlayerCharUnitType.loadouts.get(u.id);
        if (l == null) return false;
        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            RelicType r = l.slots[i];
            if (r != null && r.ability instanceof DefectAbility) return true;
        }
        return false;
    }

    protected boolean equippedBy(Unit unit, PlayerLoadout l) {
        if (Vars.player == null || Vars.player.unit() != unit || l == null) return false;
        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            RelicType r = l.slots[i];
            if (r != null && r.ability != null && r.ability.getClass() == getClass()) return true;
        }
        return false;
    }

    static void ensureDrawHook() {
        if (drawHooked || Vars.headless) return;
        drawHooked = true;
        Events.run(EventType.Trigger.draw, () -> {
            if (!Core.settings.getBool("drawhitboxes") || beams.isEmpty() || !playerHasAbility()) {
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
                Lines.stroke(2.4f);
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
        Events.run(EventType.Trigger.draw, () -> {
            if (rings.isEmpty()) return;
            if (!playerHasAbility()) {
                rings.clear();
                return;
            }
            boolean hitbox = Core.settings.getBool("drawhitboxes");
            Draw.z(Layer.effect);
            for (RingVis v : rings) {
                if (hitbox) {
                    if (v.radius > 0f) {
                        Draw.color(Color.green, v.window ? 0.75f : 0.4f);
                        Lines.stroke(v.window ? 2f : 1.2f);
                        Lines.circle(v.x, v.y, v.radius);
                    }
                }
            }
            Draw.reset();
            rings.clear();
        });
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout l, int slot) {
        if (!(unit.type instanceof DefectUnitType)) {
            toast("Not Defect");
            return false;
        }
        DefectState s = DefectState.get(unit);
        if (s == null) return false;

        float cost = energyCost;
        if (cost > 0f && s.energy < cost) {
            toast("Need " + (int) cost + " energy (" + (int) s.energy + "/" + (int) s.energyCap + ")");
            return false;
        }
        if (!s.spendEnergy(cost)) return false;

        boolean ok = activate(unit, s, l, slot);
        if (!ok) {
            s.addEnergy(cost);
            return false;
        }

        if (s.echoForm) {
            s.echoForm = false;
            activate(unit, s, l, slot);
        }
        return true;
    }

    protected abstract boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot);
}
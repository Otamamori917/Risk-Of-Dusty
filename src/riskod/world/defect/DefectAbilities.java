package riskod.world.defect;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.ObjectSet;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import riskod.world.abilites.ChargedAbility;
import riskod.world.abilites.CounterAbility;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.abilites.SustainAbility;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;

public class DefectAbilities {

    public static class Zap extends DefectAbility {
        public Zap() {
            energyCost = 0f;
            maxCharges = 3;
            cooldown = 120f;
            noCycleRefresh = true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            s.channel(unit, OrbType.lightning);
            Fx.lightning.at(unit.x, unit.y);
            return true;
        }
    }

    public static class StaticDischarge extends DefectAbility {
        public float duration = 400f;

        public StaticDischarge() {
            energyCost = 2f;
            maxCharges = 1;
            cooldown = 450f;
            noCycleRefresh = true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            s.staticDischargeTime = duration;
            s.staticDischargeCd = 0f;
            Fx.shieldBreak.at(unit.x, unit.y, unit.hitSize, Pal.lancerLaser);
            return true;
        }
    }

    public static class ThunderStrike extends DefectAbility {
        public float basePerChannel = 25f;
        public float cone = 45f;
        public float range = 120f;

        public ThunderStrike() {
            energyCost = 3f;
            maxCharges = 2;
            cooldown = 180f;
            noCycleRefresh = true;
            ensureDrawHook();
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);

            DefectState s = DefectState.get(unit);

            float dmg = basePerChannel * Math.max(0, s.lightningChanneledSector);
            dmg *= damageMul(l, slot);

            value =  dmg;
            parent = unit;

            if (equippedBy(unit, l) && Core.settings.getBool("drawhitboxes")) {
                BeamVis b = new BeamVis();
                b.x = unit.x;
                b.y = unit.y;
                b.rot = unit.rotation;
                b.range = range;
                b.cone = cone;
                b.width = 0;
                beams.add(b);
            }
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            float dmg = basePerChannel * Math.max(0, s.lightningChanneledSector);
            dmg *= damageMul(l, slot);
            float r = range * rangeMul(l, slot);
            if (dmg <= 0f) {
                toast("No lightning channeled this sector");
                return true;
            }
            float rot = unit.rotation;
            float finalDmg = dmg;
            Units.nearbyEnemies(unit.team, unit.x, unit.y, r, u -> {
                if (Angles.within(rot, unit.angleTo(u), cone)) {
                    if(RunState.active()) RunState.current.noteDamageDealt(finalDmg);
                    u.damage(finalDmg);
                    Fx.hitLancer.at(u.x, u.y);
                }
            });
            Fx.lightning.at(unit.x, unit.y);
            return true;
        }
    }

    public static class ColdSnap extends DefectAbility {
        public float range = 156;
        public float cone = 4;

        public ColdSnap() {
            energyCost = 2f;
            maxCharges = 2;
            cooldown = 320f;
            noCycleRefresh = true;
            ensureDrawHook();
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);
            value = Math.max(1f, unit.shield) * damageMul(l, slot);
            parent = unit;

            if (equippedBy(unit, l) && Core.settings.getBool("drawhitboxes")) {
                BeamVis b = new BeamVis();
                b.x = unit.x;
                b.y = unit.y;
                b.rot = unit.rotation;
                b.range = range;
                b.cone = cone;
                b.width = 6;
                beams.add(b);
            }
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            float dmg = Math.max(1f, unit.shield) * damageMul(l, slot);
            float ang = unit.rotation;
            float range = this.range * rangeMul(l, slot);
            Units.nearbyEnemies(unit.team, unit.x, unit.y, range, u -> {
                float a = unit.angleTo(u);
                if (Angles.within(ang, a, cone) && unit.within(u, range)) {
                    if (RunState.active()) {
                        RunState.current.noteDamageDealt(dmg);
                    }
                    u.damage(dmg);
                    Fx.hitLancer.at(u.x, u.y);
                }
            });
            s.channel(unit, OrbType.frost);
            Fx.freezing.at(unit.x, unit.y);
            return true;
        }
    }

    public static class Glacier extends DefectAbility {
        public float shieldBase = 34f;

        public Glacier() {
            energyCost = 1f;
            maxCharges = 2;
            cooldown = 200;
            noCycleRefresh = true;
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);

            value = shieldBase;
            parent = unit;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            s.addShield(unit, shieldBase);
            float r = (unit.hitSize*1.25f) * rangeMul(l, slot);
            Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, eb -> {
                if (eb.team != unit.team && eb.within(unit, r)) {
                    toast("[stat]PERFECT COUNTER[]");
                    Sounds.drillImpact.at(unit);
                    refreshAll(unit, l);
                }
            });
            s.channel(unit, OrbType.frost, 2);
            Fx.shieldBreak.at(unit.x, unit.y, unit.hitSize, Pal.lancerLaser);
            return true;
        }
    }

    public static class Buffer extends DefectAbility {
        public int stacksPerUse = 1;
        public float shieldCost = 80f;

        public Buffer() {
            energyCost = 2f;
            maxCharges = 2;
            cooldown = 600f;
            noCycleRefresh = true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            if (unit.shield < shieldCost) {
                toast("Need " + (int) shieldCost + " shield (" + (int) unit.shield + ")");
                return false;
            }
            unit.shield -= shieldCost;
            s.bufferStacks += stacksPerUse;
            Fx.shieldApply.at(unit.x, unit.y, 0f, Pal.lancerLaser);
            return true;
        }
    }
    public static class Darkness extends DefectAbility {
        public Darkness() {
            energyCost = 1f;
            maxCharges = 3;
            cooldown = 160;
            noCycleRefresh = true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            s.channel(unit, OrbType.dark);
            s.triggerPassivesOf(unit, OrbType.dark, 1);
            Fx.mine.at(unit.x, unit.y);
            return true;
        }
    }

    public static class DoomAndGloom extends DefectAbility {
        public float damage = 280f;
        public float range = 64f;
        public float cone = 82f;

        public DoomAndGloom() {
            energyCost = 2f;
            maxCharges = 2;
            cooldown = 120f;
            value = damage;
            ensureDrawHook();
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);
            value = damage * damageMul(l, slot);
            parent = unit;

            if (equippedBy(unit, l) && Core.settings.getBool("drawhitboxes")) {
                BeamVis b = new BeamVis();
                b.x = unit.x;
                b.y = unit.y;
                b.rot = unit.rotation;
                b.range = range;
                b.cone = cone;
                b.width = 0;
                beams.add(b);
            }
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            float dmg = damage * damageMul(l, slot);
            float r = range * rangeMul(l, slot);
            float rot = unit.rotation;
            Units.nearbyEnemies(unit.team, unit.x, unit.y, r, u -> {
                if (Angles.within(rot, unit.angleTo(u), cone)) {
                    if (RunState.active()) {
                        RunState.current.noteDamageDealt(dmg);
                    }
                    u.damage(dmg);
                    Fx.hitBulletColor.at(u.x, u.y, Pal.suppress);
                }
            });
            s.channel(unit, OrbType.dark, 2);
            return true;
        }
    }

    public static class Sunder extends DefectAbility {
        public float damage = 300f;
        public float range = 80f;
        public int triggerAmount = 5;

        public Sunder() {
            energyCost = 3f;
            maxCharges = 1;
            cooldown = 380f;
            noCycleRefresh = true;
            value = damage;
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);
            value = damage * damageMul(l, slot);
            parent = unit;

            if (!equippedBy(unit, l)) return;
            RingVis v = new RingVis();
            v.x = unit.x;
            v.y = unit.y;
            v.radius = range;
            v.window = hit;
            v.team = unit.team.color;
            rings.add(v);
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            float r = range * rangeMul(l, slot);
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, r, u -> true);
            if (target == null) {
                hit = false;
                toast("No target");
                return true;
            }
            float dmg = damage * damageMul(l, slot);
            if (RunState.active()) {
                RunState.current.noteDamageDealt(dmg);
            }
            target.damage(dmg);
            boolean killed = target.dead || !target.isValid() || target.health <= 0.001f;
            if (killed) {
                hit = true;
                s.triggerPassivesOf(unit, OrbType.dark, triggerAmount);
            }
            hit = false;
            Fx.explosion.at(target.x, target.y);
            return true;
        }
    }

    public static class Fusion extends DefectAbility {
        public Fusion() {
            energyCost = 2f;
            maxCharges = 2;
            cooldown = 120f;
            noCycleRefresh = true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            s.channel(unit, OrbType.plasma);
            Fx.pulverize.at(unit.x, unit.y);
            return true;
        }
    }

    public static class Fission extends DefectAbility {
        public Fission() {
            energyCost = 0f;
            maxCharges = 2;
            cooldown = 60f;
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);

            DefectState s = DefectState.get(unit);
            parent = unit;
            value = s.orbs.size;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            if (s.orbs.isEmpty()){
                toast("You have no orbs to sacrifice");
                return false;
            }
            int n = s.removeAllOrbs();
            s.addEnergy(n, true);
            s.addFocus(n);

            refreshAll(unit,l);

            Fx.explosion.at(unit.x, unit.y);
            toast("Fission +" + n + " energy/focus");
            return true;
        }
    }

    public static class MeteorStrike extends DefectAbility {
        public float damage = 600f;
        public float range = 100f;

        public MeteorStrike() {
            energyCost = 5f;
            maxCharges = 1;
            cooldown = 460f;
            noCycleRefresh = true;
            value = damage;
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);
            value = damage * damageMul(l, slot);
            parent = unit;

            if (!equippedBy(unit, l)) return;
            RingVis v = new RingVis();
            v.x = unit.x;
            v.y = unit.y;
            v.radius = range;
            v.window = hit;
            v.team = unit.team.color;
            rings.add(v);
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            float r = range * rangeMul(l, slot);
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, r, u -> true);
            if (target != null) {
                float dmg = damage * damageMul(l, slot);
                hit = true;
                target.damage(dmg);
                if (RunState.active()) {
                    RunState.current.noteDamageDealt(dmg);
                }
                Fx.massiveExplosion.at(target.x, target.y);
            } else {
                hit = false;
                toast("No target");
            }
            hit = false;
            s.channel(unit, OrbType.plasma, 3);
            return true;
        }
    }

    public static class Multicast extends DefectAbility {
        public Multicast() {
            energyCost = 0f;
            maxCharges = 3;
            cooldown = 340f;
            noCycleRefresh = true;
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);
            DefectState s = DefectState.get(unit);

            value = (int) s.energy+1;
            parent = unit;
        }

        @Override
        public boolean onActivate(Unit unit, PlayerLoadout l, int slot) {
            if (!(unit.type instanceof DefectUnitType)) {
                toast("Not Defect");
                return false;
            }
            DefectState s = DefectState.get(unit);
            if (s == null || s.orbs.isEmpty()) {
                toast("No orbs");
                return false;
            }

            int x = (int) s.energy;
            s.energy = 0f;
            s.multicastExtra = x;
            s.evokeNext(unit);

            if (s.echoForm) {
                s.echoForm = false;
            }
            toast("Multicast x" + (x + 1));
            return true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            return true;
        }
    }

    public static class HyperBeam extends DefectAbility {
        public float damage = 800f;
        public float cone = 1f;
        public int focusCost = 5;
        public float range = 280f;

        public HyperBeam() {
            energyCost = 3f;
            maxCharges = 1;
            cooldown = 560f;
            noCycleRefresh = true;
            value = damage;
            ensureDrawHook();
        }

        @Override
        public void update(Unit unit,PlayerLoadout l, int slot){
            super.update(unit,l,slot);
            value = damage * damageMul(l, slot);
            parent = unit;

            if (equippedBy(unit, l) && Core.settings.getBool("drawhitboxes")) {
                BeamVis b = new BeamVis();
                b.x = unit.x;
                b.y = unit.y;
                b.rot = unit.rotation;
                b.range = range;
                b.cone = cone;
                b.width = 10;
                beams.add(b);
            }
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            float r = range * rangeMul(l, slot);
            float rot = unit.rotation;
            s.addFocus(-focusCost);
            Units.nearbyEnemies(unit.team, unit.x, unit.y, r, u -> {
                if (Angles.within(rot, unit.angleTo(u), cone)) {
                    Lines.stroke(8,Color.white.cpy().a(0.15f));
                    Lines.line(unit.x,unit.y,u.x,u.y);
                    float dmg = damage * damageMul(l, slot);
                    if (RunState.active()) {
                        RunState.current.noteDamageDealt(dmg);
                    }
                    u.damage(dmg);
                    Fx.hitLancer.at(u.x, u.y);
                }
            });
            return true;
        }
    }

    public static class EchoForm extends DefectAbility {
        public EchoForm() {
            energyCost = 2f;
            maxCharges = 4;
            cooldown = 800f;
            noCycleRefresh = true;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            s.echoForm = true;
            toast("Echo Form ready");
            return true;
        }
    }

    public static class EvokeBasic extends DefectAbility {
        public EvokeBasic() {
            energyCost = 0f;
            maxCharges = 8;
            cooldown = 15f;
        }

        @Override
        protected boolean activate(Unit unit, DefectState s, PlayerLoadout l, int slot) {
            if (s.orbs.isEmpty()) {
                toast("No orbs");
                return false;
            }
            s.evokeNext(unit);
            return true;
        }
    }
}
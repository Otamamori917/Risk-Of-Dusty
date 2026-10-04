package riskod.world.abilites;

import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Groups;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import riskod.world.bullets.LensWarp;
import riskod.world.run.PlayerLoadout;
import riskod.world.unit.PlayerCharUnitType;

public class BulletReflectAbility extends ChargedAbility {
    public float radius = 48f;
    public Effect effect = Fx.coreBuildShockwave;

    public float reflectedLifeMin = 2,reflectedLifeMax= 2.4f,reflectedVelocityMin = 1.4f, reflectedVelocityMax = 1.8f,reflectedDamageMax = 1.2f, reflectedDamageMin = 0.8f;

    public BulletReflectAbility() {
        maxCharges = 2;
        cooldown = 180f;
        chargesOnReady = 1;
    }

    @Override
    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        super.update(unit, loadout, slot);
        float r = radius * rangeMul(loadout, 0);
        Drawf.dashCircle(unit.x,unit.y,r,unit.team.color);
        Drawf.dashCircle(unit.x,unit.y,r/2.5f,unit.team.color);
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout loadout, int slot) {
        float r = radius * rangeMul(loadout, slot);
        LensWarp.add(unit.x, unit.y, r * 2f, 3f, 5f, unit.rotation(), 180);
        Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, eb -> {
            if (eb.team != unit.team && eb.within(unit, r)) {
                BulletType bulletcpy = eb.type.copy();
                bulletcpy.pierce = false;
                bulletcpy.pierceBuilding = false;
                bulletcpy.removeAfterPierce = true;
                bulletcpy.fragOnHit = true;

                bulletcpy.create(unit, unit.team(),
                        eb.x, eb.y,
                        eb.rotation() - 180,
                        (eb.damage * Mathf.random(reflectedDamageMin, reflectedDamageMax))*damageMul(loadout,slot),
                        Mathf.random(reflectedVelocityMin, reflectedVelocityMax),
                        Mathf.random(reflectedLifeMin, reflectedLifeMax),
                        null);

                eb.type.fragOnAbsorb = false;
                if (eb.within(unit, r/2.5f)) {
                    toast("[stat]PERFECT COUNTER[]");
                    Sounds.drillImpact.at(unit);
                    refreshAll(unit, loadout);
                }
                eb.absorb();
            }
        });
        return true;
    }
}

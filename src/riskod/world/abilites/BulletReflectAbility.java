package riskod.world.abilites;

import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import riskod.world.bullets.LensWarp;
import riskod.world.run.PlayerLoadout;

public class BulletReflectAbility extends CounterAbility {
    public float radius = 48f;

    public float reflectedLifeMin = 2f, reflectedLifeMax = 2.4f;
    public float reflectedVelocityMin = 1.4f, reflectedVelocityMax = 1.8f;
    public float reflectedDamageMin = 0.8f, reflectedDamageMax = 1.2f;

    public BulletReflectAbility() {
        maxCharges = 2;
        cooldown = 180f;
        chargesOnReady = 1;
        perfectsNeeded = 3;
        counterWindow = 24f;
        streakBreakDamage = 15f;
    }

    @Override
    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        super.update(unit, loadout, slot);
        if (Vars.player == null || Vars.player.unit() != unit) return;

        float r = radius * rangeMul(loadout, slot);
        RingVis v = new RingVis();
        v.x = unit.x;
        v.y = unit.y;
        v.outer = r;
        v.inner = 0f;
        v.window = isCounterWindowOpen(unit);
        v.team = unit.team.color;
        rings.add(v);
    }

    @Override
    protected boolean onCounterActivate(Unit unit, PlayerLoadout loadout, int slot) {
        float r = radius * rangeMul(loadout, slot);
        LensWarp.add(unit.x, unit.y, r * 2f, 3f, 5f, unit.rotation(), 180f);

        Groups.bullet.intersect(unit.x - r, unit.y - r, r * 2f, r * 2f, eb -> {
            if (eb.team == unit.team || !eb.within(unit, r)) return;

            BulletType bulletcpy = eb.type.copy();
            bulletcpy.pierce = false;
            bulletcpy.pierceBuilding = false;
            bulletcpy.removeAfterPierce = true;
            bulletcpy.fragOnHit = true;

            bulletcpy.create(
                    unit, unit.team,
                    eb.x, eb.y,
                    eb.rotation() - 180f,
                    (eb.damage * Mathf.random(reflectedDamageMin, reflectedDamageMax)) * damageMul(loadout, slot),
                    Mathf.random(reflectedVelocityMin, reflectedVelocityMax),
                    Mathf.random(reflectedLifeMin, reflectedLifeMax),
                    null
            );

            eb.type.fragOnAbsorb = false;
            tryPerfectBullet(unit, loadout, eb);
            eb.absorb();
        });
        return true;
    }
}
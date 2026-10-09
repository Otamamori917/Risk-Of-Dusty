package riskod.world.unit;

import arc.struct.IntIntMap;
import mindustry.gen.Unit;
import riskod.world.run.RunState;

/** Companion drone whose bonus health, damage and healing are multiplied by the shrine count recorded when that drone was created by a chant. */
public class ChantDroneType extends CompanionDroneType {
    /// Shrine count recorded when each drone was created, by unit id; never changes afterwards.
    public static final IntIntMap shrinesById = new IntIntMap();

    /// Extra max health on top of the base health, multiplied by the drone's chant scale.
    public float bonusHealth;

    /// Extra attack damage on top of the base damage, multiplied by the drone's chant scale.
    public float bonusDamage;

    /// Extra healing on top of the base healing, multiplied by the drone's chant scale.
    public float bonusHeal;

    float baseHealth, baseHeal, baseDamage;

    public ChantDroneType(String name) {
        super(name);
    }

    @Override
    public void init() {
        super.init();
        baseHealth = health;
        baseHeal = healAmount;
        baseDamage = attackBullet != null ? attackBullet.damage : 0f;
    }

    public float scaleOf(Unit unit) {
        return shrinesById.get(unit.id, 0) * RunState.CHANT_SCALE_PER_SHRINE;
    }

    @Override
    public void update(Unit unit) {
        applyScale(unit);
        super.update(unit);
    }

    @Override
    public void killed(Unit unit) {
        super.killed(unit);
        shrinesById.remove(unit.id, 0);
    }

    void applyScale(Unit unit) {
        float s = scaleOf(unit);
        healAmount = baseHeal + bonusHeal * s;
        if (attackBullet != null) attackBullet.damage = baseDamage + bonusDamage * s;

        float want = baseHealth + bonusHealth * s;
        if (Math.abs(unit.maxHealth - want) > 0.01f) {
            float frac = unit.maxHealth > 0f ? unit.health / unit.maxHealth : 1f;
            unit.maxHealth(want);
            unit.health(want * frac);
        }
    }
}
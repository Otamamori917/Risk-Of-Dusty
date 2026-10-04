package riskod.world.abilites;

import arc.math.Mathf;
import mindustry.gen.Unit;
import riskod.world.run.PlayerLoadout;

public class DashAbility extends ChargedAbility {
    public float dashSpeed = 12f;
    public float dashNudge = 0.35f;

    public DashAbility() {
        maxCharges = 2;
        cooldown = 120f;
        chargesOnReady = 1;
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout loadout, int slot) {
        float ang = unit.rotation;
        float spd = dashSpeed * (loadout != null ? Math.max(0.25f, loadout.speedMul) : 1f);
        unit.vel.trns(ang, spd);
        unit.move(Mathf.cosDeg(ang) * spd * dashNudge, Mathf.sinDeg(ang) * spd * dashNudge);
        return true;
    }
}

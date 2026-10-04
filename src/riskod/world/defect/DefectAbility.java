package riskod.world.defect;

import mindustry.Vars;
import mindustry.gen.Unit;
import riskod.world.abilites.ChargedAbility;
import riskod.world.run.PlayerLoadout;

public abstract class DefectAbility extends ChargedAbility {
    public float energyCost = 1f;
    public float value;
    public Unit parent;

    public DefectAbility() {
        maxCharges = 4;
        chargesOnReady = 1;
        cooldown = 45f;
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
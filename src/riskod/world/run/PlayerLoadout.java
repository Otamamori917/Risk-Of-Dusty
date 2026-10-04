package riskod.world.run;

import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Unit;
import riskod.world.abilites.ChargedAbility;
import riskod.world.defect.DefectState;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.ui.RelicPickupToast;

public class PlayerLoadout {
    public static final int FLEX_SLOTS = 4;
    public static final int GEAR_SLOT = 4;
    public static final int SLOT_COUNT = 5;
    public static final int BONUS_ALL = 5;

    public final RelicType[] slots = new RelicType[SLOT_COUNT];
    public final Seq<RelicType> passives = new Seq<>();

    public int gearCharges;
    public float gearCooldownTimer;

    public float healthMul = 1f;
    public float speedMul = 1f;

    public int bonusGearCharges;
    public float gearCooldownMul = 1f;

    public float bonusLuck;
    public float luckMul = 1f;

    public final int[] slotBonusCharges = new int[FLEX_SLOTS];
    public final float[] slotCooldownMul = new float[FLEX_SLOTS];
    public final float[] slotDamageMul = new float[FLEX_SLOTS];
    public final float[] slotRangeMul = new float[FLEX_SLOTS];
    public final float[] slotReloadMul = new float[FLEX_SLOTS];

    public int bonusFocus;
    public int bonusOrbCapacity;
    public float bonusEnergyCap;
    public float pulseIntervalMul = 1f;
    public boolean plasmaBankPermanent;

    public GearType gear() {
        return slots[GEAR_SLOT] instanceof GearType g ? g : null;
    }

    public int effectiveGearMax() {
        GearType g = gear();
        return g == null ? 0 : Math.max(1, g.maxCharges + bonusGearCharges);
    }

    public float effectiveGearCooldown() {
        GearType g = gear();
        return g == null ? 60f : Math.max(1f, g.cooldown * gearCooldownMul);
    }

    public boolean hasPassive() {
        return passives.any();
    }

    public boolean hasPassive(RelicType type) {
        if (type == null) return passives.any();
        return passives.contains(type);
    }

    public RelicType takePassive(RelicType type) {
        if (passives.isEmpty()) return null;
        RelicType taken;
        if (type == null) {
            taken = passives.random();
        } else {
            int idx = passives.indexOf(type);
            if (idx < 0) return null;
            taken = passives.remove(idx);
            recompute();
            return taken;
        }
        passives.remove(taken);
        recompute();
        return taken;
    }

    public float effectiveLuck() {
        return bonusLuck * luckMul;
    }

    public int slotBonusCharges(int slot) {
        return slot >= 0 && slot < FLEX_SLOTS ? slotBonusCharges[slot] : 0;
    }

    public float slotCooldownMul(int slot) {
        return slot >= 0 && slot < FLEX_SLOTS ? slotCooldownMul[slot] : 1f;
    }

    public float slotDamageMul(int slot) {
        return slot >= 0 && slot < FLEX_SLOTS ? slotDamageMul[slot] : 1f;
    }

    public float slotRangeMul(int slot) {
        return slot >= 0 && slot < FLEX_SLOTS ? slotRangeMul[slot] : 1f;
    }

    public float slotReloadMul(int slot) {
        return slot >= 0 && slot < FLEX_SLOTS ? slotReloadMul[slot] : 1f;
    }

    public boolean tryPickup(RelicType relic) {
        return tryPickup(relic, false, 0);
    }

    public boolean tryPickup(RelicType relic, boolean replaceFlex, int flexIndex) {
        if (relic == null) return false;

        if (!relic.canDropForCurrentRun()) {
            RelicPickupToast.show(relic.localizedName, "Wrong hero for this relic", -1, null, "");
            return false;
        }

        if (relic.slotKind == RelicType.SlotKind.passive) {
            passives.add(relic);
            recompute();
            return true;
        }

        if (relic.slotKind == RelicType.SlotKind.gear) {
            slots[GEAR_SLOT] = relic;
            recompute();
            gearCharges = effectiveGearMax();
            gearCooldownTimer = 0f;
            return true;
        }

        int eq = relic.equipSlot;
        if (eq < 0 || eq >= FLEX_SLOTS) return false;
        slots[eq] = relic;
        recompute();
        return true;
    }

    /** One-shot pickup effects (grantFocus) + push Defect capacity from loadout. */
    public void onPickedUp(Unit unit, RelicType relic) {
        if (unit == null || relic == null) return;
        if (relic.grantFocus != 0) {
            DefectState s = DefectState.get(unit);
            if (s != null) s.addFocus(relic.grantFocus);
        }
        DefectState.refreshCapacity(unit, this);
    }

    public void recompute() {
        healthMul = 1f;
        speedMul = 1f;
        bonusGearCharges = 0;
        gearCooldownMul = 1f;
        bonusLuck = 0f;
        luckMul = 1f;

        bonusFocus = 0;
        bonusOrbCapacity = 0;
        bonusEnergyCap = 0f;
        pulseIntervalMul = 1f;
        plasmaBankPermanent = false;

        for (int i = 0; i < FLEX_SLOTS; i++) {
            slotBonusCharges[i] = 0;
            slotCooldownMul[i] = 1f;
            slotDamageMul[i] = 1f;
            slotRangeMul[i] = 1f;
            slotReloadMul[i] = 1f;
        }

        for (RelicType r : passives) {
            applyGlobal(r);
            applySlotBonuses(r, true);
        }
        for (RelicType r : slots) {
            if (r == null) continue;
            applyGlobal(r);
            applySlotBonuses(r, false);
        }

        if (gear() != null) {
            gearCharges = Math.min(gearCharges, effectiveGearMax());
        }
        if (RunState.active()) {
            RunState.current.applyLevelToLoadout(this);
        }
    }

    void applyGlobal(RelicType r) {
        healthMul *= r.healthMul;
        speedMul *= r.speedMul;
        bonusGearCharges += r.bonusGearCharges;
        gearCooldownMul *= r.gearCooldownMul;
        bonusLuck += r.bonusLuck;
        luckMul *= r.luckMul;

        bonusFocus += r.bonusFocus;
        bonusOrbCapacity += r.bonusOrbCapacity;
        bonusEnergyCap += r.bonusEnergyCap;
        pulseIntervalMul *= r.pulseIntervalMul;
        plasmaBankPermanent = r.plasmaBankPermanent;
    }

    void applySlotBonuses(RelicType r, boolean fromPassive) {
        int t = r.bonusSlot;

        if (fromPassive) {
            if (t == -1 || t == BONUS_ALL) {
                for (int i = 0; i < FLEX_SLOTS; i++) addSlotBonus(i, r);
                bonusGearCharges += r.bonusAbilityCharges;
                gearCooldownMul *= r.abilityCooldownMul;
            } else if (t >= 0 && t < FLEX_SLOTS) {
                addSlotBonus(t, r);
            }
            return;
        }

        if (t >= 0 && t < FLEX_SLOTS) {
            addSlotBonus(t, r);
        } else if (r.equipSlot >= 0 && r.equipSlot < FLEX_SLOTS) {
            addSlotBonus(r.equipSlot, r);
        }
    }

    void addSlotBonus(int slot, RelicType r) {
        slotBonusCharges[slot] += r.bonusAbilityCharges;
        slotCooldownMul[slot] *= r.abilityCooldownMul;
        slotDamageMul[slot] *= r.abilityDamageMul;
        slotRangeMul[slot] *= r.abilityRangeMul;
        slotReloadMul[slot] *= r.abilityCooldownMul;
    }

    public void updateGear(Unit unit) {
        GearType g = gear();
        if (g == null) return;
        int max = effectiveGearMax();
        if (gearCharges < max) {
            gearCooldownTimer += Time.delta;
            if (gearCooldownTimer >= effectiveGearCooldown()) {
                gearCooldownTimer = 0f;
                gearCharges = Math.min(max, gearCharges + g.chargesOnReady);
            }
        }
    }

    public boolean tryActivateSlot(Unit unit, int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;

        if (slot == GEAR_SLOT) {
            GearType g = gear();
            if (g == null || gearCharges <= 0) return false;
            g.activate(unit, this);
            gearCharges--;
            return true;
        }

        RelicType r = slots[slot];
        if (r == null) return false;
        if (r.ability instanceof ChargedAbility charged) {
            return charged.tryUse(unit, this, slot);
        }
        return false;
    }

    public void write(Writes write) {
        write.i(passives.size);
        for (RelicType r : passives) {
            write.str(r.name);
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            write.str(slots[i] == null ? "" : slots[i].name);
        }
        write.i(gearCharges);
        write.f(gearCooldownTimer);
    }

    public void read(Reads read) {
        passives.clear();
        int n = read.i();
        for (int i = 0; i < n; i++) {
            RelicType r = find(read.str());
            if (r != null) passives.add(r);
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            String id = read.str();
            slots[i] = id.isEmpty() ? null : find(id);
        }
        gearCharges = read.i();
        gearCooldownTimer = read.f();
        recompute();
    }

    public static RelicType find(String name) {
        return RelicType.all.find(r -> r.name.equals(name));
    }
}
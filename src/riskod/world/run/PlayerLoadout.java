package riskod.world.run;

import arc.Core;
import arc.func.Boolf;
import arc.math.Mathf;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Unit;
import riskod.content.RiskodCont;
import riskod.world.abilites.ChargedAbility;
import riskod.world.defect.DefectState;
import riskod.world.relic.ChantRelic;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.ui.RelicPickupToast;

import java.util.Arrays;

public class PlayerLoadout {
    public static final int FLEX_SLOTS = 4;
    public static final int GEAR_SLOT = 4;
    public static final int SLOT_COUNT = 5;

    static final int G_HEALTH = 0, G_SPEED = 1, G_GEAR_CD = 2, G_LUCK = 3, G_PULSE = 4, G_COUNT = 5;
    static final int S_COOLDOWN = 0, S_DAMAGE = 1, S_RANGE = 2, S_COUNT = 3;

    public final RelicType[] slots = new RelicType[SLOT_COUNT];
    public final Seq<RelicType> passives = new Seq<>();
    public final IntSeq passiveShrines = new IntSeq();

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

    public int bonusFocus;
    public int bonusOrbCapacity;
    public float bonusEnergyCap;
    public float pulseIntervalMul = 1f;
    public boolean plasmaBankPermanent;


    public int obeliskLastSlot = -1;

    public int obeliskStacks;
    public static final float OBELISK_HARDCAP = 0.44f;
    public static final float OBELISK_BONUS = 0.01f;

    public int negativeObeliskStacks;
    public static final float NEGATIVE_OBELISK_HARDCAP = 1.48f;
    public static final float NEGATIVE_OBELISK_BONUS = 0.01f;

    final float[] chantGlobal = new float[G_COUNT];
    final float[][] chantSlot = new float[FLEX_SLOTS][S_COUNT];

    public GearType gear() {
        return slots[GEAR_SLOT] instanceof GearType g ? g : null;
    }

    public int effectiveGearMax() {
        GearType g = gear();
        return g == null ? 0 : Math.max(1, g.maxCharges + bonusGearCharges);
    }

    public float effectiveGearCooldown() {
        GearType g = gear();
        float base = g == null ? 60f : g.cooldown * gearCooldownMul;
        return Math.max(1f, base * obeliskCdMul());
    }

    public float obeliskCdMul() {
        if (!hasPassiveFlag(r -> r.obelisk)) return 1f;
        return Math.max(OBELISK_HARDCAP, 1f - obeliskStacks * OBELISK_BONUS);
    }

    public float negativeObeliskDmgMul() {
        if (!hasPassiveFlag(r -> r.negativeObelisk)) return 1f;
        return Math.min(NEGATIVE_OBELISK_HARDCAP, 1+(negativeObeliskStacks * NEGATIVE_OBELISK_BONUS));
    }

    public boolean hasPassive() {
        return passives.any();
    }

    public boolean hasPassive(RelicType type) {
        if (type == null) return passives.any();
        return passives.contains(type);
    }

    public boolean hasRelic(RelicType type) {
        if (type == null) return false;
        if (passives.contains(type)) return true;
        for (RelicType r : slots) if (r == type) return true;
        return false;
    }

    public boolean hasPassiveFlag(Boolf<RelicType> test) {
        for (RelicType r : passives) {
            if (test.get(r)) return true;
        }
        return false;
    }

    public boolean blocksUnstackable(RelicType type) {
        return type != null && type.unstackable && hasRelic(type);
    }

    public RelicType takePassive(RelicType type) {
        if (passives.isEmpty()) return null;
        int idx = type == null ? Mathf.random(passives.size - 1) : passives.indexOf(type);
        if (idx < 0) return null;
        if (passives.get(idx) == RiskodCont.obelisk){
            obeliskLastSlot = -1;
            obeliskStacks = 0;
        }
        if (passives.get(idx) == RiskodCont.negativeObelisk){
            obeliskLastSlot = -1;
            negativeObeliskStacks = 0;
        }
        RelicType taken = passives.remove(idx);
        if (idx < passiveShrines.size) passiveShrines.removeIndex(idx);
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
        float m = slot >= 0 && slot < FLEX_SLOTS ? slotCooldownMul[slot] : 1f;
        return m * obeliskCdMul();
    }

    public float slotDamageMul(int slot) {
        float m = slot >= 0 && slot < FLEX_SLOTS ? slotDamageMul[slot] : 1f;
        return m * negativeObeliskDmgMul();
    }

    public float slotRangeMul(int slot) {
        return slot >= 0 && slot < FLEX_SLOTS ? slotRangeMul[slot] : 1f;
    }

    int shrinesAt(int passiveIndex) {
        return passiveIndex >= 0 && passiveIndex < passiveShrines.size ? passiveShrines.get(passiveIndex) : 0;
    }

    public boolean tryPickup(RelicType relic) {
        return tryPickup(relic, false, 0, 0);
    }

    public boolean tryPickup(RelicType relic, boolean replaceFlex, int flexIndex) {
        return tryPickup(relic, replaceFlex, flexIndex, 0);
    }

    public boolean tryPickup(RelicType relic, boolean replaceFlex, int flexIndex, int shrines) {
        if (relic == null) return false;

        if (!relic.canDropForCurrentRun()) {
            RelicPickupToast.show(
                    RelicPickupToast.ToastChannel.MAIN
                    ,relic.localizedName,
                    "Wrong hero for this relic",
                    -1,
                    null,
                    "");
            return false;
        }
        if (blocksUnstackable(relic)) {
            RelicPickupToast.show(RelicPickupToast.ToastChannel.MAIN,
                    relic.localizedName
                    ,
                    "Already owned",
                    -1,
                    null,
                    "");
            return false;
        }

        if (relic.slotKind == RelicType.SlotKind.passive) {
            passives.add(relic);
            passiveShrines.add(shrines);
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

        int eq = relic.equipApplyto.ordinal();
        if (eq >= FLEX_SLOTS) return false;
        slots[eq] = relic;
        recompute();
        return true;
    }

    public void onPickedUp(Unit unit, RelicType relic) {
        onPickedUp(unit, relic, 0);
    }

    public void onPickedUp(Unit unit, RelicType relic, int shrines) {
        if (unit == null || relic == null) return;
        if (relic.grantFocus != 0) {
            DefectState s = DefectState.get(unit);
            if (s != null) {
                int focus = relic instanceof ChantRelic c ? scaledInt(relic.grantFocus, c.scale(shrines)) : relic.grantFocus;
                s.addFocus(focus);
            }
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

        Arrays.fill(chantGlobal, 0f);
        for (float[] row : chantSlot) Arrays.fill(row, 0f);

        for (int i = 0; i < FLEX_SLOTS; i++) {
            slotBonusCharges[i] = 0;
            slotCooldownMul[i] = 1f;
            slotDamageMul[i] = 1f;
            slotRangeMul[i] = 1f;
        }

        boolean smeared = false;
        boolean stunt = false;

        for (int i = 0; i < passives.size; i++) {
            RelicType r = passives.get(i);
            int shrines = shrinesAt(i);
            applyGlobal(r, shrines);
            applySlotBonuses(r, true, shrines);
            if (r.smeared) smeared = true;
            if (r.stuntMan) stunt = true;
        }
        for (RelicType r : slots) {
            if (r == null) continue;
            applyGlobal(r, 0);
            applySlotBonuses(r, false, 0);
        }

        if (smeared) {
            for (int i = 0; i < passives.size; i++) {
                RelicType r = passives.get(i);
                int shrines = shrinesAt(i);
                RelicType.ApplyType t = r.bonusApplyTo;
                if (t == RelicType.ApplyType.allMain || t == RelicType.ApplyType.ALL || t == RelicType.ApplyType.gear) {
                    continue;
                }
                if (t == RelicType.ApplyType.primary) {
                    addSlotBonus(RelicType.ApplyType.utility.ordinal(), r, shrines);
                }
                if (t == RelicType.ApplyType.secondary) {
                    addSlotBonus(RelicType.ApplyType.special.ordinal(), r, shrines);
                }
                if (t == RelicType.ApplyType.utility) {
                    addSlotBonus(RelicType.ApplyType.primary.ordinal(), r, shrines);
                }
                if (t == RelicType.ApplyType.special) {
                    addSlotBonus(RelicType.ApplyType.secondary.ordinal(), r, shrines);
                }
            }
        }

        healthMul *= chantFactor(chantGlobal[G_HEALTH]);
        speedMul *= chantFactor(chantGlobal[G_SPEED]);
        gearCooldownMul *= chantFactor(chantGlobal[G_GEAR_CD]);
        luckMul *= chantFactor(chantGlobal[G_LUCK]);
        pulseIntervalMul *= chantFactor(chantGlobal[G_PULSE]);
        for (int i = 0; i < FLEX_SLOTS; i++) {
            slotCooldownMul[i] *= chantFactor(chantSlot[i][S_COOLDOWN]);
            slotDamageMul[i] *= chantFactor(chantSlot[i][S_DAMAGE]);
            slotRangeMul[i] *= chantFactor(chantSlot[i][S_RANGE]);
        }

        if (stunt) applyStuntMan();

        if (gear() != null) {
            gearCharges = Math.min(gearCharges, effectiveGearMax());
        }
        if (RunState.active()) {
            RunState.current.applyLevelToLoadout(this);
        }
    }

    void applyStuntMan() {
        for (int i = 0; i < FLEX_SLOTS; i++) {
            slotDamageMul[i] *= 1.20f;
            slotRangeMul[i] *= 1.20f;

            int baseMax = 1;
            RelicType slotR = slots[i];
            if (slotR != null && slotR.ability instanceof ChargedAbility c) {
                baseMax = c.maxCharges;
            }
            if (baseMax == 2) {
                slotBonusCharges[i] -= 1;
                slotCooldownMul[i] *= 1.10f;
            } else if (baseMax == 1) {
                slotCooldownMul[i] *= 1.20f;
            } else {
                slotBonusCharges[i] -= 2;
            }
        }
        GearType g = gear();
        if (g != null) {
            if (g.maxCharges == 2) {
                bonusGearCharges -= 1;
                gearCooldownMul *= 1.10f;
            } else if (g.maxCharges == 1) {
                gearCooldownMul *= 1.20f;
            } else {
                bonusGearCharges -= 2;
            }
        }
    }

    static float chantFactor(float added) {
        if (added == 0f) return 1f;
        return Math.max(0.1f, 1f + added);
    }

    static int scaledInt(int value, float scale) {
        return Math.round(value * scale);
    }

    void applyGlobal(RelicType r, int shrines) {
        if (r instanceof ChantRelic c) {
            float s = c.scale(shrines);
            chantGlobal[G_HEALTH] += (r.healthMul - 1f) * s;
            chantGlobal[G_SPEED] += (r.speedMul - 1f) * s;
            bonusGearCharges += scaledInt(r.bonusGearCharges, s);
            chantGlobal[G_GEAR_CD] += (r.gearCooldownMul - 1f) * s;
            bonusLuck += r.bonusLuck * s;
            chantGlobal[G_LUCK] += (r.luckMul - 1f) * s;
            bonusFocus += scaledInt(r.bonusFocus, s);
            bonusOrbCapacity += scaledInt(r.bonusOrbCapacity, s);
            bonusEnergyCap += r.bonusEnergyCap * s;
            chantGlobal[G_PULSE] += (r.pulseIntervalMul - 1f) * s;
            if (r.plasmaBankPermanent) plasmaBankPermanent = true;
            return;
        }

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
        if (r.plasmaBankPermanent) plasmaBankPermanent = true;
    }

    void applySlotBonuses(RelicType relic, boolean fromPassive, int shrines) {
        RelicType.ApplyType targetBonus = relic.bonusApplyTo;

        if (fromPassive) {
            if (targetBonus == RelicType.ApplyType.allMain || targetBonus == RelicType.ApplyType.ALL) {
                for (int slot = 0; slot < FLEX_SLOTS; slot++) {
                    addSlotBonus(slot, relic, shrines);
                }
            } else if (targetBonus != RelicType.ApplyType.gear) {
                addSlotBonus(targetBonus.ordinal(), relic, shrines);
            }

            if (targetBonus == RelicType.ApplyType.ALL) {
                if (relic instanceof ChantRelic c) {
                    float s = c.scale(shrines);
                    bonusGearCharges += scaledInt(relic.bonusAbilityCharges, s);
                    chantGlobal[G_GEAR_CD] += (relic.abilityCooldownMul - 1f) * s;
                } else {
                    bonusGearCharges += relic.bonusAbilityCharges;
                    gearCooldownMul *= relic.abilityCooldownMul;
                }
            }
        } else {
            if (targetBonus.ordinal() < FLEX_SLOTS) {
                addSlotBonus(targetBonus.ordinal(), relic, shrines);
            }
            if (relic.equipApplyto.ordinal() < FLEX_SLOTS) {
                addSlotBonus(relic.equipApplyto.ordinal(), relic, shrines);
            }
        }
    }

    void addSlotBonus(int slot, RelicType r, int shrines) {
        if (slot < 0 || slot >= FLEX_SLOTS) return;
        if (r instanceof ChantRelic c) {
            float s = c.scale(shrines);
            slotBonusCharges[slot] += scaledInt(r.bonusAbilityCharges, s);
            chantSlot[slot][S_COOLDOWN] += (r.abilityCooldownMul - 1f) * s;
            chantSlot[slot][S_DAMAGE] += (r.abilityDamageMul - 1f) * s;
            chantSlot[slot][S_RANGE] += (r.abilityRangeMul - 1f) * s;
            return;
        }
        slotBonusCharges[slot] += r.bonusAbilityCharges;
        slotCooldownMul[slot] *= r.abilityCooldownMul;
        slotDamageMul[slot] *= r.abilityDamageMul;
        slotRangeMul[slot] *= r.abilityRangeMul;
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

        boolean ok;
        if (slot == GEAR_SLOT) {
            GearType g = gear();
            if (g == null || gearCharges <= 0) return false;
            g.activate(unit, this);
            gearCharges--;
            ok = true;
        } else {
            RelicType r = slots[slot];
            if (r == null) return false;
            if (!(r.ability instanceof ChargedAbility charged)) return false;
            ok = charged.tryUse(unit, this, slot);
        }
        if (ok) noteObelisk(slot);
        return ok;
    }

    void noteObelisk(int slot) {
        if (!hasPassiveFlag(r -> r.obelisk || r.negativeObelisk)) return;
        if (obeliskLastSlot == slot) {

            negativeObeliskStacks++;
            if (obeliskStacks != 0 && hasPassiveFlag(r -> r.obelisk)) RelicPickupToast.show(RelicPickupToast.ToastChannel.RELIC, RiskodCont.obelisk.localizedName, "[scarlet]Bonus Lost[]", -1, RiskodCont.obelisk.icon, "");
            if (hasPassiveFlag(r -> r.negativeObelisk)) RelicPickupToast.show(RelicPickupToast.ToastChannel.RELIC2, RiskodCont.negativeObelisk.localizedName, "[stat]Bonus: "+ (int) ((Math.min(NEGATIVE_OBELISK_HARDCAP,1+(negativeObeliskStacks * NEGATIVE_OBELISK_BONUS)))*100)+"%[]", -1, RiskodCont.negativeObelisk.icon, "");
            obeliskStacks = 0;

            obeliskLastSlot = slot;
        } else {

            obeliskStacks++;
            if (hasPassiveFlag(r -> r.obelisk)) RelicPickupToast.show(RelicPickupToast.ToastChannel.RELIC, RiskodCont.obelisk.localizedName, "[stat]Bonus: "+ (int) ((Math.min(2-OBELISK_HARDCAP,1+(obeliskStacks * OBELISK_BONUS)))*100)+"%[]", -1, RiskodCont.obelisk.icon, "");
            if (negativeObeliskStacks != 0 && hasPassiveFlag(r -> r.negativeObelisk)) RelicPickupToast.show(RelicPickupToast.ToastChannel.RELIC2, RiskodCont.negativeObelisk.localizedName, "[scarlet]Bonus Lost[]", -1, RiskodCont.negativeObelisk.icon, "");
            negativeObeliskStacks = 0;

            obeliskLastSlot = slot;
        }
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
        write.i(passives.size);
        for (int i = 0; i < passives.size; i++) {
            write.i(shrinesAt(i));
        }
        write.i(obeliskStacks);
        write.i(obeliskLastSlot);
    }

    public void read(Reads read) {
        passives.clear();
        passiveShrines.clear();

        int n = read.i();
        String[] names = new String[n];
        for (int i = 0; i < n; i++) {
            names[i] = read.str();
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            String id = read.str();
            slots[i] = id.isEmpty() ? null : find(id);
        }
        gearCharges = read.i();
        gearCooldownTimer = read.f();

        IntSeq saved = new IntSeq();
        try {
            int m = read.i();
            for (int i = 0; i < m; i++) saved.add(read.i());
        } catch (Throwable ignored) {
        }

        for (int i = 0; i < n; i++) {
            RelicType r = find(names[i]);
            if (r == null) continue;
            passives.add(r);
            passiveShrines.add(i < saved.size ? saved.get(i) : 0);
        }
        try {
            obeliskStacks = read.i();
            obeliskLastSlot = read.i();
        } catch (Throwable ignored) {
            obeliskStacks = 0;
            obeliskLastSlot = -1;
        }
        recompute();
    }

    public static RelicType find(String name) {
        return RelicType.all.find(r -> r.name.equals(name));
    }
}
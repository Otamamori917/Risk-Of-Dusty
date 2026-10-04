package riskod.world.defect;

import arc.struct.IntMap;
import arc.struct.Seq;
import mindustry.gen.Unit;
import riskod.world.run.PlayerLoadout;
import riskod.world.unit.PlayerCharUnitType;

public class DefectState {
    public static final float ORB_RANGE = 96f;
    public static final float DEFAULT_ENERGY_CAP = 3f;
    public static final float DEFAULT_ORB_CAP = 3f;
    public static final float PULSE_INTERVAL = 180f;
    public static final int PLASMA_BANK_PULSES = 2;

    public static final IntMap<DefectState> states = new IntMap<>();

    public float energy = DEFAULT_ENERGY_CAP;
    public float energyCap = DEFAULT_ENERGY_CAP;
    public float plasmaBank;
    public int plasmaBankPulsesLeft;
    public boolean plasmaBankPermanent;

    public int focus;
    public int relicFocus;
    public int orbCapacity = (int) DEFAULT_ORB_CAP;
    public float pulseIntervalMul = 1f;

    public final Seq<Orb> orbs = new Seq<>();

    public float pulseTimer;
    public int lightningChanneledSector;

    public int multicastExtra;
    public boolean echoForm;
    public int bufferStacks;
    public float staticDischargeTime;
    public float staticDischargeCd;

    public float lastHealth = -1f;
    public float lastShield = -1f;

    public static DefectState get(Unit unit) {
        if (unit == null) return null;
        DefectState s = states.get(unit.id);
        if (s == null) {
            s = new DefectState();
            states.put(unit.id, s);
        }
        return s;
    }

    public static void clear(Unit unit) {
        if (unit != null) states.remove(unit.id);
    }

    public static void clearAll() {
        states.clear();
    }

    public int totalFocus() {
        return focus + relicFocus;
    }

    public void addFocus(int amount) {
        focus += amount;
    }

    public void addEnergy(float amount) {
        addEnergy(amount, false);
    }

    public void addEnergy(float amount, boolean fromPlasma) {
        if (amount == 0f) return;
        energy += amount;
        if (fromPlasma && amount > 0f) {
            plasmaBank += amount;
            if (!plasmaBankPermanent) {
                plasmaBankPulsesLeft = PLASMA_BANK_PULSES;
            }
        }
        syncPlasmaBank();
    }

    public boolean spendEnergy(float cost) {
        if (cost <= 0f) return true;
        if (energy < cost) return false;
        energy -= cost;
        syncPlasmaBank();
        return true;
    }

    void syncPlasmaBank() {
        plasmaBank = Math.max(0f, Math.min(plasmaBank, energy));
        if (plasmaBank <= 0.001f) {
            plasmaBank = 0f;
            if (!plasmaBankPermanent) plasmaBankPulsesLeft = 0;
        }
    }

    public void addShield(Unit unit, float amount) {
        if (unit == null || amount <= 0f) return;
        unit.shield(unit.shield + amount);
    }

    public int f(int base) {
        return base + totalFocus();
    }

    public float f(float base) {
        return base + totalFocus();
    }

    public void channel(Unit unit, OrbType type) {
        channel(unit, type, 1);
    }

    public void channel(Unit unit, OrbType type, int count) {
        if (unit == null || type == null || count <= 0) return;
        for (int i = 0; i < count; i++) {
            while (orbs.size >= Math.max(1, orbCapacity)) {
                evokeOldest(unit);
            }
            Orb orb = new Orb(type);
            type.onChannel(unit, this, orb);
            orbs.add(orb);
            if (type == OrbType.lightning) {
                lightningChanneledSector++;
            }
        }
    }

    public void evokeOldest(Unit unit) {
        if (orbs.isEmpty()) return;
        evokeNext(unit, true);
    }

    public void evokeNext(Unit unit) {
        evokeNext(unit, true);
    }

    public void evokeNext(Unit unit, boolean consumeFlags) {
        if (orbs.isEmpty()) return;

        int times = 1;
        if (consumeFlags) {
            if (multicastExtra > 0) {
                times = multicastExtra + 1;
                multicastExtra = 0;
            }
        }

        Orb orb = orbs.remove(0);
        if (orb == null || orb.type == null) return;

        for (int i = 0; i < times; i++) {
            orb.type.evoke(unit, this, orb);
        }
    }

    public void triggerPassives(Unit unit) {
        for (Orb orb : orbs) {
            if (orb != null && orb.type != null) {
                orb.type.passive(unit, this, orb);
            }
        }
    }

    public void triggerPassivesOf(Unit unit, OrbType type, int times) {
        if (type == null || times <= 0) return;
        for (int t = 0; t < times; t++) {
            for (Orb orb : orbs) {
                if (orb != null && orb.type == type) {
                    orb.type.passive(unit, this, orb);
                }
            }
        }
    }

    public int removeAllOrbs() {
        int n = orbs.size;
        orbs.clear();
        return n;
    }

    public void pulse(Unit unit) {
        if (plasmaBank > 0f && !plasmaBankPermanent) {
            if (plasmaBankPulsesLeft <= 0) {
                plasmaBank = 0f;
            } else {
                plasmaBankPulsesLeft--;
            }
        }

        energy = energyCap + plasmaBank;
        triggerPassives(unit);
    }

    public void update(Unit unit) {
        if (unit == null) return;

        float interval = Math.max(30f, PULSE_INTERVAL * Math.max(0.05f, pulseIntervalMul));
        pulseTimer += mindustry.Vars.state.isPaused() ? 0f : arc.util.Time.delta;
        if (pulseTimer >= interval) {
            pulseTimer = 0f;
            pulse(unit);
        }

        if (staticDischargeTime > 0f) {
            staticDischargeTime -= arc.util.Time.delta;
            if (staticDischargeCd > 0f) staticDischargeCd -= arc.util.Time.delta;
        }

        trackDamage(unit);
    }

    void trackDamage(Unit unit) {
        float hp = unit.health;

        if (lastHealth < 0f) {
            lastHealth = hp;
            lastShield = unit.shield;
            return;
        }

        float healthLost = lastHealth - hp;
        if (healthLost > 0.001f) {
            if (bufferStacks > 0) {
                unit.health += healthLost;
                bufferStacks--;
                PlayerCharUnitType.syncHealth(unit);
            } else {
                onUnblocked(unit, healthLost);
            }
        }

        lastHealth = unit.health;
        lastShield = unit.shield;
    }

    void onUnblocked(Unit unit, float amount) {
        if (staticDischargeTime > 0f && staticDischargeCd <= 0f) {
            channel(unit, OrbType.lightning);
            staticDischargeCd = 10f;
        }
    }

    public void onSectorAdvance() {
        lightningChanneledSector = 0;
    }

    public static void refreshCapacity(Unit unit, PlayerLoadout loadout) {
        DefectState s = get(unit);
        if (s == null) return;

        s.orbCapacity = (int) DEFAULT_ORB_CAP;
        s.energyCap = DEFAULT_ENERGY_CAP;
        s.relicFocus = 0;
        s.pulseIntervalMul = 1f;
        s.plasmaBankPermanent = false;

        if (loadout != null) {
            s.orbCapacity += loadout.bonusOrbCapacity;
            s.energyCap += loadout.bonusEnergyCap;
            s.relicFocus = loadout.bonusFocus;
            s.pulseIntervalMul = Math.max(0.05f, loadout.pulseIntervalMul);
            s.plasmaBankPermanent = loadout.plasmaBankPermanent;
        }

        s.orbCapacity = Math.max(1, s.orbCapacity);
        s.energyCap = Math.max(1f, s.energyCap);

        while (s.orbs.size > s.orbCapacity) {
            s.evokeOldest(unit);
        }
        s.syncPlasmaBank();
    }
}
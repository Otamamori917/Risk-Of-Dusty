package riskod.world.abilites;

import arc.struct.IntMap;
import arc.struct.ObjectSet;
import arc.util.Time;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.run.PlayerLoadout;

public class ChargedAbility extends Ability {
    public static final IntMap<ChargeState> states = new IntMap<>();

    public int maxCharges = 1;
    public float cooldown = 120f;
    public int chargesOnReady = 1;

    public float healAmount = 0f;

    /** If true, kit swap does not reset this ability's charges/cooldown ticking. */
    public boolean noCycleRefresh = false;

    public static class ChargeState {
        public int charges;
        public float timer;
        public boolean inited;
    }

    public void refreshAll(Unit unit,PlayerLoadout l){
        ObjectSet<ChargedAbility> seen = new ObjectSet<>();
        for (int i = 0; i < PlayerLoadout.SLOT_COUNT; i++) {
            var r = l.slots[i];
            if (r != null && r.ability instanceof ChargedAbility charged) {
                if (seen.add(charged)) charged.refill(unit, l, i);
            }
        }
        KitSwapAbility swap = KitSwapAbility.find(l);
        if (swap != null && swap.kits != null) {
            for (var kit : swap.kits) {
                if (kit == null) continue;
                for (int i = 0; i < KitSwapAbility.KIT_FLEX; i++) {
                    var r = kit.slot(i);
                    if (r != null && r.ability instanceof ChargedAbility charged) {
                        if (seen.add(charged)) charged.refill(unit, l, i);
                    }
                }
            }
        }
    }

    public int stateKey(Unit unit) {
        return unit.id * 31 + System.identityHashCode(this);
    }

    public ChargeState state(Unit unit) {
        int key = stateKey(unit);
        ChargeState s = states.get(key);
        if (s == null) {
            s = new ChargeState();
            states.put(key, s);
        }
        return s;
    }

    public int effectiveMax(PlayerLoadout loadout, int slot) {
        int bonus = loadout == null ? 0 : loadout.slotBonusCharges(slot);
        return Math.max(1, maxCharges + bonus);
    }

    public float effectiveCooldown(PlayerLoadout loadout, int slot) {
        float cdMul = loadout == null ? 1f : loadout.slotCooldownMul(slot);
        float relMul = loadout == null ? 1f : loadout.slotReloadMul(slot);
        return Math.max(1f, cooldown * cdMul * relMul);
    }

    public float damageMul(PlayerLoadout loadout, int slot) {
        return loadout == null ? 1f : loadout.slotDamageMul(slot);
    }

    public float rangeMul(PlayerLoadout loadout, int slot) {
        return loadout == null ? 1f : loadout.slotRangeMul(slot);
    }

    @Override
    public void update(Unit unit) {
        PlayerLoadout loadout = unit.type instanceof PlayerCharUnitType
                ? PlayerCharUnitType.loadout(unit) : null;
        update(unit, loadout, 0);
    }

    public void refill(Unit unit, PlayerLoadout loadout, int slot) {
        ChargeState s = state(unit);
        s.charges = effectiveMax(loadout, slot);
        s.timer = 0f;
    }

    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        ChargeState s = state(unit);
        int max = effectiveMax(loadout, slot);
        if (!s.inited) {
            s.charges = max;
            s.inited = true;
        }
        if (s.charges < max) {
            s.timer += Time.delta;
            if (s.timer >= effectiveCooldown(loadout, slot)) {
                s.timer = 0f;
                s.charges = Math.min(max, s.charges + chargesOnReady);
            }
        }
    }

    public boolean tryUse(Unit unit) {
        PlayerLoadout l = unit.type instanceof PlayerCharUnitType
                ? PlayerCharUnitType.loadout(unit) : null;
        return tryUse(unit, l, 0);
    }

    public boolean tryUse(Unit unit, PlayerLoadout loadout, int slot) {
        ChargeState s = state(unit);
        int max = effectiveMax(loadout, slot);
        if (!s.inited) {
            s.charges = max;
            s.inited = true;
        }
        if (s.charges <= 0) return false;
        if (!activate(unit, loadout, slot)) return false;
        s.charges--;
        return true;
    }

    public boolean activate(Unit unit, PlayerLoadout loadout, int slot) {
        if (healAmount > 0f) {
            unit.heal(healAmount);
        }
        return onActivate(unit, loadout, slot);
    }

    public boolean onActivate(Unit unit, PlayerLoadout loadout, int slot) {
        return true;
    }

    protected static void toast(String msg) {
        if (Vars.player != null && Vars.ui != null) {
            Vars.ui.showInfoToast(msg, 1.2f);
        }
    }
}
package riskod.world.abilites;

import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import riskod.world.meta.UnlockReq;
import riskod.world.relic.RelicType;
import riskod.world.run.HeroKitPrefs;
import riskod.world.run.PlayerLoadout;
import riskod.world.ui.RelicPickupToast;

public class KitSwapAbility extends ChargedAbility {

    public static final int KIT_SLOT = 3;
    public static final int KIT_FLEX = 3;

    /** Full pool of kits (may be larger than the active rotation). */
    public Kits[] kits = {};

    /**
     * How many kits rotate in a run. Must stay fixed for a hero.
     * Defaults to {@code kits.length} in the constructor; set lower if the pool grows with unlocks.
     */
    public int baseKitCount = -1;

    private static final arc.struct.IntMap<UnitKitState> states = new arc.struct.IntMap<>();

    public KitSwapAbility(Kits... kt) {
        kits = kt != null ? kt : new Kits[0];
        baseKitCount = kits.length;
        maxCharges = 8;
        chargesOnReady = 1;
        cooldown = 20f;
    }

    public static void clearAll() {
        states.clear();
    }

    public static void clear(Unit unit) {
        if (unit != null) states.remove(unit.id);
    }

    public static KitSwapAbility find(PlayerLoadout l) {
        if (l == null) return null;
        RelicType r = l.slots[KIT_SLOT];
        if (r != null && r.ability instanceof KitSwapAbility k) return k;
        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            RelicType s = l.slots[i];
            if (s != null && s.ability instanceof KitSwapAbility k) return k;
        }
        return null;
    }

    UnitKitState kitState(Unit unit) {
        UnitKitState s = states.get(unit.id);
        if (s == null) {
            s = new UnitKitState();
            states.put(unit.id, s);
        }
        return s;
    }

    int baseCount() {
        if (kits.length == 0) return 0;
        int b = baseKitCount > 0 ? baseKitCount : kits.length;
        return Math.min(b, kits.length);
    }

    /**
     * Kits currently in the rotation for this unit (from saved selection / defaults).
     * Length is always {@link #baseCount()}.
     */
    public Kits[] activeKits(Unit unit) {
        if (kits.length == 0 || unit == null) return new Kits[0];
        UnitKitState s = kitState(unit);
        ensureSelection(unit, s);
        Kits[] out = new Kits[s.selected.size];
        for (int i = 0; i < s.selected.size; i++) {
            int idx = s.selected.get(i);
            out[i] = idx >= 0 && idx < kits.length ? kits[idx] : null;
        }
        return out;
    }

    void ensureSelection(Unit unit, UnitKitState s) {
        int base = baseCount();
        if (base == 0) {
            s.selected.clear();
            return;
        }
        if (s.selected.size == base) {
            // drop invalid indices
            boolean ok = true;
            for (int i = 0; i < s.selected.size; i++) {
                int v = s.selected.get(i);
                if (v < 0 || v >= kits.length) {
                    ok = false;
                    break;
                }
            }
            if (ok) return;
        }

        s.selected.clear();
        UnitType hero = unit.type;
        if (hero != null) {
            int[] pref = HeroKitPrefs.kitSelection(hero, this);
            if (pref.length == base) {
                IntSeq used = new IntSeq();
                for (int v : pref) {
                    if (v >= 0 && v < kits.length && !used.contains(v)) {
                        s.selected.add(v);
                        used.add(v);
                    }
                }
                for (int i = 0; s.selected.size < base && i < kits.length; i++) {
                    if (!used.contains(i)) {
                        s.selected.add(i);
                        used.add(i);
                    }
                }
                return;
            }
        }
        for (int i = 0; i < base; i++) s.selected.add(i);
    }

    /** Apply saved kit indices onto this unit, then reset cycle index. */
    public void applyPrefsSelection(Unit unit, UnitType hero) {
        if (unit == null) return;
        UnitKitState s = kitState(unit);
        s.selected.clear();
        UnitType h = hero != null ? hero : unit.type;
        int[] pref = HeroKitPrefs.kitSelection(h, this);
        int base = baseCount();
        IntSeq used = new IntSeq();
        for (int v : pref) {
            if (s.selected.size >= base) break;
            if (v >= 0 && v < kits.length && !used.contains(v)) {
                s.selected.add(v);
                used.add(v);
            }
        }
        for (int i = 0; s.selected.size < base && i < kits.length; i++) {
            if (!used.contains(i)) {
                s.selected.add(i);
                used.add(i);
            }
        }
        if (s.selected.isEmpty()) ensureSelection(unit, s);
        s.index = 0;
    }

    /** Index within {@link #activeKits(Unit)}, not the pool. */
    public int indexOf(Unit unit) {
        Kits[] active = activeKits(unit);
        if (active.length == 0) return 0;
        int i = kitState(unit).index;
        if (i < 0 || i >= active.length) return 0;
        return i;
    }

    /** Pool index for the currently active kit. */
    public int poolIndexOf(Unit unit) {
        UnitKitState s = kitState(unit);
        ensureSelection(unit, s);
        int active = indexOf(unit);
        if (active < 0 || active >= s.selected.size) return 0;
        return s.selected.get(active);
    }

    public RelicType effective(Unit unit, int activeKitIndex, int slot) {
        if (slot < 0 || slot >= KIT_FLEX || unit == null) return null;
        Kits[] active = activeKits(unit);
        if (active.length == 0) return null;
        if (activeKitIndex < 0 || activeKitIndex >= active.length) activeKitIndex = 0;

        UnitKitState s = kitState(unit);
        int poolIndex = s.selected.get(activeKitIndex);

        RelicType[] over = s.overrides.get(poolIndex);
        if (over != null && over[slot] != null) return over[slot];

        Kits kit = active[activeKitIndex];
        return kit == null ? null : kit.slot(slot);
    }

    public void applyCurrentKit(Unit unit, PlayerLoadout l) {
        if (unit == null || l == null) return;
        int index = indexOf(unit);
        for (int i = 0; i < KIT_FLEX; i++) {
            l.slots[i] = effective(unit, index, i);
        }
        l.recompute();
    }

    /**
     * Override one flex slot on the current kit. Stored under the pool index so it
     * survives kit-selection edits that keep the same pool entry.
     */
    public boolean tryOverride(Unit unit, PlayerLoadout l, RelicType relic, int slot) {
        if (unit == null || l == null || relic == null) return false;
        if (slot < 0 || slot >= KIT_FLEX) return false;
        if (kits.length == 0) return false;

        UnitKitState s = kitState(unit);
        ensureSelection(unit, s);

        int activeIndex = indexOf(unit);
        int poolIndex = s.selected.get(activeIndex);

        if (overrideUsedElsewhere(s, relic, poolIndex, slot)) {
            return false;
        }

        RelicType[] over = s.overrides.get(poolIndex);
        if (over == null) {
            over = new RelicType[KIT_FLEX];
            s.overrides.put(poolIndex, over);
        }
        over[slot] = relic;

        l.slots[slot] = relic;
        l.recompute();
        return true;
    }

    boolean overrideUsedElsewhere(UnitKitState s, RelicType relic, int exceptPool, int exceptSlot) {
        for (ObjectMap.Entry<Integer, RelicType[]> e : s.overrides) {
            RelicType[] over = e.value;
            if (over == null) continue;
            for (int i = 0; i < KIT_FLEX; i++) {
                if (e.key == exceptPool && i == exceptSlot) continue;
                if (over[i] == relic) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout l, int slot) {
        if (unit == null || l == null) return false;
        Kits[] active = activeKits(unit);
        if (active.length == 0) return false;

        UnitKitState s = kitState(unit);
        int current = indexOf(unit);
        int next = (current + 1) % active.length;
        s.index = next;

        Kits kit = active[next];
        if (kit == null) return false;

        for (int i = 0; i < KIT_FLEX; i++) {
            l.slots[i] = effective(unit, next, i);
        }
        l.recompute();

        RelicType swapRelic = l.slots[KIT_SLOT];
        RelicPickupToast.show(
                RelicPickupToast.ToastChannel.ABILITY,
                kit.name != null ? kit.name : "Kit",
                kit.description != null ? kit.description : "",
                -1,
                swapRelic != null ? swapRelic.icon : null,
                (next + 1) + "/" + active.length
        );
        return true;
    }

    static class UnitKitState {
        /** Index into {@link #selected} / activeKits. */
        int index;
        /** Pool indices into {@link KitSwapAbility#kits}, length = baseCount. */
        final IntSeq selected = new IntSeq();
        /** Overrides keyed by pool index. */
        final ObjectMap<Integer, RelicType[]> overrides = new ObjectMap<>();
    }

    public static class Kits {
        public String name = "";
        public String description = "";
        public RelicType slot0, slot1, slot2;
        public UnlockReq unlock = UnlockReq.none();

        public Kits() {
        }

        public Kits(String name, String description, RelicType a, RelicType b, RelicType c) {
            this.name = name;
            this.description = description;
            this.slot0 = a;
            this.slot1 = b;
            this.slot2 = c;
        }

        public RelicType slot(int i) {
            return switch (i) {
                case 0 -> slot0;
                case 1 -> slot1;
                case 2 -> slot2;
                default -> null;
            };
        }
    }
}
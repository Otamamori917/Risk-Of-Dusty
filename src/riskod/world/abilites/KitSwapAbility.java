package riskod.world.abilites;

import arc.struct.IntMap;
import arc.struct.ObjectMap;
import mindustry.gen.Unit;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;
import riskod.world.ui.RelicPickupToast;

public class KitSwapAbility extends ChargedAbility {

    public static final int KIT_SLOT = 3;
    public static final int KIT_FLEX = 3;

    public Kits[] kits = {};

    private static final IntMap<UnitKitState> states = new IntMap<>();

    public KitSwapAbility(Kits... kt) {
        kits = kt != null ? kt : new Kits[0];
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

    public int indexOf(Unit unit) {
        if (kits.length == 0) return 0;
        int i = kitState(unit).index;
        if (i < 0 || i >= kits.length) return 0;
        return i;
    }

    public RelicType effective(Unit unit, int kitIndex, int slot) {
        if (slot < 0 || slot >= KIT_FLEX || kits.length == 0) return null;
        if (kitIndex < 0 || kitIndex >= kits.length) kitIndex = 0;

        UnitKitState s = kitState(unit);
        RelicType[] over = s.overrides.get(kitIndex);
        if (over != null && over[slot] != null) return over[slot];

        Kits kit = kits[kitIndex];
        if (kit == null) return null;
        return kit.slot(slot);
    }

    public void applyCurrentKit(Unit unit, PlayerLoadout l) {
        if (unit == null || l == null || kits.length == 0) return;
        int index = indexOf(unit);
        for (int i = 0; i < KIT_FLEX; i++) {
            l.slots[i] = effective(unit, index, i);
        }
        l.recompute();
    }

    public boolean tryOverride(Unit unit, PlayerLoadout l, RelicType relic, int slot) {
        if (unit == null || l == null || relic == null) return false;
        if (slot < 0 || slot >= KIT_FLEX) return false;
        if (kits.length == 0) return false;

        UnitKitState s = kitState(unit);
        int kitIndex = indexOf(unit);

        if (overrideUsedElsewhere(s, relic, kitIndex, slot)) {
            return false;
        }

        RelicType[] over = s.overrides.get(kitIndex);
        if (over == null) {
            over = new RelicType[KIT_FLEX];
            s.overrides.put(kitIndex, over);
        }
        over[slot] = relic;

        l.slots[slot] = relic;
        l.recompute();
        return true;
    }

    boolean overrideUsedElsewhere(UnitKitState s, RelicType relic, int exceptKit, int exceptSlot) {
        for (ObjectMap.Entry<Integer, RelicType[]> e : s.overrides) {
            RelicType[] over = e.value;
            if (over == null) continue;
            for (int i = 0; i < KIT_FLEX; i++) {
                if (e.key == exceptKit && i == exceptSlot) continue;
                if (over[i] == relic) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout l, int slot) {
        if (unit == null || kits.length == 0 || l == null) return false;

        UnitKitState s = kitState(unit);
        int current = indexOf(unit);
        int next = (current + 1) % kits.length;
        s.index = next;

        Kits kit = kits[next];
        if (kit == null) return false;

        for (int i = 0; i < KIT_FLEX; i++) {
            l.slots[i] = effective(unit, next, i);
        }
        l.recompute();

        RelicPickupToast.show(
                kit.name != null ? kit.name : "Kit",
                kit.description != null ? kit.description : "",
                -1,
                null,
                (next + 1) + "/" + kits.length
        );
        return true;
    }

    static class UnitKitState {
        int index;
        final ObjectMap<Integer, RelicType[]> overrides = new ObjectMap<>();
    }

    public static class Kits {
        public String name = "";
        public String description = "";
        public RelicType slot0, slot1, slot2;

        public Kits() {}

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
package riskod.world.run;

import arc.Core;
import arc.struct.IntSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.UnitType;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.relic.RelicType;
import riskod.world.unit.HeroAlt;
import riskod.world.unit.PlayerCharUnitType;

/**
 * Per-hero loadout choices at run start (slot alternates + kit-swap selection).
 * Stored in Core.settings.
 */
public class HeroKitPrefs {
    static final ObjectMap<String, int[]> kitSelCache = new ObjectMap<>();
    static final ObjectMap<String, String[]> slotCache = new ObjectMap<>();

    static String keySlots(String hero) {
        return "riskod-hero-slots-" + hero;
    }

    static String keyKits(String hero) {
        return "riskod-hero-kits-" + hero;
    }

    /** Slot relic names length 5; empty string = default startingSlots. */
    public static String[] slotPicks(UnitType hero) {
        if (hero == null) return emptySlots();
        String[] cached = slotCache.get(hero.name);
        if (cached != null) return cached;
        String raw = Core.settings.getString(keySlots(hero.name), "");
        String[] out = emptySlots();
        if (!raw.isEmpty()) {
            String[] parts = raw.split(",", -1);
            for (int i = 0; i < Math.min(5, parts.length); i++) out[i] = parts[i];
        }
        slotCache.put(hero.name, out);
        return out;
    }

    public static void setSlotPick(UnitType hero, int slot, RelicType r) {
        if (hero == null || slot < 0 || slot >= 5) return;
        String[] picks = slotPicks(hero).clone();
        picks[slot] = r == null ? "" : r.name;
        slotCache.put(hero.name, picks);
        Core.settings.put(keySlots(hero.name), String.join(",", picks));
    }

    /** Resolved pick for a slot: saved override if still valid, else default. */
    public static RelicType picked(PlayerCharUnitType hero, int slot, RelicType fallback) {
        if (hero == null || slot < 0 || slot >= PlayerLoadout.SLOT_COUNT) return fallback;
        String[] picks = slotPicks(hero);
        if (picks == null || slot >= picks.length) return fallback;
        String id = picks[slot];
        if (id == null || id.isEmpty()) return fallback;
        RelicType r = PlayerLoadout.find(id);
        if (r == null) return fallback;
        if (!altAllowed(hero, slot, r) && r != fallback) return fallback;
        return r;
    }

    /** Indices into KitSwapAbility.kits pool; length = base kit count. */
    public static int[] kitSelection(UnitType hero, KitSwapAbility swap) {
        if (hero == null || swap == null || swap.kits == null || swap.kits.length == 0) {
            return new int[0];
        }
        int base = swap.baseKitCount > 0 ? swap.baseKitCount : swap.kits.length;
        base = Math.min(base, swap.kits.length);

        int[] cached = kitSelCache.get(hero.name);
        if (cached != null && cached.length == base) return cached;

        String raw = Core.settings.getString(keyKits(hero.name), "");
        int[] out = new int[base];
        if (raw.isEmpty()) {
            for (int i = 0; i < base; i++) out[i] = i;
        } else {
            String[] parts = raw.split(",");
            IntSeq used = new IntSeq();
            int n = 0;
            for (String p : parts) {
                if (n >= base) break;
                try {
                    int v = Integer.parseInt(p.trim());
                    if (v >= 0 && v < swap.kits.length && !used.contains(v)) {
                        out[n++] = v;
                        used.add(v);
                    }
                } catch (Throwable ignored) {
                }
            }
            for (int i = 0; n < base && i < swap.kits.length; i++) {
                if (!used.contains(i)) {
                    out[n++] = i;
                    used.add(i);
                }
            }
        }
        kitSelCache.put(hero.name, out);
        return out;
    }

    public static void setKitSelection(UnitType hero, int[] indices) {
        if (hero == null || indices == null) return;
        kitSelCache.put(hero.name, indices.clone());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indices.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(indices[i]);
        }
        Core.settings.put(keyKits(hero.name), sb.toString());
    }

    /** Build loadout for run start from defaults + prefs. */
    public static PlayerLoadout buildLoadout(PlayerCharUnitType hero) {
        PlayerLoadout l = new PlayerLoadout();
        if (hero.startingSlots != null) {
            for (int i = 0; i < PlayerLoadout.SLOT_COUNT; i++) {
                if (i < hero.startingSlots.length) l.slots[i] = hero.startingSlots[i];
            }
        }
        String[] picks = slotPicks(hero);
        for (int i = 0; i < PlayerLoadout.SLOT_COUNT; i++) {
            if (picks[i] == null || picks[i].isEmpty()) continue;
            RelicType r = PlayerLoadout.find(picks[i]);
            if (r == null) continue;
            if (!altAllowed(hero, i, r)) continue;
            l.slots[i] = r;
        }
        if (l.gear() != null) l.gearCharges = l.effectiveGearMax();
        l.recompute();
        return l;
    }

    /** Drop locked alts / kits from saved picks. Call after lock* / meta reset / opening customize. */
    public static void sanitize(UnitType hero) {
        if (!(hero instanceof PlayerCharUnitType pc)) return;

        String[] picks = slotPicks(hero).clone();
        boolean slotsChanged = false;
        for (int i = 0; i < picks.length; i++) {
            if (picks[i] == null || picks[i].isEmpty()) continue;
            RelicType r = PlayerLoadout.find(picks[i]);
            if (r == null || !altAllowed(pc, i, r)) {
                picks[i] = "";
                slotsChanged = true;
            }
        }
        if (slotsChanged) {
            slotCache.put(hero.name, picks);
            Core.settings.put(keySlots(hero.name), String.join(",", picks));
        }

        KitSwapAbility swap = findSwap(pc);
        if (swap == null || swap.kits == null || swap.kits.length == 0) return;

        int base = swap.baseKitCount > 0 ? Math.min(swap.baseKitCount, swap.kits.length) : swap.kits.length;
        int[] sel = kitSelection(hero, swap).clone();
        boolean kitsChanged = false;
        arc.struct.IntSeq used = new arc.struct.IntSeq();

        for (int i = 0; i < sel.length; i++) {
            int idx = sel[i];
            if (kitUnlocked(swap, idx) && !used.contains(idx)) {
                used.add(idx);
                continue;
            }
            kitsChanged = true;
            int replacement = -1;
            for (int f = 0; f < swap.kits.length; f++) {
                if (!used.contains(f) && kitUnlocked(swap, f)) {
                    replacement = f;
                    break;
                }
            }
            if (replacement < 0) replacement = i < base ? i : 0;
            sel[i] = replacement;
            used.add(replacement);
        }
        if (sel.length != base) {
            sel = new int[base];
            used.clear();
            for (int i = 0, f = 0; i < base; i++) {
                while (f < swap.kits.length && (used.contains(f) || !kitUnlocked(swap, f))) f++;
                sel[i] = f < swap.kits.length ? f : i;
                used.add(sel[i]);
            }
            kitsChanged = true;
        }
        if (kitsChanged) setKitSelection(hero, sel);
    }

    public static void sanitizeAll() {
        for (UnitType t : Vars.content.units()) {
            if (t instanceof PlayerCharUnitType) sanitize(t);
        }
    }

    static boolean kitUnlocked(KitSwapAbility swap, int poolIdx) {
        if (poolIdx < 0 || poolIdx >= swap.kits.length) return false;
        KitSwapAbility.Kits k = swap.kits[poolIdx];
        if (k == null) return false;
        return k.unlock == null || k.unlock.met();
    }

    static KitSwapAbility findSwap(PlayerCharUnitType hero) {
        if (hero.startingSlots == null) return null;
        for (RelicType r : hero.startingSlots) {
            if (r != null && r.ability instanceof KitSwapAbility k) return k;
        }
        return null;
    }

    public static boolean altAllowed(PlayerCharUnitType hero, int slot, RelicType r) {
        if (r == null) return false;
        if (hero.startingSlots != null && slot < hero.startingSlots.length && hero.startingSlots[slot] == r) {
            return true;
        }
        Seq<HeroAlt> alts = hero.altsFor(slot);
        if (alts == null) return false;
        for (HeroAlt a : alts) {
            if (a.relic == r && a.unlocked()) return true;
        }
        return false;
    }

    static String[] emptySlots() {
        return new String[]{"", "", "", "", ""};
    }
}
package riskod.world.unit;

import arc.math.Angles;
import arc.struct.IntFloatMap;
import arc.struct.IntMap;
import arc.struct.Seq;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import riskod.RiskOfDustryLoader;
import riskod.world.meta.Meta;
import riskod.world.meta.UnlockReq;
import riskod.world.run.HeroKitPrefs;
import riskod.world.run.MockRun;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.relic.RelicType;
import riskod.world.abilites.ChargedAbility;
import riskod.world.abilites.KitSwapAbility;

public class PlayerCharUnitType extends UnitType {
    public static final IntMap<PlayerLoadout> loadouts = new IntMap<>();
    public static final Seq<PlayerCharUnitType> selectable = new Seq<>();

    static final IntFloatMap lastHealth = new IntFloatMap();

    public RelicType[] startingSlots = new RelicType[PlayerLoadout.SLOT_COUNT];
    public UnlockReq unlock = UnlockReq.none();
    public boolean heroSelect = true;

    public PlayerCharUnitType(String name) {
        super(name);
        playerControllable = true;
    }

    @Override
    public void init() {
        super.init();
        if (heroSelect && !selectable.contains(this)) {
            selectable.add(this);
        }
    }

    public boolean unlocked() {
        if (Meta.isForcedUnlocked("hero", name)) return true;
        return unlock == null || unlock.met();
    }

    public static PlayerLoadout loadout(Unit unit) {
        if (unit == null) return null;
        PlayerLoadout l = loadouts.get(unit.id);
        if (l == null) {
            l = new PlayerLoadout();
            if (unit.type instanceof PlayerCharUnitType pc) {
                if (RunState.current == null || RunState.pendingLoadout == null) {
                    if (RunState.current != null && RunState.current.heroType == pc) {
                        PlayerLoadout built = HeroKitPrefs.buildLoadout(pc);
                        System.arraycopy(built.slots, 0, l.slots, 0, PlayerLoadout.SLOT_COUNT);
                        l.gearCharges = built.gearCharges;
                        l.recompute();
                    } else if (pc.startingSlots != null) {
                        for (int i = 0; i < PlayerLoadout.SLOT_COUNT; i++) {
                            if (i < pc.startingSlots.length) l.slots[i] = pc.startingSlots[i];
                        }
                        if (l.gear() != null) l.gearCharges = l.effectiveGearMax();
                        l.recompute();
                    }
                }
            }
            loadouts.put(unit.id, l);
        }
        return l;
    }

    public static void removeLoadout(Unit unit) {
        if (unit != null) {
            loadouts.remove(unit.id);
            lastHealth.remove(unit.id, 0);
        }
    }

    public static void syncHealth(Unit unit) {
        if (unit != null && lastHealth.containsKey(unit.id)) {
            lastHealth.put(unit.id, unit.health);
        }
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        PlayerLoadout l = loadout(unit);
        if (l == null) return;

        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            RelicType r = l.slots[i];
            if (r != null && r.ability instanceof ChargedAbility charged) {
                charged.update(unit, l, i);
            }
        }
        tickParkedAbilities(unit, l);

        l.updateGear(unit);
        unit.speedMultiplier *= l.speedMul;
        trackHealth(unit);

        if (mindustry.Vars.player != null && mindustry.Vars.player.unit() == unit) {
            for (int i = 0; i < PlayerLoadout.SLOT_COUNT; i++) {
                if (RiskOfDustryLoader.slotTapped(i)) {
                    l.tryActivateSlot(unit, i);
                }
            }
            unit.rotation = Angles.mouseAngle(unit.x, unit.y);
        }
    }

    /** Alternates per slot 0–4 (gear = 4). Defaults stay in startingSlots. */
    @SuppressWarnings("unchecked")
    public final Seq<HeroAlt>[] slotAlts = new Seq[PlayerLoadout.SLOT_COUNT];

    {
        for (int i = 0; i < slotAlts.length; i++) slotAlts[i] = new Seq<>();
    }

    public Seq<HeroAlt> altsFor(int slot) {
        if (slot < 0 || slot >= slotAlts.length) return null;
        return slotAlts[slot];
    }

    public PlayerCharUnitType alt(int slot, RelicType relic, UnlockReq unlock) {
        if (slot >= 0 && slot < slotAlts.length && relic != null) {
            slotAlts[slot].add(new HeroAlt(relic, unlock));
        }
        return this;
    }

    public PlayerCharUnitType alt(int slot, RelicType relic) {
        return alt(slot, relic, UnlockReq.none());
    }

    void trackHealth(Unit unit) {
        boolean run = RunState.active();
        if (!(run || MockRun.active) || mindustry.Vars.player == null || mindustry.Vars.player.unit() != unit) return;
        float prev = lastHealth.get(unit.id, -1f);
        if (run && prev >= 0f) {
            float delta = unit.health - prev;
            if (delta > 0f) RunState.current.noteHealing(delta);
            else if (delta < 0f) RunState.current.noteDamageReceived(-delta);
        }

        if (unit.health <= 20f) {
            PlayerLoadout l = loadout(unit);
            if (l != null && RunState.tryMrBones(unit, l)) {
                lastHealth.put(unit.id, unit.health);
                return;
            }
        }
        lastHealth.put(unit.id, unit.health);
    }

    @Override
    public void killed(Unit unit) {
        super.killed(unit);
        float prev = lastHealth.get(unit.id, -1f);
        if (prev > 0f && RunState.active()) {
            RunState.current.noteDamageReceived(prev);
        }
        removeLoadout(unit);
    }

    /**
     * Abilities marked noCycleRefresh keep ticking charge/cooldown
     * even while not on the active kit (anti kit-swap CD abuse).
     */
    static void tickParkedAbilities(Unit unit, PlayerLoadout l) {
        KitSwapAbility swap = KitSwapAbility.find(l);
        if (swap == null || swap.kits == null) return;

        for (KitSwapAbility.Kits kit : swap.kits) {
            if (kit == null) continue;
            for (int slot = 0; slot < KitSwapAbility.KIT_FLEX; slot++) {
                RelicType r = kit.slot(slot);
                if (r == null || !(r.ability instanceof ChargedAbility c)) continue;
                if (!c.noCycleRefresh) continue;

                boolean equipped = false;
                for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
                    if (l.slots[i] != null && l.slots[i].ability == c) {
                        equipped = true;
                        break;
                    }
                }
                if (equipped) continue;
                c.update(unit, l, slot);
            }
        }
    }
}
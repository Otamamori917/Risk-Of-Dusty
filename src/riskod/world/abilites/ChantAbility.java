package riskod.world.abilites;

import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;
import riskod.world.defect.DefectState;
import riskod.world.relic.ChantRelic;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.unit.ChantDroneType;
import riskod.world.unit.CompanionDroneType;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;

import java.util.Objects;

/** Slows the user while chanting after sacrificing something of value up front; on success grants a shrine-scaled relic or drone. */
public class ChantAbility extends ChargedAbility {
    enum Kind {
        relic, gear, drone, health, items
    }

    /// Ticks the chant lasts.
    public float duration = 60f * 4f;

    /// Speed multiplier applied to the user while chanting.
    public float slowMul = 0.4f;

    /// Chance the chant fails and gives no reward.
    public float failChance = 0.3f;

    /// Fraction of max health taken when health is the sacrifice.
    public float healthCost = 0.3f;

    /// Fraction of one core item stack taken when items are the sacrifice.
    public float itemFraction = 0.5f;
    /** Core stacks below this are not valid item sacrifices. */
    public int itemMinimum = 40;


    /// Chance the reward is a relic rather than a drone when both pools have entries.
    public float relicRewardChance = 0.5f;

    public final Seq<ChantRelic> relicRewards = new Seq<>();
    public final Seq<ChantDroneType> droneRewards = new Seq<>();

    static class Chant {
        float left;
    }

    static final IntMap<Chant> chants = new IntMap<>();

    public ChantAbility() {
        maxCharges = 1;
        cooldown = 60f * 45f;
        chargesOnReady = 1;
        noCycleRefresh = true;
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout l, int slot) {
        if (unit == null || l == null) return false;

        int key = stateKey(unit);
        if (chants.containsKey(key)) return false;

        Seq<Kind> options = options(unit, l);
        if (options.isEmpty()) {
            toast("Nothing to sacrifice");
            return false;
        }

        String lost = sacrifice(options.random(), unit, l);
        Chant c = new Chant();
        c.left = duration;
        chants.put(key, c);
        toast("Chanting... sacrificed " + lost);
        return true;
    }

    @Override
    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        super.update(unit, loadout, slot);

        int key = stateKey(unit);
        Chant c = chants.get(key);
        if (c == null) return;

        unit.speedMultiplier *= slowMul;
        if (Vars.state.isPaused()) return;

        c.left -= Time.delta;
        if (c.left > 0f) return;

        chants.remove(key);
        finish(unit);
    }

    void finish(Unit unit) {
        if (Mathf.chance(failChance)) {
            toast("The chant failed");
            return;
        }

        UnitType hero = RunState.current != null ? RunState.current.heroType : unit.type;
        Seq<ChantRelic> relics = relicRewards.select(r -> r != null && r.canDropFor(hero));
        Seq<ChantDroneType> drones = droneRewards.select(Objects::nonNull);
        if (relics.isEmpty() && drones.isEmpty()) {
            toast("The chant has no reward configured");
            return;
        }

        int shrines = RunState.current != null ? RunState.current.shrinesThisSector : 0;
        boolean giveRelic = !relics.isEmpty() && (drones.isEmpty() || Mathf.chance(relicRewardChance));
        float ox = unit.x + Mathf.range(14f);
        float oy = unit.y + Mathf.range(14f);

        if (giveRelic) {
            ChantRelic r = relics.random();
            if (r.slotKind != RelicType.SlotKind.passive) stamp(r.name);

            PlayerLoadout l = PlayerCharUnitType.loadout(unit);
            if (l != null) l.recompute();

            if (!RelicPickupUnitType.give(unit, r, shrines)) RelicPickupUnitType.spawn(ox, oy, r, shrines);
            toast("Chant bonus x" + Strings.autoFixed(r.scale(shrines), 2));
        } else {
            ChantDroneType d = drones.random();
            Unit spawned = d.spawn(unit.team, ox, oy);
            if (spawned != null) ChantDroneType.shrinesById.put(spawned.id, shrines);
            toast(d.localizedName + " (x" + Strings.autoFixed(shrines * RunState.CHANT_SCALE_PER_SHRINE, 2) + ")");
        }
    }

    void stamp(String name) {
        if (RunState.current != null) RunState.current.setChantScale(name);
    }

    Seq<Kind> options(Unit unit, PlayerLoadout l) {
        Seq<Kind> out = new Seq<>();
        if (sacrificablePassives(l).any()) out.add(Kind.relic);
        if (l.gear() != null) out.add(Kind.gear);
        if (sacrificableDrones(unit).any()) out.add(Kind.drone);
        if (unit.health > unit.maxHealth * healthCost + 1f) out.add(Kind.health);
        if (coreItems(unit).any()) out.add(Kind.items);
        return out;
    }

    /** Non-chant passives only. */
    Seq<RelicType> sacrificablePassives(PlayerLoadout l) {
        Seq<RelicType> out = new Seq<>();
        if (l == null) return out;
        for (RelicType r : l.passives) {
            if (r != null && !(r instanceof ChantRelic)) out.add(r);
        }
        return out;
    }

    /** Non-chant companion drones only. */
    Seq<Unit> sacrificableDrones(Unit unit) {
        Seq<Unit> out = new Seq<>();
        Groups.unit.each(u -> {
            if (u.team != unit.team || !u.isValid()) return;
            if (!(u.type instanceof CompanionDroneType)) return;
            if (u.type instanceof ChantDroneType) return;
            out.add(u);
        });
        return out;
    }

    Seq<Item> coreItems(Unit unit) {
        Seq<Item> out = new Seq<>();
        var core = unit.team.core();
        if (core == null) return out;
        for (Item item : Vars.content.items()) {
            if (item != null && core.items.get(item) >= itemMinimum) out.add(item);
        }
        return out;
    }

    String sacrifice(Kind kind, Unit unit, PlayerLoadout l) {
        return switch (kind) {
            case relic -> {
                Seq<RelicType> list = sacrificablePassives(l);
                RelicType pick = list.random();
                RelicType r = l.takePassive(pick);
                DefectState.refreshCapacity(unit, l);
                yield r == null ? "a relic" : r.localizedName;
            }
            case gear -> {
                String name = l.gear().localizedName;
                l.slots[PlayerLoadout.GEAR_SLOT] = null;
                l.gearCharges = 0;
                l.gearCooldownTimer = 0f;
                l.recompute();
                DefectState.refreshCapacity(unit, l);
                yield name;
            }
            case drone -> {
                Unit d = sacrificableDrones(unit).random();
                d.kill();
                yield d.type.localizedName;
            }
            case health -> {
                unit.health -= unit.maxHealth * healthCost;
                PlayerCharUnitType.syncHealth(unit);
                yield "health";
            }
            default -> {
                Item item = coreItems(unit).random();
                var core = unit.team.core();
                int amount = Math.max(itemMinimum, (int) (core.items.get(item) * itemFraction));
                amount = Math.min(amount, core.items.get(item));
                core.items.remove(item, amount);
                yield amount + " " + item.localizedName;
            }
        };
    }
}
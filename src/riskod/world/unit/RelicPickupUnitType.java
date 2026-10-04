package riskod.world.unit;

import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.ui.RelicPickupToast;

public class RelicPickupUnitType extends UnitType {
    public static RelicPickupUnitType shared;
    public static final IntMap<RelicType> carried = new IntMap<>();

    public float pickupRange = 18f;

    public RelicPickupUnitType(String name) {
        super(name);
        isEnemy = killable = hittable = false;
        flying = true;
        speed = 0f;
        drag = 1f;
        hitSize = 10f;
        health = 10f;
        hidden = true;
        itemCapacity = 0;
        physics = false;
        useUnitCap = false;
        playerControllable = false;
        createWreck = false;
        createScorch = false;
    }

    RelicType relicOf(Unit unit) {
        return carried.get(unit.id);
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        RelicType relic = relicOf(unit);
        if (relic == null) return;

        Unit player = mindustry.Vars.player == null ? null : mindustry.Vars.player.unit();
        if (player == null || !player.isValid()) return;
        if (!(player.type instanceof PlayerCharUnitType)) return;
        if (!player.within(unit, pickupRange)) return;

        if (relic.slotKind == RelicType.SlotKind.gear && relic instanceof GearType gear) {
            Unit drone = Units.closest(player.team, unit.x, unit.y, 48f, u ->
                    u.type instanceof CompanionDroneType c
                            && c.role == CompanionDroneType.Role.gear
                            && CompanionDroneType.ActionState.get(u).gear == null);
            if (drone != null) {
                CompanionDroneType.giveGear(drone, gear);
                RelicPickupToast.show(relic, "assigned to drone");
                if (RunState.active()) {
                    RunState.current.unlockLogbook(relic);
                    RunState.current.noteRelic(relic, 1);
                }
                carried.remove(unit.id);
                unit.remove();
                return;
            }
        }

        PlayerLoadout loadout = PlayerCharUnitType.loadout(player);
        if (loadout == null) return;

        KitSwapAbility kitSwap = KitSwapAbility.find(loadout);
        if (kitSwap != null
                && relic.slotKind != RelicType.SlotKind.passive
                && relic.slotKind != RelicType.SlotKind.gear
                && relic.equipSlot >= 0 && relic.equipSlot <= 2) {

            if (kitSwap.tryOverride(player, loadout, relic, relic.equipSlot)) {
                loadout.onPickedUp(player, relic);
                RelicPickupToast.show(relic, "kit override");
                if (RunState.active()) {
                    RunState.current.unlockLogbook(relic);
                    RunState.current.noteRelic(relic, 1);
                }
                carried.remove(unit.id);
                unit.remove();
                return;
            }

            RelicPickupToast.show(relic, "already used in another kit");
            return;
        }

        if (loadout.tryPickup(relic)) {
            loadout.onPickedUp(player, relic);
            RelicPickupToast.show(relic, relic.slotKind.name());
            if (RunState.active()) {
                RunState.current.unlockLogbook(relic);
                RunState.current.noteRelic(relic, stackOf(loadout, relic));
            }
            carried.remove(unit.id);
            unit.remove();
        }
    }

    static int stackOf(PlayerLoadout loadout, RelicType relic) {
        if (relic.slotKind != RelicType.SlotKind.passive) return 1;
        int n = 0;
        for (RelicType r : loadout.passives) {
            if (r == relic) n++;
        }
        return Math.max(1, n);
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        RelicType relic = relicOf(unit);
        if (relic != null && relic.icon != null) {
            Draw.rect(relic.icon, unit.x, unit.y + Mathf.sin(Time.time, 16f, 2f), 8f, 8f);
        }
    }

    public static Unit spawn(float x, float y, RelicType relic) {
        if (relic == null || shared == null) return null;
        Unit u = shared.spawn(Team.derelict, x, y);
        if (u != null) carried.put(u.id, relic);
        return u;
    }
}
package riskod.world.abilites;

import arc.struct.IntMap;
import arc.util.Time;
import mindustry.entities.abilities.Ability;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.world.blocks.storage.CoreBlock;
import riskod.world.run.RunState;

public class LootCarryAbility extends Ability {
    public static final IntMap<Team> lastAttackerTeam = new IntMap<>();
    public static final IntMap<Float> lastAttackTime = new IntMap<>();

    public Item item;
    public int amount = 1;
    public float creditWindow = 60f * 8f;

    public LootCarryAbility(Item item, int amount) {
        this.item = item;
        this.amount = amount;
    }

    public LootCarryAbility() {
    }

    public static void noteAttack(Unit target, Team attacker) {
        if (target == null || attacker == null) return;
        lastAttackerTeam.put(target.id, attacker);
        lastAttackTime.put(target.id, Time.time);
    }

    @Override
    public void death(Unit unit) {
        if (item == null || amount <= 0) return;

        Team credited = lastAttackerTeam.get(unit.id);
        Float t = lastAttackTime.get(unit.id);
        if (credited == null || t == null || Time.time - t > creditWindow) {
            Unit near = Groups.unit.find(u ->
                    u.isPlayer() && u.team != unit.team && u.within(unit, 40f * 8f));
            if (near != null) credited = near.team;
        }

        if (credited == null || credited.core() == null) return;

        CoreBlock.CoreBuild core = credited.core();
        if (core == null || core.items == null) return;

        int space = core.getMaximumAccepted(item) - core.items.get(item);
        int put = Math.min(amount, Math.max(space, 0));
        if (put > 0) {
            core.items.add(item, put);
            if (RunState.active()) RunState.current.noteItems(put);
        }

        lastAttackerTeam.remove(unit.id);
        lastAttackTime.remove(unit.id);
    }
}
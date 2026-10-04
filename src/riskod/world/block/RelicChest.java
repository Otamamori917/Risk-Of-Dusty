package riskod.world.block;

import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import riskod.world.meta.Meta;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;

public class RelicChest extends Block {
    public ItemStack[] openCost = {};
    public Seq<RelicLoot> loot = new Seq<>();
    public float spawnOffset = 12f;
    public float luck = 0f;
    public int chestRarity = 1;

    public RelicChest(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        destructible = true;
        hasItems = false;
    }

    public void addLoot(RelicType relic, float weight) {
        loot.add(new RelicLoot(relic, weight));
    }

    public RelicType rollLoot() {
        return rollLoot(0f, currentHero());
    }

    public RelicType rollLoot(float extraLuck) {
        return rollLoot(extraLuck, currentHero());
    }

    public RelicType rollLoot(float extraLuck, UnitType hero) {
        if (loot.isEmpty()) return null;

        float useLuck = luck + extraLuck;
        float total = 0f;
        float[] scores = new float[loot.size];

        for (int i = 0; i < loot.size; i++) {
            RelicLoot l = loot.get(i);
            if (l.relic == null || !l.relic.canDropFor(hero)) {
                scores[i] = 0f;
                continue;
            }
            int rarity = Math.max(1, l.relic.rarity);
            float score = l.weight * Math.max(0.01f, 1f + useLuck * (rarity - 1));
            scores[i] = score;
            total += score;
        }

        if (total <= 0f) return null;

        float r = Mathf.random(total);
        float c = 0f;
        for (int i = 0; i < loot.size; i++) {
            c += scores[i];
            if (r <= c) return loot.get(i).relic;
        }
        for (int i = loot.size - 1; i >= 0; i--) {
            if (scores[i] > 0f) return loot.get(i).relic;
        }
        return null;
    }

    static UnitType currentHero() {
        return RunState.current != null ? RunState.current.heroType : null;
    }

    public static class RelicLoot {
        public RelicType relic;
        public float weight;

        public RelicLoot(RelicType relic, float weight) {
            this.relic = relic;
            this.weight = weight;
        }
    }

    public class RelicChestBuild extends Building {
        public boolean opened;

        public boolean canPay() {
            if (team.core() == null) return false;
            for (ItemStack stack : openCost) {
                if (team.core().items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        public void pay() {
            if (team.core() == null) return;
            for (ItemStack stack : openCost) {
                team.core().items.remove(stack.item, stack.amount);
            }
        }

        public void openChest() {
            openChest(mindustry.Vars.player != null ? mindustry.Vars.player.unit() : null);
        }

        public void openChest(Unit opener) {
            if (opened) {
                toast("Already opened");
                return;
            }
            if (team.core() == null) {
                toast("No core");
                return;
            }
            if (!canPay()) {
                toast("Need items in the core");
                return;
            }
            if (mindustry.Vars.net.client()) return;
            if (loot.isEmpty()) {
                toast("Chest has no loot");
                return;
            }

            pay();
            opened = true;
            Meta.relicChestOpened();

            float extra = 0f;
            UnitType hero = currentHero();
            if (opener != null && opener.isValid() && opener.team == team
                    && opener.type instanceof PlayerCharUnitType) {
                PlayerLoadout loadout = PlayerCharUnitType.loadout(opener);
                if (loadout != null) extra = loadout.effectiveLuck();
                if (hero == null) hero = opener.type;
            }

            RelicType drop = rollLoot(extra, hero);
            if (drop == null) {
                toast("No drop");
                return;
            }
            float ang = Mathf.random(360f);
            RelicPickupUnitType.spawn(
                    x + Mathf.cosDeg(ang) * spawnOffset,
                    y + Mathf.sinDeg(ang) * spawnOffset,
                    drop
            );
            toast(drop.localizedName);
        }

        void toast(String msg) {
            if (mindustry.Vars.player != null && mindustry.Vars.ui != null) {
                mindustry.Vars.ui.showInfoToast(msg, 1.8f);
            }
        }

        public int costSum() {
            int sum = 0;
            for (ItemStack stack : openCost) {
                sum += stack.amount;
            }
            return sum;
        }

        @Override
        public void buildConfiguration(arc.scene.ui.layout.Table table) {
            if (opened) return;
            table.add(costSum() + "").left().pad(4f).row();
            table.button(mindustry.gen.Icon.downOpen, mindustry.ui.Styles.cleari, () -> {
                openChest(mindustry.Vars.player != null ? mindustry.Vars.player.unit() : null);
                if (mindustry.Vars.control != null) mindustry.Vars.control.input.config.hideConfig();
            }).size(40f);
        }

        @Override
        public void configured(Unit builder, Object value) {
            openChest(builder);
        }

        @Override
        public void tapped() {
            openChest();
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.bool(opened);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            opened = read.bool();
        }
    }
}
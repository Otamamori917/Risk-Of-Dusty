package riskod.world.block;

import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.world.Block;
import riskod.world.meta.Meta;

public class DroneChest extends Block {
    public ItemStack[] openCost = {};
    public UnitType droneType;
    public float spawnOffset = 16f;
    /** Higher = rarer in InteractablesGenerator. */
    public int chestRarity = 1;

    public DroneChest(String name) {
        super(name);
        update = true;
        solid = true;
        configurable = true;
        destructible = true;
        hasItems = false;
    }

    public class DroneChestBuild extends Building {
        public boolean opened;

        public boolean canPay() {
            if (team.core() == null) return false;
            for (ItemStack stack : openCost) {
                if (stack == null || stack.item == null) continue;
                if (team.core().items.get(stack.item) < stack.amount) return false;
            }
            return true;
        }

        public void openChest() {
            if (opened) return;
            if (!canPay()) {
                if (Vars.player != null) Vars.ui.showInfoToast("Need items in core", 1.5f);
                return;
            }
            if (Vars.net.client()) return;
            if (droneType == null) {
                if (Vars.player != null) Vars.ui.showInfoToast("No drone type", 1.5f);
                return;
            }

            for (ItemStack stack : openCost) {
                if (stack != null && stack.item != null) {
                    team.core().items.remove(stack.item, stack.amount);
                }
            }
            opened = true;
            Meta.droneChestOpened();

            float ang = Mathf.random(360f);
            droneType.spawn(team,
                    x + Mathf.cosDeg(ang) * spawnOffset,
                    y + Mathf.sinDeg(ang) * spawnOffset);

            if (Vars.player != null) {
                Vars.ui.showInfoToast("Drone deployed", 1.5f);
            }
        }

        public int costSum(){
            int sum = 0;
            for (ItemStack stack : openCost) {
                sum += stack.amount;
            }
            return sum;
        }

        @Override
        public void buildConfiguration(arc.scene.ui.layout.Table table) {
            if(opened) return;
            table.add(costSum()+"").left().pad(4f).row();
            table.button(mindustry.gen.Icon.downOpen, mindustry.ui.Styles.cleari, () -> {
                openChest();
                if (mindustry.Vars.control != null) mindustry.Vars.control.input.config.hideConfig();
            }).size(40f);
        }

        @Override
        public void tapped() {
            openChest();
        }

        @Override
        public void configured(Unit builder, Object value) {
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
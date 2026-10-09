package riskod.world.block;

import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.entities.Units;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.Tile;
import riskod.world.meta.Meta;
import riskod.world.relic.RelicType;
import riskod.world.run.MockRun;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.unit.HuntHeroAI;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;

import static riskod.world.run.EnemySpawnDirector.currentPool;
import static riskod.world.run.EnemySpawnDirector.randomTile;

public class Shrine extends Block {

    public int maxSuccessfulUses = 1;
    public float chanceToFail = 0.3f;
    public float costMultPerUse = 1.2f;
    public float costScale = 40f;

    public ItemStack[] openCost = {};
    public float bloodCost = 0.4f;
    public RelicType relicCost;

    public int enemySpawnAmt = 4;

    public float healingRangePerSuccess = 40f;
    public float healingPerSuccess = 1f;
    public float healingPercentPerSuccess = 0.5f;
    public float healingRate = 5f;

    public ItemStack[] itemReward = {};
    public Seq<RelicLoot> loot = new Seq<>();
    public float spawnOffset = 12f;
    public float luck = 0f;
    public int shrineRarity = 1;

    public CostType cost = CostType.item;
    public RewardType reward = RewardType.relic;

    public enum CostType {
        relic, blood, item, none
    }

    public enum RewardType {
        heal, item, relic, summon, teleporterStrength
    }

    public Shrine(String name) {
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

    public RelicType rollLoot(float extraLuck) {
        return rollLoot(extraLuck, RunState.current != null ? RunState.current.heroType : null);
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

    public static class RelicLoot {
        public RelicType relic;
        public float weight;

        public RelicLoot(RelicType relic, float weight) {
            this.relic = relic;
            this.weight = weight;
        }
    }

    public class ShrineBuild extends Building {
        public int attempts;
        public int successes;
        public int copies = 1;

        public float healRange;
        public float healFlat;
        public float healPercent;
        public float healTimer;
        public boolean activelyHealing;

        public float costMul() {
            return 1f + attempts * (costMultPerUse - 1f);
        }

        public int copiesFromPaid(float paid) {
            if (costScale <= 0f) return 1;
            return Math.max(1, (int) (paid / costScale));
        }

        PlayerLoadout loadoutOf(Unit unit) {
            if (unit == null || !(unit.type instanceof PlayerCharUnitType)) return null;
            return PlayerCharUnitType.loadout(unit);
        }

        public boolean canPay(Unit opener) {
            if (cost == CostType.none) return true;
            if (cost == CostType.item) {
                if (team.core() == null) return false;
                float mul = costMul();
                for (ItemStack stack : openCost) {
                    if (stack == null || stack.item == null) continue;
                    if (team.core().items.get(stack.item) < (int) (stack.amount * mul)) return false;
                }
                return true;
            }
            if (cost == CostType.blood) {
                if (opener == null || !opener.isValid()) return false;
                float payHp = opener.maxHealth * bloodCost + (bloodCost * costMul());
                return opener.health > payHp;
            }
            if (cost == CostType.relic) {
                PlayerLoadout l = loadoutOf(opener);
                if (l == null) return false;
                return relicCost == null ? l.hasPassive() : l.hasPassive(relicCost);
            }
            return true;
        }

        public float pay(Unit opener) {
            if (cost == CostType.none) return 0f;
            float mul = costMul();

            if (cost == CostType.item) {
                if (team.core() == null) return 0f;
                float paid = 0f;
                for (ItemStack stack : openCost) {
                    if (stack == null || stack.item == null) continue;
                    int amt = (int) (stack.amount * mul);
                    team.core().items.remove(stack.item, amt);
                    paid += amt;
                }
                return paid;
            }
            if (cost == CostType.blood) {
                if (opener == null || !opener.isValid()) return 0f;
                float payHp = opener.maxHealth * bloodCost + (bloodCost * mul);
                opener.health -= payHp;
                PlayerCharUnitType.syncHealth(opener);
                return payHp;
            }
            if (cost == CostType.relic) {
                PlayerLoadout l = loadoutOf(opener);
                if (l == null) return 0f;
                RelicType taken = l.takePassive(relicCost);
                return taken == null ? 0f : Math.max(1, taken.rarity);
            }
            return 0f;
        }

        public void activateShrine(Unit opener) {
            if (successes >= maxSuccessfulUses) {
                toast("Already used");
                return;
            }
            if (cost == CostType.item && team.core() == null) {
                toast("No core");
                return;
            }
            if (!canPay(opener)) {
                toast(needText());
                return;
            }

            float paid = pay(opener);
            attempts++;
            Meta.shrineActivated();
            if (RunState.active()) RunState.current.noteShrine();

            if (chanceToFail > 0f && Mathf.chance(chanceToFail)) {
                toast("You made an offering but gained nothing");
                return;
            }

            successes++;
            copies = copiesFromPaid(paid);
            grant(opener);
        }

        void grant(Unit opener) {
            if (reward == RewardType.relic) {
                if (Vars.net.client()) return;
                if (loot.isEmpty()) {
                    toast("Shrine has no loot");
                    return;
                }
                float extra = 0f;
                PlayerLoadout l = loadoutOf(opener);
                if (l != null) extra = l.effectiveLuck();
                UnitType hero = RunState.current != null ? RunState.current.heroType
                        : (opener != null ? opener.type : null);

                RelicType drop = rollLoot(extra, hero);
                if (drop == null) {
                    toast("No drop");
                    return;
                }
                ringSpawn(copies, (x, y) -> RelicPickupUnitType.spawn(x, y, drop));
                toast(drop.localizedName + (copies > 1 ? " x" + copies : ""));
                return;
            }

            if (reward == RewardType.item) {
                if (team.core() == null) return;
                for (int n = 0; n < copies; n++) {
                    for (ItemStack item : itemReward) {
                        if (item == null || item.item == null) continue;
                        team.core().items.add(item.item, item.amount);
                        if (RunState.active()) RunState.current.noteItems(item.amount);
                    }
                }
                toast("Items granted x" + copies);
                return;
            }

            if (reward == RewardType.heal) {
                activelyHealing = true;
                healRange += healingRangePerSuccess;
                healFlat += healingPerSuccess;
                healPercent += healingPercentPerSuccess;
                toast("Healing aura +" + copies);
                return;
            }

            if (reward == RewardType.summon) {
                int amount = Math.max(1, enemySpawnAmt * copies);
                String last = "";
                Team enemyTeam = Vars.state.rules.waveTeam;
                for (int i = 0; i < amount; i++) {
                    Tile tile = randomTile();
                    if (tile == null) continue;
                    if (currentPool().isEmpty()) break;
                    UnitType type = currentPool().random();
                    last = type.localizedName;
                    Unit u = type.spawn(enemyTeam, tile.worldx(), tile.worldy());
                    if (u != null) {
                        HuntHeroAI.apply(u);
                        if (RunState.active()) {
                            float m = RunState.current.enemyStatMul();
                            u.maxHealth(u.maxHealth * m);
                            u.health(u.maxHealth);
                        }
                    }
                }
                toast(amount + " " + last + " summoned");
                return;
            }

            if (reward == RewardType.teleporterStrength) {
                if (RunState.current != null) {
                    RunState.current.teleporterMult += copies;
                    toast("Teleporter Strength: " + RunState.teleporterMult() + "X");
                }
                if (MockRun.active) {
                    MockRun.teleporterMult += copies;
                    toast("Teleporter Strength: " + MockRun.teleporterMult() + "X");
                }
            }
        }

        void ringSpawn(int n, SpawnFn fn) {
            int count = Math.max(1, n);
            float base = Mathf.random(360f);
            for (int i = 0; i < count; i++) {
                float a = base + (360f / count) * i;
                fn.get(x + Mathf.cosDeg(a) * spawnOffset, y + Mathf.sinDeg(a) * spawnOffset);
            }
        }

        interface SpawnFn {
            void get(float x, float y);
        }

        String needText() {
            return switch (cost) {
                case item -> "Need items in the core";
                case blood -> "Not enough health";
                case relic -> relicCost == null ? "Need a passive relic" : "Need " + relicCost.localizedName;
                default -> "Can't pay";
            };
        }

        @Override
        public void updateTile() {
            super.updateTile();
            if (!activelyHealing) return;
            healTimer += 1f;
            if (healTimer < healingRate) return;
            healTimer = 0f;
            Units.nearby(team, x, y, healRange, u -> {
                if (u.isValid()) {
                    u.heal(healPercent / 100f * u.maxHealth + healFlat);
                }
            });
        }

        @Override
        public void draw() {
            super.draw();
            if (!activelyHealing || healRange <= 0f) return;
            float z = Draw.z();
            Draw.z(Layer.effect);
            Lines.stroke(Math.max(1.2f, healRange / 10f), Pal.gray);
            Lines.circle(x, y, healRange);
            Lines.stroke(Math.max(0.8f, healRange / 14f), Pal.heal);
            Lines.circle(x, y, healRange);
            Draw.reset();
            Draw.z(z);
        }

        public String display() {
            if (cost == CostType.none) return "Free";
            float mul = costMul();
            if (cost == CostType.item) {
                StringBuilder s = new StringBuilder();
                for (ItemStack stack : openCost) {
                    if (stack == null || stack.item == null) continue;
                    s.append((int) (stack.amount * mul)).append(" ").append(stack.item.localizedName).append("\n");
                }
                return s.toString();
            }
            if (cost == CostType.blood) {
                return "[scarlet]" + Mathf.round(bloodCost + (bloodCost * mul) * 100f) + "% HP";
            }
            if (cost == CostType.relic) {
                return relicCost == null ? "Random passive" : relicCost.localizedName;
            }
            return "";
        }

        @Override
        public void buildConfiguration(Table table) {
            if (successes >= maxSuccessfulUses) {
                table.add("Spent").pad(6f);
                return;
            }
            table.add(display()).left().pad(4f).row();
            table.button(Icon.downOpen, Styles.cleari, () -> configure(0)).size(40f);
        }

        @Override
        public void configured(Unit builder, Object value) {
            activateShrine(builder);
        }

        void toast(String msg) {
            if (Vars.player != null && Vars.ui != null) {
                Vars.ui.showInfoToast(msg, 1.8f);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(attempts);
            write.i(successes);
            write.i(copies);
            write.bool(activelyHealing);
            write.f(healTimer);
            write.f(healRange);
            write.f(healFlat);
            write.f(healPercent);
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            attempts = read.i();
            successes = read.i();
            copies = read.i();
            activelyHealing = read.bool();
            healTimer = read.f();
            healRange = read.f();
            healFlat = read.f();
            healPercent = read.f();
        }
    }
}
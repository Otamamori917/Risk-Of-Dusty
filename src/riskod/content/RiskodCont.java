package riskod.content;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Sounds;
import mindustry.gen.UnitEntity;
import mindustry.graphics.CacheLayer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.meta.BuildVisibility;
import riskod.world.bullets.ConcussiveWaveBulletType;
import riskod.world.abilites.BulletReflectAbility;
import riskod.world.abilites.DashAbility;
import riskod.world.abilites.LootCarryAbility;
import riskod.world.abilites.SlotShootAbility;
import riskod.world.block.DroneChest;
import riskod.world.block.RelicChest;
import riskod.world.block.Shrine;
import riskod.world.block.Teleporter;
import riskod.world.defect.DefectKits;
import riskod.world.defect.DefectUnitType;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.run.EnemySpawnDirector;
import riskod.world.run.InteractablesGenerator;
import riskod.world.unit.CompanionDroneType;
import riskod.world.unit.HuntHeroAI;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;

import static mindustry.type.ItemStack.with;

public class RiskodCont {
    public static Block fakeCore,
            chest, rareChest, gearChest,
            mountainShrine, chanceShrine, woodShrine, summonShrine, bloodShrine,
            droneChestAttack, droneChestHeal, droneChestGear,
            teleporter;

    public static RelicType superConductor, dataDisk, focusChip, goldCircuit,
            speedCharm, clover, mendGear, finalFlash;

    public static UnitType shared, thief, droneAttack, droneHeal, droneGear, grunt;
    public static UnitType factotum, heroBrawler, defect;

    public static void loadRelics() {
        superConductor = new RelicType("superconductor") {{
            rarity = 6;
            slotKind = SlotKind.passive;
            plasmaBankPermanent = true;
            description = "Plasma/fission energy never expires";
        }};

        dataDisk = new RelicType("data-disk") {{
            rarity = 2;
            slotKind = SlotKind.passive;
            description = "+1 orb slot, +1 max energy";
            bonusOrbCapacity = 1;
            bonusEnergyCap = 1f;
            bonusSlot = -1;
        }};

        focusChip = new RelicType("focus-chip") {{
            rarity = 3;
            slotKind = SlotKind.passive;
            description = "+2 permanent focus while held; +1 focus on pickup";
            bonusFocus = 2;
            grantFocus = 1;
            bonusSlot = -1;
        }};

        goldCircuit = new RelicType("gold-circuit") {{
            rarity = 4;
            slotKind = SlotKind.passive;
            description = "Faster orb pulse";
            pulseIntervalMul = 0.75f;
            bonusSlot = -1;
        }};

        speedCharm = new RelicType("speed-charm") {{
            rarity = 1;
            speedMul = 1.15f;
            slotKind = SlotKind.passive;
            bonusSlot = -1;
        }};

        clover = new RelicType("clover") {{
            rarity = 2;
            slotKind = SlotKind.passive;
            bonusLuck = 0.75f;
            bonusSlot = -1;
        }};

        mendGear = new GearType("mend-gear") {{
            rarity = 3;
            maxCharges = 3;
            cooldown = 180f;
            chargesOnReady = 1;
            healAmount = 40f;
        }};

        finalFlash = new RelicType("final-flash") {{
            slotKind = SlotKind.ability;
            description = "Replaces your special ability with [scarlet]Final Flash[]";
            equipSlot = 3;
            rarity = 5;
            ability = new SlotShootAbility() {{
                maxCharges = 1;
                cooldown = 1200f;
                burstCount = 1;
                windup = Fx.greenLaserCharge.lifetime;
                windupEffect = Fx.greenLaserCharge;
                shootSound = Sounds.shootCorvus;
                windupSound = Sounds.chargeCorvus;
                windupDrag = 0.40f;
                bullet = new LaserBulletType() {{
                    length = 460f;
                    damage = 560f;
                    width = 75f;
                    lifetime = 65f;
                    lightningSpacing = 35f;
                    lightningLength = 5;
                    lightningDelay = 1.1f;
                    lightningLengthRand = 15;
                    lightningDamage = 50;
                    lightningAngleRand = 40f;
                    largeHit = true;
                    lightColor = lightningColor = Pal.heal;
                    healPercent = 25f;
                    collidesTeam = true;
                    sideAngle = 15f;
                    sideWidth = 0f;
                    sideLength = 0f;
                    colors = new Color[]{Pal.heal.cpy().a(0.4f), Pal.heal, Color.white};
                }};
            }};
        }};
    }

    public static void bindDefectRelics() {
        if (defect == null) return;
        if (superConductor != null) superConductor.forHero(defect);
        if (dataDisk != null) dataDisk.forHero(defect);
        if (focusChip != null) focusChip.forHero(defect);
        if (goldCircuit != null) goldCircuit.forHero(defect);
        DefectKits.bindHero(defect);
    }

    public static void loadUnits() {
        shared = new RelicPickupUnitType("relic-pickup") {{
            constructor = UnitEntity::create;
        }};
        RelicPickupUnitType.shared = (RelicPickupUnitType) shared;

        thief = new UnitType("loot-runner") {{
            constructor = UnitEntity::create;
            health = 120;
            speed = 1.2f;
            abilities.add(new LootCarryAbility(Items.silicon, 5));
            weapons.add(new Weapon("pew") {{
                reload = 5;
                mirror = true;
                x = -4;
                bullet = new BasicBulletType(4, 1) {{
                    lifetime = 30;
                }};
            }});
        }};

        grunt = new UnitType("riskod-grunt") {{
            constructor = UnitEntity::create;
            health = 90;
            speed = 0.7f;
            hitSize = 10f;
            abilities.add(new LootCarryAbility(Items.silicon, 5));
            weapons.add(new Weapon("pow") {{
                reload = 80;
                mirror = false;
                bullet = new BasicBulletType(2, 20) {{
                    lifetime = 60;
                }};
            }});
        }};

        droneAttack = new CompanionDroneType("drone-attack") {{
            constructor = UnitEntity::create;
            health = 600;
            role = CompanionDroneType.Role.attack;
        }};
        droneHeal = new CompanionDroneType("drone-heal") {{
            constructor = UnitEntity::create;
            health = 350;
            role = CompanionDroneType.Role.heal;
        }};
        droneGear = new CompanionDroneType("drone-gear") {{
            constructor = UnitEntity::create;
            health = 400;
            role = CompanionDroneType.Role.gear;
        }};
    }

    public static void loadHeros() {
        factotum = new PlayerCharUnitType("factotum") {{
            localizedName = "[orange]Factotum";
            String error = "Ŕ̵͍̬̘̪̝͙̠̦̾͌̑͑̔́̚͝͠ͅȨ̷̡̬̖̭̳͌̿͗̓͗́̍̚̕D̸̨̧̢͚̩͙̣̃̍̀̀̈́͗͆͘͠Ă̶̧̹̈͒̍̐̚̕ͅÇ̸̧̨͕͈̫͉̼̗̥͖͓͎̺̘͓̘͙͚̤̭̱̪̱̙͉̥̣̤̩̆̍͂́͊̒̈́̽̎͒̓͗̎̐̎̀̕͘͘͠ͅͅT̶̨̡̳̙̱̫̥̻̰̣̭̲̪̦͈̳̘̝̭̘̬̹̲̻̗̟̮̩̹̪̮̲̽́͐̄̌̉́̈́̓̀̈̂̔̃̀̏̎̀͊̽͛́̔͋̕͘Ē̷̛̺̝̫̟̙̰̻̜͉͍͔͕̹̝̗̞̓̔͆̔̃̒̈̈́̀̿̃̓̓͗̿͛̈͛̾͐̈̍̕̕͘͜͠D̷̹́̈́̔̓̽̌̑̀́͛̉̋̔̒̾̈́͆͐̉̂̂̏̌͒͝͝͝";
            description = "Factotum, is a jack of all trades.\nHis real name is "+error+"͓ but he prefers you call him Factotum. \nhis voice holds power and he uses it offensively";
            constructor = UnitEntity::create;
            health = 450;
            speed = 0.8f;
            hitSize = 12f;
            startingSlots = new RelicType[]{
                    null,
                    new RelicType("concussive-output") {{
                        localizedName = "Concussive Output";
                        description = "[orange]Factotum[] shouts and emits a concussive blast forward. \n[orange]Factotum[] and hit enemies get pushed back";

                        slotKind = SlotKind.ability;
                        equipSlot = 0;
                        ability = new SlotShootAbility() {{
                            burstCount = 3;
                            spread = 43f;
                            cooldown = 140;
                            maxCharges = 4;
                            chargesOnReady = 4;
                            bullet = new ConcussiveWaveBulletType() {{
                                waves = 5;
                                hitsPerWave = new int[]{4, 8, 11, 13, 10};
                                speed = 3.5f;
                                lifetime = 12f;
                                damage = 8f;
                                knockback = 250f;
                                recoil = 140;
                                cone = 20f;
                                rayLength = 80f;
                                waveColor = Color.valueOf("d0d8e8");
                                waveColorDark = Color.valueOf("3a4050");
                            }};
                        }};
                    }},
                    new RelicType("i-refuse") {{
                        localizedName = "I Refuse";
                        description = "[orange]Factotum[] shouts and emits a sonic wave in all directions. \nAll nearby enemy projectiles get launched back at their owners";
                        slotKind = SlotKind.ability;
                        equipSlot = 1;
                        ability = new BulletReflectAbility();
                    }},
                    new RelicType("dash") {{
                        slotKind = SlotKind.ability;
                        equipSlot = 2;
                        ability = new DashAbility() {{
                            dashSpeed = 18;
                            dashNudge = 0.8f;
                        }};
                    }},
                    mendGear
            };
        }};

        heroBrawler = new PlayerCharUnitType("hero-brawler") {{
            constructor = UnitEntity::create;
            health = 560;
            speed = 1f;
            hitSize = 14f;
            startingSlots = new RelicType[]{
                    new RelicType("pow-pow") {{
                        slotKind = SlotKind.ability;
                        equipSlot = 0;
                        ability = new SlotShootAbility() {{
                            burstCount = 1;
                            spread = 0f;
                            cooldown = 40;
                            maxCharges = 8;
                            chargesOnReady = 3;
                            bullet = new BasicBulletType(5, 28) {{
                                keepVelocity = false;
                            }};
                        }};
                    }},
                    null,
                    new RelicType("sprint") {{
                        slotKind = SlotKind.ability;
                        equipSlot = 2;
                        ability = new DashAbility() {{
                            dashSpeed = 8;
                        }};
                    }},
                    null,
                    null
            };
        }};

        DefectKits.load();

        defect = new DefectUnitType("defect") {{
            constructor = UnitEntity::create;
            health = 400;
            speed = 3f;
            startingSlots[0] = DefectKits.lightningZap;
            startingSlots[1] = DefectKits.lightningStatic;
            startingSlots[2] = DefectKits.lightningThunder;
            startingSlots[3] = DefectKits.kitSwap;
        }};

        bindDefectRelics();

    }

    public static void loadBlocks() {
        fakeCore = new CoreBlock("fake-core") {{
            buildVisibility = BuildVisibility.editorOnly;
            size = 1;
            health = 40000;
            solid = true;
            drawCracks = false;
            targetable = false;
            unitCapModifier = 8;
            itemCapacity = 20000;
            isFirstTier = true;
            unitType = UnitTypes.alpha;
            breakable = alwaysReplace = unitMoveBreakable = false;
            cacheLayer = CacheLayer.walls;
            allowRectanglePlacement = true;
            placeEffect = Fx.rotateBlock;
            instantBuild = true;
            ignoreBuildDarkness = true;
            placeableLiquid = true;
        }};

        chest = new RelicChest("relic-chest") {{
            requirements(Category.effect, with());
            health = 40000;
            chestRarity = 1;
            openCost = new ItemStack[]{new ItemStack(Items.silicon, 5)};
            luck = 0.25f;
            addLoot(speedCharm, 10f);
            addLoot(clover, 4f);
        }};

        rareChest = new RelicChest("relic-chest-rare") {{
            requirements(Category.effect, with());
            health = 40000;
            chestRarity = 3;
            openCost = new ItemStack[]{new ItemStack(Items.silicon, 15)};
            luck = 1.2f;
            addLoot(clover, 6f);
            addLoot(finalFlash, 5f);
        }};

        gearChest = new RelicChest("relic-chest-gear") {{
            requirements(Category.effect, with());
            health = 40000;
            chestRarity = 2;
            openCost = new ItemStack[]{new ItemStack(Items.silicon, 20)};
            luck = 1.2f;
            addLoot(mendGear, 6f);
        }};

        droneChestAttack = new DroneChest("drone-chest-attack") {{
            requirements(Category.effect, with());
            health = 40000;
            chestRarity = 2;
            droneType = droneAttack;
            openCost = new ItemStack[]{new ItemStack(Items.silicon, 15)};
        }};
        droneChestHeal = new DroneChest("drone-chest-heal") {{
            requirements(Category.effect, with());
            health = 40000;
            chestRarity = 3;
            droneType = droneHeal;
            openCost = new ItemStack[]{new ItemStack(Items.silicon, 20)};
        }};
        droneChestGear = new DroneChest("drone-chest-gear") {{
            requirements(Category.effect, with());
            health = 40000;
            chestRarity = 4;
            droneType = droneGear;
            openCost = new ItemStack[]{new ItemStack(Items.silicon, 30)};
        }};

        teleporter = new Teleporter("roguelike-teleporter") {{
            requirements(Category.effect, with());
            health = 40000;
            size = 10;
            solid = false;
            underBullets = true;
            chargeRadius = 680f;
            chargeTime = 60f * 2f;
            addBossRelic(clover);
            addBossRelic(speedCharm);
        }};

        bloodShrine = new Shrine("blood-shrine") {{
            bloodCost = 0.5f;
            costMultPerUse = 0.15f;
            chanceToFail = 0;
            maxSuccessfulUses = 3;
            costScale = 20;
            cost = CostType.blood;
            reward = RewardType.item;
            itemReward = with(Items.silicon, 10);
        }};
    }

    public static void afterPlanet() {
        InteractablesGenerator.registerRelicChest((RelicChest) chest);
        InteractablesGenerator.registerRelicChest((RelicChest) rareChest);
        InteractablesGenerator.registerRelicChest((RelicChest) gearChest);
        InteractablesGenerator.registerDroneChest((DroneChest) droneChestAttack);
        InteractablesGenerator.registerDroneChest((DroneChest) droneChestHeal);
        InteractablesGenerator.registerDroneChest((DroneChest) droneChestGear);
        InteractablesGenerator.registerShrine((Shrine) bloodShrine);

        EnemySpawnDirector.register(grunt);
        EnemySpawnDirector.register(thief);

        bindDefectRelics();

        ((RelicChest) chest).addLoot(dataDisk, 3f);
        ((RelicChest) rareChest).addLoot(focusChip, 2f);
        ((RelicChest) rareChest).addLoot(goldCircuit, 1.5f);
        ((RelicChest) rareChest).addLoot(superConductor, 0.5f);

        HuntHeroAI.setStyle(grunt, HuntHeroAI.HuntStyle.aggressive);
        HuntHeroAI.setStyle(thief, HuntHeroAI.HuntStyle.flanker);
        HuntHeroAI.setStyle(UnitTypes.mace, HuntHeroAI.HuntStyle.guard);
        HuntHeroAI.setStyle(UnitTypes.fortress, HuntHeroAI.HuntStyle.shy);
        HuntHeroAI.setStyle(UnitTypes.crawler, HuntHeroAI.HuntStyle.kamikaze);
    }
}
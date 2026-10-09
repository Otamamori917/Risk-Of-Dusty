package riskod.world.block;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.ui.Bar;
import mindustry.ui.Styles;
import mindustry.world.Block;
import riskod.world.RiskodMaps;
import riskod.world.meta.Meta;
import riskod.world.relic.RelicType;
import riskod.world.run.EnemySpawnDirector;
import riskod.world.run.MockRun;
import riskod.world.run.RunState;
import riskod.world.unit.HuntHeroAI;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;

public class Teleporter extends Block {
    public float chargeRadius = 64f;
    public float chargeTime = 60f * 20f;
    public float bossSpawnOffset = 48f;
    public float bossChargeMul = 0.35f;
    public float bossChargeCap = 0.99f;

    public Seq<UnitType> bossPool = new Seq<>();
    public Seq<RelicType> bossRelicPool = new Seq<>();
    public Seq<RelicType> rareBossRelicPool = new Seq<>();
    public float rareBossRelicChance = 0.12f;

    public Teleporter(String name) {
        super(name);
        update = true;
        solid = true;
        destructible = false;
        configurable = true;
        hasItems = false;
        targetable = false;
    }

    public void addBoss(UnitType type) {
        if (type != null) bossPool.add(type);
    }

    public void addBossRelic(RelicType relic) {
        if (relic != null) bossRelicPool.add(relic);
    }

    public void addRareBossRelic(RelicType relic) {
        if (relic != null) rareBossRelicPool.add(relic);
    }

    public enum Phase {
        idle, boss, charging, ready, done
    }

    @Override
    public void setBars() {
        super.setBars();
        addBar("prog", (TeleporterBuild b) -> new Bar(
                () -> b.phase.name() + "  " + Mathf.round(b.chargeFrac() * 100f) + "%",
                () -> b.phase == Phase.ready ? Pal.heal : Pal.accent,
                b::chargeFrac
        ));
    }

    public class TeleporterBuild extends Building {
        public Phase phase = Phase.idle;
        public float charge;
        public final IntSeq bossIds = new IntSeq();
        public boolean bossDropGiven;

        public final Seq<UnitType> localBosses = new Seq<>();
        public final Seq<RelicType> localRelics = new Seq<>();
        public final Seq<RelicType> localRareRelics = new Seq<>();
        public float localChargeTime = -1f;
        public float localChargeRadius = -1f;
        public float localBossChargeMul = -1f;
        public float localRareChance = -1f;

        public float useChargeTime() {
            return localChargeTime > 0f ? localChargeTime : chargeTime;
        }

        public float useChargeRadius() {
            return localChargeRadius > 0f ? localChargeRadius : chargeRadius;
        }

        public float useBossChargeMul() {
            return localBossChargeMul > 0f ? localBossChargeMul : bossChargeMul;
        }

        public Seq<UnitType> useBosses() {
            return localBosses.any() ? localBosses : bossPool;
        }

        public Seq<RelicType> useRelics() {
            return localRelics.any() ? localRelics : bossRelicPool;
        }

        public Seq<RelicType> useRareRelics() {
            return localRareRelics.any() ? localRareRelics : rareBossRelicPool;
        }

        public float useRareChance() {
            return localRareChance >= 0f ? localRareChance : rareBossRelicChance;
        }

        public float chargeFrac() {
            return Mathf.clamp(charge / Math.max(useChargeTime(), 1f));
        }

        public boolean bossesAlive() {
            pruneBosses();
            return bossIds.size > 0;
        }

        void pruneBosses() {
            for (int i = bossIds.size - 1; i >= 0; i--) {
                Unit u = Groups.unit.getByID(bossIds.get(i));
                if (u == null || !u.isValid() || u.dead) bossIds.removeIndex(i);
            }
        }

        @Override
        public void created() {
            super.created();
            RiskodMaps.applyTeleporter(this);
        }

        public void tryActivate(Unit opener) {
            if (phase == Phase.idle) {
                spawnBosses();
                return;
            }
            if (phase == Phase.ready) {
                transfer(opener);
            }
        }

        void spawnBosses() {
            RiskodMaps.applyTeleporter(this);
            bossIds.clear();
            bossDropGiven = false;

            Seq<UnitType> pool = useBosses();
            if (MockRun.boss == null && pool.isEmpty()) {
                phase = Phase.charging;
                toast("No boss pool — charging");
                return;
            }

            phase = Phase.boss;
            UnitType type = MockRun.active ? MockRun.boss : pool.random();
            int n = 0;
            if (RunState.active()) {
                n = RunState.teleporterMult();
            } else if (MockRun.active) {
                n = MockRun.teleporterMult();
            }
            float base = Mathf.random(360f);
            for (int i = 0; i < n; i++) {
                float a = base + (360f / n) * i;
                Unit boss = type.spawn(Vars.state.rules.waveTeam,
                        x + Mathf.cosDeg(a) * bossSpawnOffset,
                        y + Mathf.sinDeg(a) * bossSpawnOffset);
                if (boss == null) continue;
                boss.apply(StatusEffects.boss);
                bossIds.add(boss.id);
                if (MockRun.active) MockRun.styleBoss(boss);
                else HuntHeroAI.apply(boss);
                if (RunState.active()) {
                    float mul = RunState.current.enemyStatMul() + EnemySpawnDirector.applyArchBuff(boss);
                    boss.maxHealth(boss.maxHealth * mul);
                    boss.health(boss.maxHealth);
                }
            }
            toast(n > 1 ? n + " bosses incoming" : "Boss incoming");
        }

        void onBossesDead() {
            if (phase != Phase.boss) return;
            if (!bossDropGiven) {
                bossDropGiven = true;
                dropBossRelics();
            }
            if (RunState.active()) {
                RunState.current.addKillXp(true);
            }
            float t = useChargeTime();
            if (charge >= t * bossChargeCap) {
                charge = t;
                phase = Phase.ready;
                if (RunState.active()) RunState.current.onTeleporterFullyCharged();
                Fx.explosion.at(x, y);
                toast("Teleporter ready — activate to leave");
            } else {
                phase = Phase.charging;
                toast("Stay near teleporter to charge");
            }
        }

        RelicType pickBossRelic(Seq<RelicType> pool, UnitType hero) {
            if (pool == null || pool.isEmpty()) return null;
            Seq<RelicType> ok = pool.select(r -> r != null && r.canDropFor(hero) && r.slotKind != RelicType.SlotKind.gear);
            if (ok.isEmpty()) return null;
            return ok.random();
        }

        void dropBossRelics() {
            int n = 0;
            if (RunState.active()) {
                n = RunState.teleporterMult();
            } else if (MockRun.active) {
                n = MockRun.teleporterMult();
            }
            float base = Mathf.random(360f);
            UnitType hero = RunState.current != null ? RunState.current.heroType : null;
            Seq<RelicType> rare = useRareRelics();
            Seq<RelicType> normal = useRelics();
            for (int i = 0; i < n; i++) {
                RelicType drop = null;
                if (rare.any() && Mathf.chance(useRareChance())) {
                    drop = pickBossRelic(rare, hero);
                }
                if (drop == null) {
                    drop = pickBossRelic(normal, hero);
                }
                if (drop == null) continue;
                float a = base + (360f / n) * i;
                RelicPickupUnitType.spawn(
                        x + Mathf.cosDeg(a) * 16f,
                        y + Mathf.sinDeg(a) * 16f,
                        drop
                );
                if (RunState.active()) RunState.current.unlockLogbook(drop);
            }
        }

        @Override
        public void updateTile() {
            if (phase == Phase.boss) {
                if (!bossesAlive()) {
                    onBossesDead();
                    return;
                }
                tickCharge(useBossChargeMul(), useChargeTime() * bossChargeCap);
                return;
            }

            if (phase == Phase.charging) {
                float t = useChargeTime();
                tickCharge(1f, t);
                if (charge >= t) {
                    charge = t;
                    phase = Phase.ready;
                    if (RunState.active()) RunState.current.onTeleporterFullyCharged();
                    Fx.explosion.at(x, y);
                    toast("Teleporter ready — activate to leave");
                }
            }
        }

        void tickCharge(float rate, float cap) {
            Unit player = Vars.player != null ? Vars.player.unit() : null;
            boolean inRange = player != null && player.isValid()
                    && player.team == team
                    && player.within(this, useChargeRadius());
            if (!inRange) return;
            charge = Math.min(cap, charge + Time.delta * rate);
        }

        void transfer(Unit opener) {
            if (phase != Phase.ready) return;
            if (MockRun.active) {
                MockRun.end();
                return;
            }
            phase = Phase.done;

            Meta.mapEscape();
            if (RunState.active()) {
                if (opener != null && opener.type instanceof PlayerCharUnitType) {
                    RunState.current.pendingLoadout = PlayerCharUnitType.loadout(opener);
                }
                RunState.current.convertCoreItemsToXp(team);
                RunState.current.prepareNextSector();
            }

            toast("Transferring…");
            RiskodMaps.playNext();
        }

        @Override
        public void buildConfiguration(Table table) {
            table.add(phase.name()).padRight(6f);
            table.button(Icon.downOpen, Styles.cleari, () -> configure(0)).size(40f);
        }

        @Override
        public void configured(Unit builder, Object value) {
            tryActivate(builder);
        }

        @Override
        public void draw() {
            super.draw();
            float z = Draw.z();
            Draw.z(Layer.blockOver);
            float rad = useChargeRadius();

            if (phase == Phase.boss || phase == Phase.charging || phase == Phase.ready) {
                Draw.color(phase == Phase.ready ? Pal.accent : phase == Phase.boss ? Pal.remove : Color.cyan, 0.25f);
                Fill.circle(x, y, rad);
                Draw.color(phase == Phase.ready ? Pal.accent : phase == Phase.boss ? Pal.remove : Color.cyan, 0.8f);
                Lines.stroke(1.5f);
                Lines.circle(x, y, rad);
            }

            if (phase == Phase.boss || phase == Phase.charging) {
                Draw.color(Pal.accent);
                Lines.stroke(3f);
                Lines.arc(x, y, size * 4f + 6f, chargeFrac(), 90f);
            }

            Draw.reset();
            Draw.z(z);
        }

        void toast(String msg) {
            if (Vars.player != null && Vars.ui != null) {
                Vars.ui.showInfoToast(msg, 2f);
            }
        }

        @Override
        public void write(Writes write) {
            super.write(write);
            write.i(phase.ordinal());
            write.f(charge);
            write.bool(bossDropGiven);
            write.i(bossIds.size);
            for (int i = 0; i < bossIds.size; i++) write.i(bossIds.get(i));
        }

        @Override
        public void read(Reads read, byte revision) {
            super.read(read, revision);
            int p = read.i();
            phase = Phase.values()[Mathf.clamp(p, 0, Phase.values().length - 1)];
            charge = read.f();
            bossDropGiven = read.bool();
            bossIds.clear();
            int n = read.i();
            for (int i = 0; i < n; i++) bossIds.add(read.i());
            RiskodMaps.applyTeleporter(this);
        }
    }
}
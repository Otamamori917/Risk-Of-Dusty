package riskod.world.meta;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.type.SectorPreset;
import mindustry.type.UnitType;
import riskod.world.RiskodMaps;
import riskod.world.relic.RelicType;

/** Unlock gate for heroes, relics, kit alts, extra kits. */
public class UnlockReq {
    public enum Kind {
        none,
        relicFound,
        relicRarity,
        enemyKills,
        itemsEarned,
        mapEscapes,
        deaths,
        damageTaken,
        healingReceived,
        interactables,
        runRelics,
        runDrones,
        runItems,
        runKills,
        runDamage,
        wins,
        stageReach,
        bossKills,
        playTime,
        levelReach,
        heroWins,
        heroLosses,
        uniqueRelics,
        perfectCounters,
        gearUses
    }

    public Kind kind = Kind.none;
    public String target = "";
    public int amount = 1;

    public UnlockReq() {
    }

    public UnlockReq(Kind kind, String target, int amount) {
        this.kind = kind;
        this.target = target == null ? "" : target;
        this.amount = Math.max(0, amount);
    }

    public static UnlockReq none() {
        return new UnlockReq(Kind.none, "", 0);
    }

    public static UnlockReq relicFound(String relicName, int times) {
        return new UnlockReq(Kind.relicFound, relicName, Math.max(1, times));
    }

    public static UnlockReq relicFound(String relicName) {
        return relicFound(relicName, 1);
    }

    public static UnlockReq relicRarity(int rarity, int count) {
        return new UnlockReq(Kind.relicRarity, String.valueOf(rarity), Math.max(1, count));
    }

    public static UnlockReq enemyKills(String unitName, int kills) {
        return new UnlockReq(Kind.enemyKills, unitName, kills);
    }

    public static UnlockReq itemsEarned(int total) {
        return new UnlockReq(Kind.itemsEarned, "", total);
    }

    public static UnlockReq mapEscapes(String mapName, int escapes) {
        return new UnlockReq(Kind.mapEscapes, mapName, escapes);
    }

    public static UnlockReq deaths(int n) {
        return new UnlockReq(Kind.deaths, "", n);
    }

    public static UnlockReq damageTaken(int n) {
        return new UnlockReq(Kind.damageTaken, "", n);
    }

    public static UnlockReq healingReceived(int n) {
        return new UnlockReq(Kind.healingReceived, "", n);
    }

    public static UnlockReq interactables(int n) {
        return new UnlockReq(Kind.interactables, "", n);
    }

    public static UnlockReq runRelics(int n) {
        return new UnlockReq(Kind.runRelics, "", n);
    }

    public static UnlockReq runDrones(int n) {
        return new UnlockReq(Kind.runDrones, "", n);
    }

    public static UnlockReq runItems(int n) {
        return new UnlockReq(Kind.runItems, "", n);
    }

    public static UnlockReq runKills(int n) {
        return new UnlockReq(Kind.runKills, "", n);
    }

    public static UnlockReq runDamage(int n) {
        return new UnlockReq(Kind.runDamage, "", n);
    }

    public static UnlockReq wins(int n) {
        return new UnlockReq(Kind.wins, "", n);
    }

    public static UnlockReq stageReach(int stage) {
        return new UnlockReq(Kind.stageReach, "", stage);
    }

    public static UnlockReq bossKills(int n) {
        return new UnlockReq(Kind.bossKills, "", n);
    }

    public static UnlockReq playTimeTicks(int ticks) {
        return new UnlockReq(Kind.playTime, "", ticks);
    }

    public static UnlockReq levelReach(int level) {
        return new UnlockReq(Kind.levelReach, "", level);
    }

    public static UnlockReq heroWins(String heroName, int n) {
        return new UnlockReq(Kind.heroWins, heroName, n);
    }

    public static UnlockReq heroLosses(String heroName, int n) {
        return new UnlockReq(Kind.heroLosses, heroName, n);
    }

    public static UnlockReq uniqueRelics(int n) {
        return new UnlockReq(Kind.uniqueRelics, "", n);
    }

    public static UnlockReq perfectCounters(int n) {
        return new UnlockReq(Kind.perfectCounters, "", n);
    }

    public static UnlockReq gearUses(int n) {
        return new UnlockReq(Kind.gearUses, "", n);
    }

    public boolean met() {
        if (kind == null || kind == Kind.none) return true;
        Meta.Data d = Meta.data();
        Meta.Totals t = d.totals;

        return switch (kind) {
            case none -> true;
            case relicFound -> {
                RelicType r = findRelic(target);
                if (r == null) yield false;
                if (Meta.isForcedUnlocked("relic", r.name)) yield true;
                Meta.RelicStat s = d.relics.get(r.name);
                yield s != null && s.found >= amount;
            }
            case relicRarity -> {
                int rarity = parseInt(target, 1);
                yield t.relicsByRarity.get(String.valueOf(rarity), 0) >= amount;
            }
            case enemyKills -> {
                UnitType u = findUnit(target);
                if (u == null) yield false;
                Meta.EnemyStat s = d.enemies.get(u.name);
                yield s != null && s.kills >= amount;
            }
            case itemsEarned -> t.itemsObtained >= amount;
            case mapEscapes -> {
                RiskodMaps.RiskodSector sector = findSector(target);
                if (sector == null) yield false;
                if (Meta.isForcedUnlocked("map", sector.name)) yield true;
                Meta.MapStat s = d.maps.get(sector.name);
                yield s != null && s.escapes >= amount;
            }
            case deaths -> totalDeaths(d) >= amount;
            case damageTaken -> t.damageReceived >= amount;
            case healingReceived -> t.healingReceived >= amount;
            case interactables -> t.interactables >= amount;
            case runRelics -> t.bestRunRelics >= amount;
            case runDrones -> t.bestRunDrones >= amount;
            case runItems -> t.bestRunItems >= amount;
            case runKills -> t.bestRunKills >= amount;
            case runDamage -> t.bestRunDamage >= amount;
            case wins -> t.runsWon >= amount;
            case stageReach -> t.bestStage >= amount;
            case bossKills -> t.bossesKilled >= amount;
            case playTime -> t.timePlayed >= amount;
            case levelReach -> t.bestLevel >= amount;
            case heroWins -> {
                UnitType h = findUnit(target);
                if (h == null) yield false;
                if (Meta.isForcedUnlocked("hero", h.name)) yield true;
                Meta.HeroStat hs = d.heroes.get(h.name);
                yield hs != null && hs.wins >= amount;
            }
            case heroLosses -> {
                UnitType h = findUnit(target);
                if (h == null) yield false;
                Meta.HeroStat hs = d.heroes.get(h.name);
                yield hs != null && Math.max(0, hs.runs - hs.wins) >= amount;
            }
            case uniqueRelics -> d.relics.size >= amount;
            case perfectCounters -> t.perfectCounters >= amount;
            case gearUses -> t.gearUses >= amount;
        };
    }

    public String describe() {
        if (kind == null || kind == Kind.none) return "Always unlocked";
        return switch (kind) {
            case none -> "Always unlocked";
            case relicFound -> {
                RelicType r = findRelic(target);
                String n = r != null ? r.localizedName : target;
                yield amount <= 1 ? "Find relic \"" + n + "\"" : "Find relic \"" + n + "\" x" + amount;
            }
            case relicRarity -> "Obtain rarity " + target + " relics x" + amount;
            case enemyKills -> {
                UnitType u = findUnit(target);
                String n = u != null ? u.localizedName : target;
                yield amount <= 1 ? "Kill \"" + n + "\"" : "Kill \"" + n + "\" x" + amount;
            }
            case itemsEarned -> "Earn " + amount + " items";
            case mapEscapes -> {
                RiskodMaps.RiskodSector s = findSector(target);
                String n = s != null ? s.localizedName : target;
                boolean moon = s != null && s == RiskodMaps.moon;
                String base = moon ? "Beat the planet" : "Escape \"" + n + "\"";
                yield amount <= 1 ? base : base + " x" + amount;
            }
            case deaths -> "Die x" + amount;
            case damageTaken -> "Take " + amount + " damage";
            case healingReceived -> "Receive " + amount + " healing";
            case interactables -> "Use interactables x" + amount;
            case runRelics -> "Hold " + amount + " relics in one run";
            case runDrones -> "Have " + amount + " drones in one run";
            case runItems -> "Gain " + amount + " items in one run";
            case runKills -> "Get " + amount + " kills in one run";
            case runDamage -> "Deal " + amount + " damage in one run";
            case wins -> "Win x" + amount;
            case stageReach -> "Reach stage " + amount;
            case bossKills -> "Kill " + amount + " bosses";
            case playTime -> "Play " + Math.max(1, amount / 3600) + " minutes";
            case levelReach -> "Reach level " + amount;
            case heroWins -> {
                UnitType h = findUnit(target);
                String n = h != null ? h.localizedName : target;
                yield "Win as " + n + " x" + amount;
            }
            case heroLosses -> {
                UnitType h = findUnit(target);
                String n = h != null ? h.localizedName : target;
                yield "Lose as " + n + " x" + amount;
            }
            case uniqueRelics -> "Discover " + amount + " unique relics";
            case perfectCounters -> "Perfect counter x" + amount;
            case gearUses -> "Use gear x" + amount;
        };
    }

    static int totalDeaths(Meta.Data d) {
        if (d.totals.runsLost > 0) return d.totals.runsLost;
        int sum = 0;
        for (var e : d.heroes) {
            sum += Math.max(0, e.value.runs - e.value.wins);
        }
        return sum;
    }

    static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }

    static String key(String q) {
        if (q == null) return "";
        return q.trim().toLowerCase().replace(' ', '-').replace('_', '-');
    }

    static RelicType findRelic(String q) {
        if (q == null || q.isEmpty()) return null;
        String k = key(q);
        RelicType exact = RelicType.all.find(r ->
                r.name.equalsIgnoreCase(k) || r.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        exact = RelicType.all.find(r ->
                r.localizedName != null && r.localizedName.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        RelicType hit = RelicType.all.find(r -> r.name.toLowerCase().contains(k));
        if (hit != null) return hit;
        String low = q.trim().toLowerCase();
        return RelicType.all.find(r ->
                r.localizedName != null && r.localizedName.toLowerCase().contains(low));
    }

    static UnitType findUnit(String q) {
        if (q == null || q.isEmpty()) return null;
        String k = key(q);
        Seq<UnitType> all = Vars.content.units();
        UnitType exact = all.find(t ->
                t.name.equalsIgnoreCase(k) || t.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        exact = all.find(t ->
                t.localizedName != null && t.localizedName.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        UnitType hit = all.find(t -> t.name.toLowerCase().contains(k));
        if (hit != null) return hit;
        String low = q.trim().toLowerCase();
        return all.find(t ->
                t.localizedName != null && t.localizedName.toLowerCase().contains(low));
    }

    static RiskodMaps.RiskodSector findSector(String q) {
        if (q == null || q.isEmpty()) return null;
        String k = key(q);
        Seq<RiskodMaps.RiskodSector> all = new Seq<>();
        all.addAll(RiskodMaps.easy);
        all.addAll(RiskodMaps.hard);
        if (RiskodMaps.launch != null) all.add(RiskodMaps.launch);
        if (RiskodMaps.moon != null) all.add(RiskodMaps.moon);
        for (SectorPreset s : Vars.content.sectors()) {
            if (s instanceof RiskodMaps.RiskodSector rs && !all.contains(rs)) all.add(rs);
        }
        RiskodMaps.RiskodSector exact = all.find(s ->
                s.name.equalsIgnoreCase(k) || s.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        exact = all.find(s ->
                s.localizedName != null && s.localizedName.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        RiskodMaps.RiskodSector hit = all.find(s -> s.name.toLowerCase().contains(k));
        if (hit != null) return hit;
        String low = q.trim().toLowerCase();
        return all.find(s ->
                s.localizedName != null && s.localizedName.toLowerCase().contains(low));
    }
}
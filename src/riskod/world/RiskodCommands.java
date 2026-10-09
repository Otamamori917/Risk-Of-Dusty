package riskod.world;

import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.game.Gamemode;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.mod.Scripts;
import mindustry.type.Sector;
import mindustry.type.SectorPreset;
import mindustry.type.UnitType;
import riskod.world.abilites.ChargedAbility;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.meta.Meta;
import riskod.world.relic.ChantRelic;
import riskod.world.relic.RelicType;
import riskod.world.run.HeroKitPrefs;
import riskod.world.run.MockRun;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.ui.CommandHelpUi;
import riskod.world.ui.HeroSelectUi;
import riskod.world.ui.InfoUi;
import riskod.world.unit.HuntHeroAI;
import riskod.world.unit.PlayerCharUnitType;
import riskod.world.unit.RelicPickupUnitType;
import rhino.NativeJavaClass;
import rhino.Scriptable;
import rhino.ScriptableObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Sandbox debug: F8 helpers on the riskod object (riskod.help(), riskod.relic(...), ...).
 */
public class RiskodCommands {

    static final String DEV_SALT = "riskod-dev:";
    static final String DEV_HASH = "01a34d974b6bd61b92670515b819677cac533cbe9ada1527fd28de69c1b3dd60";

    public static boolean devUnlocked;

    static boolean god;
    static boolean noCooldowns;

    public static void register() {
        Events.on(ClientLoadEvent.class, e -> installJs());
        Events.on(WorldLoadEvent.class, e -> {
            god = false;
            noCooldowns = false;
        });
        Events.run(Trigger.update, () -> {
            tickGod();
            tickNoCooldowns();
        });
        MockRun.register();
        CommandHelpUi.register();
    }

    public static boolean devUnlocked() {
        return devUnlocked;
    }

    static void installJs() {
        try {
            Scripts scripts = Vars.mods.getScripts();
            java.lang.reflect.Field scopeField = Scripts.class.getDeclaredField("scope");
            scopeField.setAccessible(true);
            Scriptable scope = (Scriptable) scopeField.get(scripts);
            ScriptableObject.putProperty(scope, "RiskodCommands", new NativeJavaClass(scope, RiskodCommands.class));

            scripts.runConsole(
                    """
                    this.riskod = {
                        help: function(){ RiskodCommands.help(); },
                        relic: function(n, x, y){ RiskodCommands.spawnRelic(String(n), x == null ? NaN : +x, y == null ? NaN : +y); },
                        give: function(n){ RiskodCommands.giveRelic(String(n)); },
                        removePassive: function(n){ RiskodCommands.removePassive(String(n)); },
                        removeSlot: function(i){ RiskodCommands.removeSlot(i|0); },
                        relics: function(f){ RiskodCommands.listRelics(f == null ? "" : String(f)); },
                        heroes: function(){ RiskodCommands.listHeroes(); },
                        hero: function(n){ RiskodCommands.spawnHero(String(n)); },
                        pickHero: function(){ RiskodCommands.pickHero(); },
                        loadout: function(){ RiskodCommands.printLoadout(); },
                        passives: function(){ RiskodCommands.printPassives(); },
                        noCooldowns: function(){ RiskodCommands.toggleNoCooldowns(); },
                        clear: function(){ RiskodCommands.clearPassives(); },
                        enemy: function(n, count, style, x, y){ RiskodCommands.spawnEnemy(String(n), count == null ? 1 : count|0, x == null ? NaN : +x, y == null ? NaN : +y, style == null ? "" : String(style)); },
                        god: function(){ RiskodCommands.toggleGod(); },
                        tp: function(x, y){ RiskodCommands.teleport(+x, +y); },
                        killAll: function(){ RiskodCommands.killAll(); },
                        relicAll: function(f){ RiskodCommands.spawnAllRelics(f == null ? "" : String(f)); },
                        uniques: function(){ RiskodCommands.listUniques(); },
                        resetUniques: function(){ RiskodCommands.resetUniques(); },
                        mockRun: function(c, s, b, style){ RiskodCommands.mockRun(c == null ? -1 : c|0, s == null ? -1 : s|0, b == null ? "" : String(b), style == null ? "" : String(style)); },
                        endMockRun: function(){ RiskodCommands.endMockRun(); },
                        dev: function(p){ RiskodCommands.dev(String(p)); }
                    };
                    """
            );
            if (devUnlocked()) installDevJs();
        } catch (Throwable t) {
            arc.util.Log.err("Riskod: failed to install JS console helpers", t);
        }
    }

    static void installDevJs() {
        try {
            Vars.mods.getScripts().runConsole(
                    """
                    riskod.unlockRelic = function(n){ RiskodCommands.unlockRelic(String(n)); };
                    riskod.lockRelic = function(n){ RiskodCommands.lockRelic(String(n)); };
                    riskod.unlockHero = function(n){ RiskodCommands.unlockHero(String(n)); };
                    riskod.lockHero = function(n){ RiskodCommands.lockHero(String(n)); };
                    riskod.unlockMap = function(n){ RiskodCommands.unlockMap(String(n)); };
                    riskod.lockMap = function(n){ RiskodCommands.lockMap(String(n)); };
                    """
            );
        } catch (Throwable t) {
            arc.util.Log.err("Riskod: failed to install dev console helpers", t);
        }
    }

    // ——— public for JS ———

    public static void help() {
        CommandHelpUi.show();
    }

    public static void dev(String password) {
        if (devUnlocked) {
            msg("Dev commands already unlocked.");
            return;
        }
        if (password == null || !sha256(DEV_SALT + password).equals(DEV_HASH)) {
            msg("Wrong password.");
            return;
        }
        devUnlocked = true;
        installDevJs();
        msg("Dev commands unlocked");
    }

    public static void spawnRelic(String name, double tx, double ty) {
        if (notSandbox()) return;
        RelicType r = findRelic(name);
        if (r == null) {
            msg("Unknown relic. riskod.relics()");
            return;
        }
        Player p = Vars.player;
        UnitType hero = heroOf(p);
        if (!r.canDropFor(hero)) {
            msg("Wrong hero for " + r.localizedName);
            return;
        }
        PlayerLoadout l = loadout(p);
        if (r.unstackable && l != null && l.blocksUnstackable(r)) {
            msg("Already own unstackable " + r.localizedName);
            return;
        }
        if (RunState.current != null && RunState.consumedUniques.contains(r.name)) {
            msg("Consumed unique: " + r.localizedName);
            return;
        }
        float wx, wy;
        if (Double.isNaN(tx) || Double.isNaN(ty)) {
            Unit u = p != null ? p.unit() : null;
            if (u == null || !u.isValid()) {
                msg("No unit.");
                return;
            }
            wx = u.x;
            wy = u.y;
        } else {
            wx = (float) tx * Vars.tilesize;
            wy = (float) ty * Vars.tilesize;
        }
        RelicPickupUnitType.spawn(wx, wy, r);
        msg("Spawned " + r.localizedName);
    }

    public static void giveRelic(String name) {
        if (notSandbox()) return;
        RelicType r = findRelic(name);
        if (r == null) {
            msg("Unknown relic.");
            return;
        }
        Player p = Vars.player;
        if (!r.canDropFor(heroOf(p))) {
            msg("Wrong hero for " + r.localizedName);
            return;
        }
        PlayerLoadout l = ensureLoadout(p);
        if (l == null) return;
        if (!l.tryPickup(r)) {
            msg("Could not add.");
            return;
        }
        l.onPickedUp(p.unit(), r);
        if (RunState.active()) RunState.current.unlockLogbook(r);
        Meta.noteFound(r);
        msg("Gave " + r.localizedName);
    }

    public static void removePassive(String name) {
        if (notSandbox()) return;
        PlayerLoadout l = loadout(Vars.player);
        if (l == null) {
            msg("No loadout.");
            return;
        }
        RelicType r = findRelic(name);
        if (r == null || !l.hasPassive(r)) {
            msg("Passive not on you.");
            return;
        }
        l.takePassive(r);
        msg("Removed " + r.localizedName);
    }

    public static void removeSlot(int slot) {
        if (notSandbox()) return;
        PlayerLoadout l = loadout(Vars.player);
        if (l == null) {
            msg("No loadout.");
            return;
        }
        if (slot < 0 || slot >= PlayerLoadout.SLOT_COUNT) {
            msg("Slot 0-3 abilities, 4 gear.");
            return;
        }
        RelicType was = l.slots[slot];
        l.slots[slot] = null;
        if (slot == PlayerLoadout.GEAR_SLOT) {
            l.gearCharges = 0;
            l.gearCooldownTimer = 0f;
        }
        l.recompute();
        msg("Cleared slot " + slot + (was == null ? "" : " (" + was.localizedName + ")"));
    }

    public static void listRelics(String filter) {
        String f = filter == null ? "" : filter.toLowerCase();
        Seq<String> lines = new Seq<>();
        for (RelicType r : RelicType.all) {
            if (!f.isEmpty()
                    && !r.name.contains(f)
                    && (r.localizedName == null || !r.localizedName.toLowerCase().contains(f))) continue;
            boolean named = r.localizedName != null && !r.localizedName.equalsIgnoreCase(r.name);
            lines.add(r.name + (named ? " [lightgray](" + r.localizedName + ")[]" : ""));
        }
        if (lines.isEmpty()) {
            msg("No matches.");
            return;
        }
        InfoUi.show("Relics (" + lines.size + ")", lines);
    }

    public static void listHeroes() {
        Seq<UnitType> heroes = heroTypes();
        if (heroes.isEmpty()) {
            msg("No hero units found.");
            return;
        }
        Seq<String> lines = new Seq<>();
        for (UnitType t : heroes) {
            boolean named = t.localizedName != null && !t.localizedName.equalsIgnoreCase(t.name);
            lines.add(t.name + (named ? " [lightgray](" + t.localizedName + ")[]" : ""));
        }
        InfoUi.show("Heroes (" + lines.size + ")", lines);
    }

    public static void spawnHero(String name) {
        if (notSandbox()) return;
        UnitType t = findHero(name);
        if (t == null) {
            msg("Unknown hero. riskod.heroes()");
            return;
        }
        Player p = Vars.player;
        if (p == null) {
            msg("No player.");
            return;
        }
        Unit old = p.unit();
        float x = old != null && old.isValid() ? old.x : p.x;
        float y = old != null && old.isValid() ? old.y : p.y;
        Unit u = t.create(p.team());
        u.set(x, y);
        u.add();
        p.unit(u);
        if (old != null && old.isValid() && old != u) old.remove();
        msg("Now playing " + t.localizedName);
    }

    public static void pickHero() {
        if (notSandbox()) return;
        if (PlayerCharUnitType.selectable.isEmpty()) {
            msg("No selectable heroes.");
            return;
        }
        HeroSelectUi.show(hero -> {
            spawnHeroUnit(hero);
            if (hero instanceof PlayerCharUnitType pc) {
                Unit u = Vars.player.unit();
                if (u != null && u.isValid()) {
                    HeroKitPrefs.sanitize(pc);
                    PlayerLoadout l = HeroKitPrefs.buildLoadout(pc);
                    PlayerCharUnitType.loadouts.put(u.id, l);
                    KitSwapAbility swap = KitSwapAbility.find(l);
                    if (swap != null) {
                        swap.applyPrefsSelection(u, pc);
                        swap.applyCurrentKit(u, l);
                    }
                }
            }
            msg("Playing " + hero.localizedName);
        });
    }

    static void spawnHeroUnit(UnitType t) {
        Player p = Vars.player;
        if (p == null) return;
        Unit old = p.unit();
        float x = old != null && old.isValid() ? old.x : p.x;
        float y = old != null && old.isValid() ? old.y : p.y;
        Unit u = t.create(p.team());
        u.set(x, y);
        u.add();
        p.unit(u);
        if (old != null && old.isValid() && old != u) old.remove();
    }

    public static void printLoadout() {
        if (notSandbox()){
            Vars.state.set(GameState.State.paused);
        }        PlayerLoadout l = loadout(Vars.player);
        if (l == null) {
            msg("No loadout.");
            return;
        }
        Unit unit = Vars.player.unit();
        Seq<String> lines = new Seq<>();
        String[] names = {"Primary", "Secondary", "Utility", "Special"};

        lines.add("[accent]Abilities[]");
        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            RelicType r = l.slots[i];
            String label = names[i] + " [" + i + "]";
            if (r == null) {
                lines.add(label + ": [gray]empty[]");
                continue;
            }
            StringBuilder line = new StringBuilder(label + ": [accent]" + r.localizedName + "[]");
            if (unit != null && r.ability instanceof ChargedAbility c) {
                ChargedAbility.ChargeState s = c.state(unit);
                int max = c.effectiveMax(l, i);
                line.append("  ").append(s.charges).append("/").append(max);
                if (s.charges < max) {
                    line.append("  cd ").append(Strings.autoFixed(s.timer / 60f, 1))
                            .append("/").append(Strings.autoFixed(c.effectiveCooldown(l, i) / 60f, 1)).append("s");
                }
            }
            lines.add(line.toString());

            StringBuilder m = new StringBuilder();
            mul(m, "dmg", l.slotDamageMul(i));
            mul(m, "range", l.slotRangeMul(i));
            mul(m, "cd", l.slotCooldownMul(i));
            add(m, "charges", l.slotBonusCharges(i));
            if (!m.isEmpty()) lines.add("    [lightgray]" + m + "[]");
        }

        lines.add("");
        lines.add("[accent]Gear [4][]");
        RelicType gear = l.slots[PlayerLoadout.GEAR_SLOT];
        if (gear == null) {
            lines.add("[gray]empty[]");
        } else {
            lines.add("[accent]" + gear.localizedName + "[]  "
                    + l.gearCharges + "/" + l.effectiveGearMax()
                    + "  cd " + Strings.autoFixed(l.gearCooldownTimer / 60f, 1)
                    + "/" + Strings.autoFixed(l.effectiveGearCooldown() / 60f, 1) + "s");
        }

        lines.add("");
        lines.add("[lightgray]" + l.passives.size + " passive(s) — riskod.passives()[]");
        if (noCooldowns) lines.add("[stat]noCooldowns ON[]");
        if (god) lines.add("[stat]god ON[]");

        InfoUi.show("Loadout", lines);
    }

    public static void printPassives() {
        if (notSandbox()){
            Vars.state.set(GameState.State.paused);
        }
        PlayerLoadout l = loadout(Vars.player);
        if (l == null) {
            msg("No loadout.");
            return;
        }
        if (l.passives.isEmpty()) {
            msg("No passives.");
            return;
        }

        boolean smearedOn = l.hasPassiveFlag(r -> r.smeared);
        Seq<String> lines = new Seq<>();

        for (int i = 0; i < l.passives.size; i++) {
            RelicType r = l.passives.get(i);
            int shrines = i < l.passiveShrines.size ? l.passiveShrines.get(i) : 0;

            StringBuilder head = new StringBuilder();
            head.append("[accent]").append(i + 1).append(". ").append(r.localizedName).append("[]");
            if (r instanceof ChantRelic c) {
                head.append(" [lightgray](chant x").append(Strings.autoFixed(c.scale(shrines), 2))
                        .append(", ").append(shrines).append(" shrines)[]");
            }
            lines.add(head.toString());

            if (r.smeared) {
                lines.add("    [stat]mirrors[] primary↔utility, secondary↔special");
                continue;
            }
            if (r.obelisk) {
                float mul = l.obeliskCdMul();
                int pct = Math.round((1f - mul) * 100f);
                lines.add("    stacks [stat]" + l.obeliskStacks + "[]  CD [stat]-" + pct + "%[] (x"
                        + Strings.autoFixed(mul, 2) + ")  last slot: "
                        + (l.obeliskLastSlot < 0 ? "none" : String.valueOf(l.obeliskLastSlot)));
                continue;
            }
            if (r.negativeObelisk) {
                float mul = l.negativeObeliskDmgMul();
                int pct = Math.round((mul - 1f) * 100f);
                lines.add("    stacks [stat]" + l.negativeObeliskStacks + "[]  dmg [stat]+" + pct + "%[] (x"
                        + Strings.autoFixed(mul, 2) + ")  last slot: "
                        + (l.obeliskLastSlot < 0 ? "none" : String.valueOf(l.obeliskLastSlot)));
                continue;
            }
            if (r.plasmaBankPermanent) {
                lines.add("    [stat]plasma bank permanent[] — excess energy no longer expires after 3 pulses");
                continue;
            }
            if (r.stuntMan) {
                lines.add("    [stat]-2 charges[] on every ability/gear (or [stat]+10% CD[] per charge you cannot pay)");
                lines.add("    [stat]+20%[] damage and range on all ability slots");
                continue;
            }

            StringBuilder global = new StringBuilder();
            mul(global, "health", r.healthMul);
            mul(global, "speed", r.speedMul);
            add(global, "gear charges", r.bonusGearCharges);
            mul(global, "gear cd", r.gearCooldownMul);
            add(global, "luck", r.bonusLuck);
            mul(global, "luck", r.luckMul);
            add(global, "focus", r.bonusFocus);
            add(global, "orbs", r.bonusOrbCapacity);
            add(global, "energy cap", r.bonusEnergyCap);
            mul(global, "pulse interval", r.pulseIntervalMul);
            if (!global.isEmpty()) lines.add("    " + global);

            String slotLine = formatSlotApply(r, smearedOn);
            if (slotLine != null) lines.add("    " + slotLine);
        }

        lines.add("");
        lines.add("[accent]Totals[]");
        StringBuilder total = new StringBuilder();
        mul(total, "health", l.healthMul);
        mul(total, "speed", l.speedMul);
        add(total, "luck", l.effectiveLuck());
        mul(total, "gear cd", l.gearCooldownMul);
        add(total, "gear charges", l.bonusGearCharges);
        lines.add(total.isEmpty() ? "[gray]no global buffs[]" : total.toString());

        String[] names = {"primary", "secondary", "utility", "special"};
        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            StringBuilder s = new StringBuilder();
            mul(s, "dmg", l.slotDamageMul(i));
            mul(s, "range", l.slotRangeMul(i));
            mul(s, "cd", l.slotCooldownMul(i));
            add(s, "charges", l.slotBonusCharges(i));
            if (!s.isEmpty()) lines.add(names[i] + ": " + s);
        }

        InfoUi.show("Passives (" + l.passives.size + ")", lines);
    }

    /** e.g. primary [and utility]: dmg x1.2, cd x0.9 */
    static String formatSlotApply(RelicType r, boolean smearedOn) {
        RelicType.ApplyType t = r.bonusApplyTo;
        if (t == RelicType.ApplyType.gear) return null;

        StringBuilder effects = new StringBuilder();
        mul(effects, "dmg", r.abilityDamageMul);
        mul(effects, "range", r.abilityRangeMul);
        mul(effects, "cd", r.abilityCooldownMul);
        add(effects, "charges", r.bonusAbilityCharges);
        if (effects.isEmpty() && t != RelicType.ApplyType.allMain && t != RelicType.ApplyType.ALL) {
            return null;
        }

        if (t == RelicType.ApplyType.allMain || t == RelicType.ApplyType.ALL) {
            String label = t == RelicType.ApplyType.ALL ? "all slots+gear" : "all mains";
            return effects.isEmpty() ? null : label + ": " + effects;
        }

        String sibling = smearedOn ? smearedSibling(t) : null;
        String label = t.name();
        if (sibling != null) {
            label = t.name() + " [green]\r[\r[]and [stat]" + sibling + "[][green]\r]\r[]";
        }
        return effects.isEmpty() ? null : label + ": " + effects;
    }

    static String smearedSibling(RelicType.ApplyType t) {
        return switch (t) {
            case primary -> "utility";
            case secondary -> "special";
            case utility -> "primary";
            case special -> "secondary";
            default -> null;
        };
    }

    public static void spawnEnemy(String name, int count, double tx, double ty, String style) {
        if (notSandbox()) return;
        UnitType t = findUnitType(name);
        if (t == null) {
            msg("Unknown unit.");
            return;
        }

        boolean keepAi = style != null && style.equalsIgnoreCase("none");
        HuntHeroAI.HuntStyle st = null;
        if (!keepAi && style != null && !style.isEmpty()) {
            st = findHuntStyle(style);
            if (st == null) {
                msg("Unknown style \"" + style + "\". Styles: " + styleNames() + ", none");
                return;
            }
        }
        if (!keepAi && st == null) {
            st = HuntHeroAI.styles.get(t);
            if (st == null) st = HuntHeroAI.HuntStyle.aggressive;
        }

        float wx, wy;
        if (Double.isNaN(tx) || Double.isNaN(ty)) {
            Player p = Vars.player;
            Unit u = p != null ? p.unit() : null;
            if (u == null || !u.isValid()) {
                msg("No unit.");
                return;
            }
            wx = u.x + Mathf.range(Vars.tilesize * 6f);
            wy = u.y + Mathf.range(Vars.tilesize * 6f);
        } else {
            wx = (float) tx * Vars.tilesize;
            wy = (float) ty * Vars.tilesize;
        }

        int n = Mathf.clamp(count, 1, 100);
        int made = 0;
        for (int i = 0; i < n; i++) {
            float sx = wx + (n > 1 ? Mathf.range(24f) : 0f);
            float sy = wy + (n > 1 ? Mathf.range(24f) : 0f);
            Unit u = t.spawn(Vars.state.rules.waveTeam, sx, sy);
            if (u == null) continue;
            if (!keepAi) {
                HuntHeroAI ai = new HuntHeroAI();
                ai.style = st;
                u.controller(ai);
            }
            made++;
        }
        msg("Spawned " + made + " " + t.localizedName + (keepAi ? "" : " (" + st.name() + ")"));
    }

    public static void toggleGod() {
        if (notSandbox()) return;
        god = !god;
        msg("God mode " + (god ? "on" : "off"));
    }

    public static void toggleNoCooldowns() {
        if (notSandbox()) return;
        noCooldowns = !noCooldowns;
        msg("no cooldowns mode " + (noCooldowns ? "on" : "off"));
    }

    public static void teleport(double tx, double ty) {
        if (notSandbox()) return;
        Player p = Vars.player;
        Unit u = p != null ? p.unit() : null;
        if (u == null || !u.isValid()) {
            msg("No unit.");
            return;
        }
        u.set((float) tx * Vars.tilesize, (float) ty * Vars.tilesize);
        u.vel.setZero();
        msg("Teleported to " + Strings.autoFixed((float) tx, 1) + ", " + Strings.autoFixed((float) ty, 1));
    }

    public static void killAll() {
        if (notSandbox()) return;
        Seq<Unit> targets = new Seq<>();
        Groups.unit.each(u -> {
            if (u.team == Vars.state.rules.waveTeam && u.isValid()) targets.add(u);
        });
        for (Unit u : targets) u.kill();
        msg("Killed " + targets.size + " units.");
    }

    public static void spawnAllRelics(String filter) {
        if (notSandbox()) return;
        Player p = Vars.player;
        Unit u = p != null ? p.unit() : null;
        if (u == null || !u.isValid()) {
            msg("No unit.");
            return;
        }
        UnitType hero = heroOf(p);
        PlayerLoadout l = loadout(p);
        String f = filter == null ? "" : filter.toLowerCase();
        int n = 0;
        for (RelicType r : RelicType.all) {
            if (!f.isEmpty()
                    && !r.name.contains(f)
                    && (r.localizedName == null || !r.localizedName.toLowerCase().contains(f))) continue;
            if (!r.canDropFor(hero)) continue;
            if (r.unstackable && l != null && l.blocksUnstackable(r)) continue;
            if (RunState.consumedUniques.contains(r.name)) continue;
            float wx = u.x + Vars.tilesize * 3f + (n % 10) * Vars.tilesize * 2f;
            float wy = u.y + Vars.tilesize * 3f + ((float) n / 10) * Vars.tilesize * 2f;
            RelicPickupUnitType.spawn(wx, wy, r);
            if (++n >= 40) break;
        }
        msg(n == 0 ? "No matches." : "Spawned " + n + " relics.");
    }

    public static void unlockRelic(String name) {
        if (!devUnlocked) return;
        RelicType r = findRelic(name);
        if (r == null) { msg("Unknown relic."); return; }
        Meta.forceUnlock("relic", r.name);
        Meta.noteFound(r);
        msg("Unlocked: " + r.localizedName);
    }

    public static void lockRelic(String name) {
        if (!devUnlocked) return;
        RelicType r = findRelic(name);
        if (r == null) { msg("Unknown relic."); return; }
        Meta.forceLock("relic", r.name);
        Meta.data().relics.remove(r.name);
        Meta.save();
        HeroKitPrefs.sanitizeAll();
        msg("Locked: " + r.localizedName);
    }

    public static void unlockHero(String name) {
        if (!devUnlocked) return;
        UnitType u = findHero(name);
        if (u == null) { msg("Unknown hero."); return; }
        Meta.forceUnlock("hero", u.name);
        Meta.noteFound(u);
        msg("Unlocked: " + u.localizedName);
    }

    public static void lockHero(String name) {
        if (!devUnlocked) return;
        UnitType u = findHero(name);
        if (u == null) { msg("Unknown hero."); return; }
        Meta.forceLock("hero", u.name);
        Meta.data().heroes.remove(u.name);
        Meta.save();
        HeroKitPrefs.sanitizeAll();
        msg("Locked: " + u.localizedName);
    }

    public static void unlockMap(String name) {
        if (!devUnlocked) return;
        RiskodMaps.RiskodSector s = findSector(name);
        if (s == null) {
            msg("Unknown map.");
            return;
        }
        Meta.noteFound(s.sector);
        msg("Logbook unlocked: " + s.localizedName);
    }

    public static void lockMap(String name) {
        if (!devUnlocked) return;
        RiskodMaps.RiskodSector s = findSector(name);
        if (s == null) {
            msg("Unknown map.");
            return;
        }
        if (Meta.data().maps.remove(s.name) == null) {
            msg("Not unlocked: " + s.localizedName);
            return;
        }
        Meta.save();
        try {
            HeroKitPrefs.sanitizeAll();
        } catch (Throwable e) {
            Log.info(e);
        }
        msg("Logbook locked: " + s.localizedName);
    }

    public static void listUniques() {
        if (notSandbox()) return;
        if (RunState.consumedUniques.isEmpty()) {
            msg("No consumed uniques.");
            return;
        }
        Seq<String> lines = new Seq<>();
        for (String s : RunState.consumedUniques) lines.add(s);
        InfoUi.show("Consumed uniques (" + lines.size + ")", lines);
    }

    public static void resetUniques() {
        if (notSandbox()) return;
        RunState.consumedUniques.clear();
        msg("Consumed uniques cleared.");
    }

    public static void mockRun(int chests, int shrines, String bossName, String style) {
        if (notSandbox() || RunState.active()) return;
        UnitType t = bossName.isEmpty() ? null : findUnitType(bossName);
        Player p = Vars.player;
        Unit u = p != null ? p.unit() : null;
        if (u == null || !u.isValid() || !(u.type instanceof PlayerCharUnitType)) {
            msg("Mock run needs a hero unit. riskod.hero(name)");
            return;
        }

        boolean keepAi = style != null && style.equalsIgnoreCase("none");
        HuntHeroAI.HuntStyle st = null;
        if (!keepAi && style != null && !style.isEmpty()) {
            st = findHuntStyle(style);
            if (st == null) {
                msg("Unknown style \"" + style + "\". Styles: " + styleNames() + ", none");
                return;
            }
        }
        if (!keepAi && st == null && t != null) {
            st = HuntHeroAI.styles.get(t);
            if (st == null) st = HuntHeroAI.HuntStyle.aggressive;
        }

        if (!MockRun.start(chests, shrines, t, st, keepAi)) {
            msg("Mock run already active, or no world. riskod.endMockRun()");
            return;
        }
        msg("Mock run started. Activate the teleporter's final stage or riskod.endMockRun() to clean up.");
    }

    public static void endMockRun() {
        if (notSandbox() || RunState.active()) return;
        if (!MockRun.active) {
            msg("No mock run active.");
            return;
        }
        MockRun.end();
    }

    static String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    static void tickGod() {
        if (!god) return;
        Player p = Vars.player;
        Unit u = p != null ? p.unit() : null;
        if (u == null || !u.isValid() || u.health >= u.maxHealth) return;
        u.health = u.maxHealth;
        if (u.type instanceof PlayerCharUnitType) PlayerCharUnitType.syncHealth(u);
    }

    static void tickNoCooldowns() {
        if (!noCooldowns || notSandboxSilent()) return;
        Player p = Vars.player;
        Unit u = p != null ? p.unit() : null;
        if (u == null || !u.isValid()) return;
        PlayerLoadout l = loadout(p);
        if (l == null) return;

        for (int i = 0; i < PlayerLoadout.FLEX_SLOTS; i++) {
            RelicType r = l.slots[i];
            if (r != null && r.ability instanceof ChargedAbility c) {
                ChargedAbility.ChargeState s = c.state(u);
                s.charges = c.effectiveMax(l, i);
                s.timer = 0f;
                s.inited = true;
            }
        }
        l.gearCharges = l.effectiveGearMax();
        l.gearCooldownTimer = 0f;
    }

    static void mul(StringBuilder sb, String label, float v) {
        if (Math.abs(v - 1f) < 0.001f) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(label).append(" x").append(Strings.autoFixed(v, 2));
    }

    static void add(StringBuilder sb, String label, float v) {
        if (Math.abs(v) < 0.001f) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(label).append(v > 0f ? " +" : " ").append(Strings.autoFixed(v, 2));
    }

    static boolean notSandbox() {
        boolean ok = Vars.state != null
                && Vars.state.rules != null
                && Vars.state.rules.mode() == Gamemode.sandbox;
        if (!ok) msg("Only available in sandbox.");
        return !ok;
    }

    static boolean notSandboxSilent() {
        return Vars.state == null || Vars.state.rules == null || Vars.state.rules.mode() != Gamemode.sandbox;
    }

    static void msg(String text) {
        Player p = Vars.player;
        if (p != null) p.sendMessage("[accent][riskod][] " + text);
        else if (Vars.ui != null) Vars.ui.showInfoToast(text, 3f);
        arc.util.Log.info("[riskod] " + text);
    }

    static UnitType heroOf(Player player) {
        if (RunState.current != null && RunState.current.heroType != null) {
            return RunState.current.heroType;
        }
        Unit u = player != null ? player.unit() : null;
        return u != null && u.isValid() ? u.type : null;
    }

    static PlayerLoadout loadout(Player player) {
        if (player == null) return null;
        Unit u = player.unit();
        if (u == null || !u.isValid()) return null;
        if (u.type instanceof PlayerCharUnitType) return PlayerCharUnitType.loadout(u);
        return null;
    }

    static PlayerLoadout ensureLoadout(Player player) {
        PlayerLoadout l = loadout(player);
        if (l == null) msg("Need a hero unit (PlayerCharUnitType).");
        return l;
    }

    static RelicType findRelic(String q) {
        if (q == null || q.isEmpty()) return null;
        String key = q.trim().toLowerCase().replace(' ', '-');
        RelicType exact = RelicType.all.find(r ->
                r.name.equalsIgnoreCase(key) || r.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        return RelicType.all.find(r ->
                r.name.toLowerCase().contains(key)
                        || (r.localizedName != null
                        && r.localizedName.toLowerCase().contains(q.trim().toLowerCase())));
    }

    static RiskodMaps.RiskodSector findSector(String q) {
        if (q == null || q.isEmpty()) return null;
        String key = q.trim().toLowerCase()
                .replace(' ', '-')
                .replace('_', '-');

        Seq<RiskodMaps.RiskodSector> all = new Seq<>();
        all.addAll(RiskodMaps.easy);
        all.addAll(RiskodMaps.hard);
        if (RiskodMaps.launch != null) all.add(RiskodMaps.launch);
        if (RiskodMaps.moon != null) all.add(RiskodMaps.moon);
        for (SectorPreset s : Vars.content.sectors()) {
            if (s instanceof RiskodMaps.RiskodSector rs && !all.contains(rs)) all.add(rs);
        }
        RiskodMaps.RiskodSector exact = all.find(s ->
                s.name.equalsIgnoreCase(key) || s.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;

        exact = all.find(s ->
                s.localizedName != null && s.localizedName.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        RiskodMaps.RiskodSector hit = all.find(s -> s.name.toLowerCase().contains(key));
        if (hit != null) return hit;
        String qLow = q.trim().toLowerCase();
        return all.find(s ->
                s.localizedName != null && s.localizedName.toLowerCase().contains(qLow));
    }

    static Seq<UnitType> heroTypes() {
        return Vars.content.units().select(t -> t instanceof PlayerCharUnitType);
    }

    static UnitType findHero(String q) {
        if (q == null || q.isEmpty()) return null;
        String key = q.trim().toLowerCase().replace(' ', '-');
        Seq<UnitType> heroes = heroTypes();
        UnitType exact = heroes.find(t ->
                t.name.equalsIgnoreCase(key) || t.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        return heroes.find(t ->
                t.name.toLowerCase().contains(key)
                        || (t.localizedName != null
                        && t.localizedName.toLowerCase().contains(q.trim().toLowerCase())));
    }

    static UnitType findUnitType(String q) {
        if (q == null || q.isEmpty()) return null;
        String key = q.trim().toLowerCase().replace(' ', '-');
        Seq<UnitType> all = Vars.content.units();
        UnitType exact = all.find(t ->
                t.name.equalsIgnoreCase(key) || t.name.equalsIgnoreCase(q.trim()));
        if (exact != null) return exact;
        return all.find(t ->
                t.name.toLowerCase().contains(key)
                        || (t.localizedName != null
                        && t.localizedName.toLowerCase().contains(q.trim().toLowerCase())));
    }

    static HuntHeroAI.HuntStyle findHuntStyle(String q) {
        if (q == null || q.isEmpty()) return null;
        String key = q.trim().toLowerCase().replace(' ', '-').replace('_', '-');

        for (HuntHeroAI.HuntStyle s : HuntHeroAI.HuntStyle.values()) {
            if (s.name().equalsIgnoreCase(key) || s.name().equalsIgnoreCase(q.trim())) {
                return s;
            }
        }
        HuntHeroAI.HuntStyle prefix = null;
        int prefixHits = 0;
        for (HuntHeroAI.HuntStyle s : HuntHeroAI.HuntStyle.values()) {
            if (s.name().toLowerCase().startsWith(key)) {
                prefix = s;
                prefixHits++;
            }
        }
        if (prefixHits == 1) return prefix;

        HuntHeroAI.HuntStyle contains = null;
        int containsHits = 0;
        for (HuntHeroAI.HuntStyle s : HuntHeroAI.HuntStyle.values()) {
            if (s.name().toLowerCase().contains(key)) {
                contains = s;
                containsHits++;
            }
        }
        if (containsHits == 1) return contains;
        return null;
    }

    static String styleNames() {
        StringBuilder sb = new StringBuilder();
        for (HuntHeroAI.HuntStyle s : HuntHeroAI.HuntStyle.values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
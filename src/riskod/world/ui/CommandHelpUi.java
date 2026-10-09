package riskod.world.ui;

import arc.Core;
import arc.Events;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Gamemode;
import mindustry.ui.dialogs.BaseDialog;
import riskod.world.RiskodCommands;
import riskod.world.unit.HuntHeroAI;

import static mindustry.Vars.mobile;

/**
 * Command help window. Opens when a sandbox map is joined unless the setting is off.
 */
public class CommandHelpUi {
    public static final String SETTING = "riskod-sandbox-help";

    static boolean registered;
    static BaseDialog dialog;

    public static void register() {
        if (registered) return;
        registered = true;

        Events.on(EventType.WorldLoadEvent.class, e -> {
            if (Vars.headless) return;
            Time.run(45f, CommandHelpUi::maybeShow);
        });
    }

    static void maybeShow() {
        if (!Core.settings.getBool(SETTING, true) || mobile) return;
        if (Vars.state == null || !Vars.state.isGame() || Vars.state.rules.mode() != Gamemode.sandbox) return;
        show();
    }

    public static void show() {
        if (dialog != null && dialog.isShown()) return;

        Seq<String> lines = new Seq<>();

        // —— intro ——
        lines.add("[accent]How to use[]");
        lines.add("[lightgray]Enable the Console in settings then Open the console with [accent]F8[]. Type commands on the [accent]riskod[] object.[]");
        lines.add("[lightgray]Most commands only work on a [accent]sandbox[] map.[]");
        lines.add("");
        lines.add("[accent]Argument legend[]");
        lines.add("[lightgray]• [blue]?[] — optional (you can leave it out if it is at the end)[]");
        lines.add("[lightgray]• [red]#[] — a number (no quotes), e.g. [white]5[] or [white]12.5[][]");
        lines.add("[lightgray]• [white]\"name\"[] — text in quotes, e.g. [white]\"factotum\"[] or [white]\"agg\"[][]");
        lines.add("[lightgray]• Most names [accent]autofill[] — short prefixes are enough ([white]\"agg\"[] → aggressive).[]");
        lines.add("");
        lines.add("[gray]Turn this popup off: Settings -> Risk of Dustry.[]");
        lines.add("");

        // —— general ——
        section(lines, "General");
        cmd(lines,
                "riskod.help()",
                "Shows this window (same as joining sandbox when the setting is on).",
                null);

        // —— heroes ——
        section(lines, "Heroes & loadout");
        cmd(lines,
                "riskod.heroes()",
                "List hero unit ids you can switch to.",
                null);
        cmd(lines,
                "riskod.hero([white]\"[][green]heroName[][white]\"[])",
                "Become that hero unit.",
                "riskod.hero(\"factotum\")");
        cmd(lines,
                "riskod.pickHero()",
                "open hero select ui like in campagin to customize kits.",
                null);
        cmd(lines,
                "riskod.noCooldowns()",
                "Toggle: ability and gear charges stay full every frame.",
                null);
        cmd(lines,
                "riskod.loadout()",
                "Print your ability slots and gear.",
                "this command can be used in campaign");
        cmd(lines,
                "riskod.removeSlot([green]slot[][red]#[])",
                "Clear slot [white]0–3[] (abilities) or [white]4[] (gear).",
                "riskod.removeSlot(4)");
        cmd(lines,
                "riskod.clear()",
                "Remove all [accent]passive[] relics only (ability slots and gear are kept).",
                null);

        // —— runs / enemies ——
        section(lines, "Runs & enemies");
        cmd(lines,
                "riskod.mockRun([green]chests[][red]#[][blue]?[], [green]shrines[][red]#[][blue]?[], [white]\"[][green]boss[][blue]?[][white]\"[], [white]\"[][green]style[][blue]?[][white]\"[])",
                "Start a mock run on this map. Omitted chest/shrine counts use map defaults. Boss is the teleporter boss; style is its hunt AI ([white]\"none\"[] = vanilla AI).",
                "riskod.mockRun(6, 2, \"fortress\", \"agg\")");
        cmd(lines,
                "riskod.endMockRun()",
                "End the mock run and clean up what it added.",
                null);
        cmd(lines,
                "riskod.enemy([white]\"[][green]unit[][white]\"[], [green]count[][red]#[][blue]?[], [green]tileX[][red]#[][blue]?[], [green]tileY[][red]#[][blue]?[], [white]\"[][green]style[][blue]?[][white]\"[])",
                "Spawn wave-team enemies. Omit position to spawn near you. Style is a hunt style or [white]\"none\"[] for default AI.",
                "riskod.enemy(\"dagger\", 5)");
        cmd(lines,
                "riskod.killAll()",
                "Kill every enemy-team unit.",
                null);
        lines.add("[lightgray]Hunt styles:[] [gray]" + styleList() + "[]");
        lines.add("");

        // —— relics ——
        section(lines, "Relics");
        cmd(lines,
                "riskod.relics([white]\"[][green]filter[][blue]?[][white]\"[])",
                "List relic ids (optional name filter).",
                "riskod.relics(\"joker\")");
        cmd(lines,
                "riskod.passives()",
                "Show equipped passives and what they add.",
                "this command can be used in campaign");
        cmd(lines,
                "riskod.give([white]\"[][green]relic[][white]\"[])",
                "Add a relic straight to your loadout.",
                "riskod.give(\"speed-charm\")");
        cmd(lines,
                "riskod.relic([white]\"[][green]relic[][white]\"[], [green]tileX[][red]#[][blue]?[], [green]tileY[][red]#[][blue]?[])",
                "Spawn a relic pickup at you, or at a tile.",
                "riskod.relic(\"clover\")");
        cmd(lines,
                "riskod.relicAll([white]\"[][green]filter[][blue]?[][white]\"[])",
                "Spawn every matching relic you are allowed to use.",
                "riskod.relicAll(\"onyx\")");
        cmd(lines,
                "riskod.removePassive([white]\"[][green]relic[][white]\"[])",
                "Remove one of your passive relics.",
                "riskod.removePassive(\"speed-charm\")");
        cmd(lines,
                "riskod.uniques()",
                "List consumed unique relics (e.g. Mr. Bones).",
                null);
        cmd(lines,
                "riskod.resetUniques()",
                "Clear the consumed-unique list.",
                null);

        // —— misc ——
        section(lines, "Utilities");
        cmd(lines,
                "riskod.god()",
                "Toggle invulnerability.",
                null);
        cmd(lines,
                "riskod.tp([green]tileX[][red]#[], [green]tileY[][red]#[])",
                "Teleport to a tile.",
                "riskod.tp(50, 50)");

        // —— dev ——
        if (RiskodCommands.devUnlocked()) {
            section(lines, "Dev only");
            cmd(lines,
                    "riskod.unlockRelic([white]\"[][green]relic[][white]\"[])",
                    "Show this relic’s stats in the logbook.",
                    null);
            cmd(lines,
                    "riskod.lockRelic([white]\"[][green]relic[][white]\"[])",
                    "Hide this relic’s stats in the logbook.",
                    null);
            cmd(lines,
                    "riskod.unlockHero([white]\"[][green]hero[][white]\"[])",
                    "Show this hero in the logbook.",
                    null);
            cmd(lines,
                    "riskod.lockHero([white]\"[][green]hero[][white]\"[])",
                    "Hide this hero in the logbook.",
                    null);
            cmd(lines,
                    "riskod.unlockMap([white]\"[][green]sector[][white]\"[])",
                    "Show this map in the logbook.",
                    null);
            cmd(lines,
                    "riskod.lockMap([white]\"[][green]sector[][white]\"[])",
                    "Hide this map in the logbook.",
                    null);
        }

        dialog = InfoUi.show("Riskod commands", lines);
    }

    static void section(Seq<String> lines, String title) {
        lines.add("");
        lines.add("[accent]" + title + "[]");
        lines.add("");
    }

    static void cmd(Seq<String> lines, String usage, String description, String example) {
        lines.add("[accent]" + usage + "[]");
        lines.add("  [lightgray]" + description + "[]");
        if (example != null && !example.isEmpty()) {
            lines.add("  [gray]?: " + example + "[]");
        }
        lines.add("");
    }

    static String styleList() {
        StringBuilder sb = new StringBuilder();
        for (HuntHeroAI.HuntStyle s : HuntHeroAI.HuntStyle.values()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(s.name());
        }
        return sb.toString();
    }
}
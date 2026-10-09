package riskod.world.ui;

import arc.scene.ui.Dialog;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.gen.Icon;
import riskod.world.run.RunState;

import static riskod.world.RiskodMaps.exitRun;

public class RunStatsUi {
    public static void show(RunState run) {
        show(run, false);
    }

    public static void show(RunState run, boolean victory) {
        if (run == null || Vars.ui == null) return;


        Dialog d = new Dialog(victory ? "Run Complete" : "Run Over");
        d.cont.add(victory ? "The moon is yours." : "Hero fallen").padBottom(8f).row();
        d.cont.add("Time: " + formatTime(run.runTime)).left().row();
        d.cont.add("Stage: " + run.stage).left().row();
        d.cont.add("Level: " + run.level).left().row();
        d.cont.add("Kills: " + run.kills).left().row();
        d.cont.add("Bosses Killed: " + run.bossesKilled).left().row();
        d.cont.add("Damage: " + Strings.autoFixed(run.damageDealt, 0)).left().row();
        d.cont.add("Damage Received: " + Strings.autoFixed(run.damageReceived, 0)).left().row();
        d.cont.add("Healing Received: " + Strings.autoFixed(run.healingReceived, 0)).left().row();
        d.cont.add("Items Obtained: " + run.itemsObtained).left().row();
        d.cont.add("Relics found: " + run.logbook.size).left().row();
        d.cont.add("Relics Obtained: " + run.relicsObtained).left().row();
        d.buttons.button("Logbook", Icon.info, LogbookUi::show).size(120f, 50f);
        d.buttons.button("Return to menu", Icon.ok, () -> {
            d.hide();
            exitRun();
        }).size(120f, 50f);
        d.show();
    }

    static String formatTime(float ticks) {
        int sec = (int) (ticks / 60f);
        int m = sec / 60;
        int s = sec % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }
}
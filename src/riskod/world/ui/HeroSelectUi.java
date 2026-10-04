package riskod.world.ui;

import arc.func.Cons;
import arc.scene.ui.Dialog;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import riskod.world.RiskodMaps;
import riskod.world.run.HeroSpawner;
import riskod.world.RiskodPlanet;
import riskod.world.run.RunState;
import riskod.world.unit.PlayerCharUnitType;

/** Shown once at the start of a run. */
public class HeroSelectUi {
    public static boolean open;

    public static void show(Cons<UnitType> picked) {
        if (open) return;
        open = true;

        Dialog d = new Dialog("@riskod.hero-select");
        d.cont.add("@riskod.hero-select-sub").padBottom(12f).row();
        d.cont.pane(p -> {
            for (PlayerCharUnitType hero : PlayerCharUnitType.selectable) {
                p.table(Tex.pane, t -> {
                    t.left();
                    if (hero.uiIcon != null) t.image(hero.uiIcon).size(48f).padRight(8f);
                    t.add(hero.localizedName).growX().left();
                    t.button(Icon.play, Styles.cleari, () -> {
                        open = false;
                        d.hide();
                        picked.get(hero);
                    }).size(40f);
                }).growX().pad(4f).row();
            }
        }).grow().maxHeight(400f);

        d.buttons.defaults().size(140f, 44f);
        if (PlayerCharUnitType.selectable.isEmpty()) {
            d.buttons.button("Close", Icon.cancel, () -> {
                open = false;
                d.hide();
            });
        }
        d.closeOnBack();
        d.show();
    }

    public static void maybePrompt() {
        if (!RiskodPlanet.onRiskod()) return;
        if (RunState.active()) return;
        if (Vars.net.client()) return;
        if (PlayerCharUnitType.selectable.isEmpty()) return;

        show(hero -> {
            RunState.start(hero);
            RiskodMaps.playRunStart();
        });
    }
}

package riskod.world.ui;

import arc.func.Prov;
import arc.scene.Element;
import arc.scene.ui.Label;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.ui.Styles;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;

/** Hover tooltip showing a relic's glossary body and keyword boxes; shared by the ability HUD and the logbook. */
public class RelicTooltip {
    public static final float MAIN = 300f;
    public static final float KEY = 260f;

    public static void attach(Element target, Prov<RelicType> relic, Prov<Unit> unit, Prov<PlayerLoadout> loadout, int slot) {
        Label[] body = {null};
        Table[] keywords = {null};
        RelicType[] shown = {null};
        boolean[] built = {false};

        target.addListener(new Tooltip(t -> {
            t.background(Tex.clear);
            t.top().left();

            Table abilityBox = new Table(Tex.button);
            abilityBox.defaults().pad(12f);
            body[0] = new Label("", Styles.outlineLabel);
            body[0].setWrap(true);
            body[0].setAlignment(Align.left);
            abilityBox.add(body[0]).width(MAIN).left();
            t.add(abilityBox).width(MAIN + 24f).left().top().padBottom(8f).row();

            keywords[0] = new Table();
            keywords[0].top().left();
            t.add(keywords[0]).width(MAIN + 24f).left().top();
        }));

        target.update(() -> {
            if (body[0] == null || keywords[0] == null || !target.hasMouse()) return;

            RelicType r = relic.get();
            body[0].setText(AbilityGlossary.buildBody(r, unit.get(), loadout.get(), slot));

            if (built[0] && shown[0] == r) return;
            built[0] = true;
            shown[0] = r;

            keywords[0].clearChildren();
            for (String k : AbilityGlossary.keywordsFor(r)) {
                Table kb = new Table(Tex.button);
                kb.defaults().pad(8f);
                kb.left();
                Label title = new Label("[accent]" + capitalize(k) + "[]", Styles.outlineLabel);
                title.setFontScale(0.9f);
                Label text = new Label(AbilityGlossary.term(k), Styles.outlineLabel);
                text.setWrap(true);
                text.setFontScale(0.85f);
                kb.add(title).left().row();
                kb.add(text).width(KEY).wrap().left().padTop(3f);
                keywords[0].add(kb).width(KEY + 20f).left().padTop(5f).padLeft(12f).row();
            }
        });
    }

    static String capitalize(String s) {
        if (s == null || s.isEmpty()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
package riskod.world.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import riskod.world.relic.RelicType;

public class RelicPickupToast {
    private static Table current;
    private static float hideAt;

    public static void show(RelicType relic, String extra){
        if(relic == null) return;
        show(relic.localizedName, relic.description, relic.rarity, relic.icon, extra);
    }

    public static void show(String name, String description, int rarity, TextureRegion icon,  String extra) {
        if (Vars.ui == null) return;

        if (current != null) {
            current.remove();
            current = null;
        }

        Table t = new Table(Tex.buttonEdge3);
        t.margin(10f);

        if (icon != null) {
            t.image(icon).size(32f).padRight(8f);
        }

        t.table(col -> {
            col.left();
            col.add(name).style(Styles.outlineLabel).left().row();
            if (description != null && !description.isEmpty()) {
                col.add(description).color(Color.lightGray).wrap().width(220f).left();
            }
            col.row();
            if (extra != null && !extra.isEmpty()) {
                col.add(extra).color(Color.lightGray).wrap().width(220f).left();
            }
            col.row();
            if(rarity != -1)col.add("[accent]" + rarityLabel(rarity) + "[]")
                    .style(Styles.outlineLabel).left();
        }).left();

        t.pack();
        t.setPosition(
                Core.graphics.getWidth() / 2f - t.getWidth() / 2f,
                Core.graphics.getHeight() * 0.22f
        );
        t.actions(
                arc.scene.actions.Actions.fadeIn(0.15f),
                arc.scene.actions.Actions.delay(2.2f),
                arc.scene.actions.Actions.fadeOut(0.35f),
                arc.scene.actions.Actions.remove()
        );

        Vars.ui.hudGroup.addChild(t);
        current = t;
        hideAt = Time.time + 180f;
    }

    static String rarityLabel(int rarity) {
        return Core.bundle.get("relic.rarity." + Math.max(1, rarity), "Rarity " + rarity);
    }
}
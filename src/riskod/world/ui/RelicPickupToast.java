package riskod.world.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import riskod.world.relic.RelicType;

public class RelicPickupToast {

    public enum ToastChannel {
        MAIN(0.5f, 0.22f),
        ABILITY(0.75f, 0.2f),
        RELIC(0.74f, 0.12f),
        RELIC2(0.84f, 0.12f);

        public final float xRatio;
        public final float yRatio;

        private Table activeTable;

        ToastChannel(float xRatio, float yRatio) {
            this.xRatio = xRatio;
            this.yRatio = yRatio;
        }

        /** Cleans up the previous active toast in this channel if it exists. */
        public void clear() {
            if (activeTable != null) {
                activeTable.remove();
                activeTable = null;
            }
        }
    }

    private static float hideAt;

    /** Default helper shortcut for backwards compatibility — routes to MAIN channel. */
    public static void show(RelicType relic, String extra){
        if(relic == null) return;
        show(ToastChannel.MAIN, relic.localizedName, relic.description, relic.rarity, relic.icon, extra);
    }

    /** Overloaded helper to specify the channel directly for simple relic instances. */
    public static void show(ToastChannel channel, RelicType relic, String extra){
        if(relic == null) return;
        show(channel, relic.localizedName, relic.description, relic.rarity, relic.icon, extra);
    }

    public static void show(ToastChannel channel, String name, String description, int rarity, TextureRegion icon, String extra) {
        if (Vars.ui == null || channel == null) return;

        channel.clear();

        Table t = new Table(Tex.buttonEdge3);
        t.margin(10f);

        if (icon != null) {
            t.image(icon).scaling(Scaling.fit).padRight(8f);
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
            if (rarity != -1) {
                col.add("[accent]" + rarityLabel(rarity) + "[]").style(Styles.outlineLabel).left();
            }
        }).left();

        t.pack();
        t.setPosition(
                Core.graphics.getWidth() * channel.xRatio - t.getWidth() / 2f,
                Core.graphics.getHeight() * channel.yRatio
        );

        t.actions(
                arc.scene.actions.Actions.fadeIn(0.35f),
                arc.scene.actions.Actions.delay(2.5f),
                arc.scene.actions.Actions.fadeOut(0.35f),
                arc.scene.actions.Actions.remove()
        );

        Vars.ui.hudGroup.addChild(t);
        channel.activeTable = t;

        hideAt = Time.time + 180f;
    }

    static String rarityLabel(int rarity) {
        return Core.bundle.get("relic.rarity." + Math.max(1, rarity), "Rarity " + rarity);
    }
}

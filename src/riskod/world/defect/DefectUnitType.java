package riskod.world.defect;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Align;
import arc.util.Strings;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.unit.PlayerCharUnitType;

/** Defect hero — orbs drawn in-world; S/E/F live on AbilityBarHud. */
public class DefectUnitType extends PlayerCharUnitType {

    public DefectUnitType(String name) {
        super(name);
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);
        DefectState s = DefectState.get(unit);
        if (s == null) return;
        s.update(unit);

        PlayerLoadout l = loadout(unit);
        if (l != null) {
            DefectState.refreshCapacity(unit, l);
        }
    }

    @Override
    public void draw(Unit unit) {
        super.draw(unit);
        DefectState s = DefectState.get(unit);
        if (s == null) return;

        float z = Draw.z();
        Draw.z(Layer.effect + 1f);

        int n = s.orbs.size;
        if (n > 0) {
            float spacing = 12f;
            float total = (n - 1) * spacing;
            float start = -total / 2f;
            for (int i = 0; i < n; i++) {
                Orb orb = s.orbs.get(i);
                float ox = unit.x + start + i * spacing;
                float oy = unit.y + unit.hitSize / 2f + 14f + Mathf.sin(arc.util.Time.time + i * 20f, 20f, 2f);
                Color col = colorOf(orb.type);
                Draw.color(col);
                Fill.circle(ox, oy, 4.5f);
                Draw.color(Color.white, 0.5f);
                Lines.stroke(1.2f);
                Lines.circle(ox, oy, 5.8f);
                Draw.color(col, 0.3f);
                Fill.circle(ox, oy, 7f);

                if (orb.type == OrbType.dark && orb.value > 0f) {
                    Font font = Fonts.outline;
                    float prev = font.getData().scaleX;
                    font.getData().setScale(0.35f);
                    Draw.color(Color.valueOf("c9a0ff"));
                    font.draw(Strings.autoFixed(orb.value, 0), ox, oy + 8f, Align.center);
                    font.getData().setScale(prev);
                }
            }
        }

        Draw.reset();
        Draw.z(z);
    }

    static Color colorOf(OrbType type) {
        if (type == null) return Color.white;
        return switch (type) {
            case lightning -> Color.valueOf("f2e96b");
            case frost -> Color.valueOf("7fd7ff");
            case dark -> Color.valueOf("a56bff");
            case plasma -> Color.valueOf("ff7ad8");
        };
    }

    @Override
    public void killed(Unit unit) {
        DefectState.clear(unit);
        super.killed(unit);
    }

    public static void onSectorAdvance() {
        if (!RunState.active()) return;
        for (var e : DefectState.states) {
            e.value.onSectorAdvance();
        }
    }
}
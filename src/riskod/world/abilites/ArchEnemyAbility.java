package riskod.world.abilites;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import mindustry.entities.abilities.Ability;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;

public class ArchEnemyAbility extends Ability {
    /// Speed multiplier applied every frame; the engine resets speedMultiplier each tick.
    public float speedMul;

    /// Alpha of the red highlight drawn under the unit.
    public float glowAlpha = 0.22f;

    public ArchEnemyAbility(float speedMul) {
        this.speedMul = speedMul;
    }

    @Override
    public void update(Unit unit) {
        unit.speedMultiplier *= speedMul;
    }

    @Override
    public void draw(Unit unit) {
        float z = Draw.z();
        Draw.z(unit.isFlying() ? Layer.flyingUnit - 1f : Layer.groundUnit - 1f);
        Draw.color(Color.scarlet, glowAlpha);
        Fill.circle(unit.x, unit.y, unit.hitSize * 0.8f);
        Draw.reset();
        Draw.z(z);
    }
}
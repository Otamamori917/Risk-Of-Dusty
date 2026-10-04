package riskod.world.relic;

import mindustry.entities.bullet.BulletType;
import mindustry.gen.Unit;
import riskod.world.run.PlayerLoadout;

public class GearType extends RelicType {
    public int maxCharges = 1;
    public float cooldown = 60f;
    public int chargesOnReady = 1;

    public float healAmount = 0f;
    public BulletType shootBullet;
    public float shootSpeed = 0f;

    public GearType(String name) {
        super(name);
        slotKind = SlotKind.gear;
    }

    public void activate(Unit unit, PlayerLoadout loadout) {
        if (healAmount > 0f) {
            unit.heal(healAmount);
        }
        if (shootBullet != null) {
            float spd = shootSpeed > 0f ? shootSpeed : shootBullet.speed;
            float dmgMul = 1f;
            shootBullet.create(unit, unit.team, unit.x, unit.y, unit.rotation, spd, dmgMul, 1f, null);
        }
        onActivate(unit, loadout);
    }

    public void onActivate(Unit unit, PlayerLoadout loadout) {
    }
}

package riskod.world.abilites;

import arc.audio.Sound;
import arc.math.Mathf;
import arc.struct.IntMap;
import arc.struct.ObjectSet;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import riskod.world.run.PlayerLoadout;

/**
 * Optional windup, then {@link #burstCount} bullets toward aim.
 * windup &lt;= 0 → fire immediately.
 * Damage mul is applied to the root bullet and all nested frag/interval bullets.
 */
public class SlotShootAbility extends ChargedAbility {

    public int burstCount = 1;
    public float spread = 0f;
    public float inaccuracy = 0f;
    public float windup = 0f;
    public float windupDrag = 0.92f;
    public Effect windupEffect = Fx.none;
    public Sound windupSound = Sounds.none, shootSound = Sounds.shoot;
    public BulletType bullet;

    private static final IntMap<Float> windupLeft = new IntMap<>();
    private static final IntMap<Boolean> winding = new IntMap<>();

    public SlotShootAbility() {
        maxCharges = 2;
        cooldown = 90f;
        chargesOnReady = 1;
    }

    int windKey(Unit unit) {
        return stateKey(unit);
    }

    @Override
    public void update(Unit unit, PlayerLoadout loadout, int slot) {
        super.update(unit, loadout, slot);

        int key = windKey(unit);
        if (!winding.get(key, false)) return;

        float left = windupLeft.get(key, 0f) - Time.delta;
        if (left <= 0f) {
            winding.put(key, false);
            windupLeft.put(key, 0f);
            fire(unit, loadout, slot);
        } else {
            windupLeft.put(key, left);
            if (windupDrag < 1f) {
                unit.vel.scl(windupDrag);
            }
        }
    }

    @Override
    public boolean onActivate(Unit unit, PlayerLoadout loadout, int slot) {
        int key = windKey(unit);
        if (winding.get(key, false)) return false;
        windupEffect.at(unit, true);
        windupSound.at(unit);

        if (windup <= 0f) {
            fire(unit, loadout, slot);
            return true;
        }

        winding.put(key, true);
        windupLeft.put(key, windup);
        return true;
    }

    void fire(Unit unit, PlayerLoadout loadout, int slot) {
        if (bullet == null) return;

        float mul = damageMul(loadout, slot);
        BulletType type = scaleTree(bullet, mul, new ObjectSet<>());
        if (type == null) return;

        shootSound.at(unit);

        float base = unit.rotation;
        int n = Math.max(1, burstCount);

        for (int i = 0; i < n; i++) {
            float a = base + Mathf.range(inaccuracy);
            if (n > 1 && spread > 0f) {
                a += Mathf.lerp(-spread, spread, i / (n - 1f));
            }
            type.create(unit, unit.team, unit.x, unit.y, a, type.damage, 1f, 1f, null);
        }
    }

    /**
     * Deep-copies {@code src} and multiplies damage fields by {@code mul},
     * walking fragBullet and intervalBullet (nested frags/intervals included).
     */
    static BulletType scaleTree(BulletType src, float mul, ObjectSet<BulletType> seen) {
        if (src == null) return null;
        if (!seen.add(src)) return src.copy();

        BulletType t = src.copy();
        scaleFields(t, mul);

        if (src.fragBullet != null) {
            t.fragBullet = scaleTree(src.fragBullet, mul, seen);
        }
        if (src.intervalBullet != null) {
            t.intervalBullet = scaleTree(src.intervalBullet, mul, seen);
        }
        return t;
    }

    static void scaleFields(BulletType t, float mul) {
        if (mul == 1f) return;
        t.damage *= mul;
        t.splashDamage *= mul;
        t.lightningDamage *= mul;
        if (t.healAmount > 0f) t.healAmount *= mul;
    }
}
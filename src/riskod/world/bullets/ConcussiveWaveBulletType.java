package riskod.world.bullets;

import arc.graphics.Color;
import arc.graphics.gl.FrameBuffer;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import riskod.world.run.RunState;

/**
 * Cascading concussive shockwave — one entity, several timed pulses.
 * Each pulse hits in a forward cone and applies knockback.
 */
public class ConcussiveWaveBulletType extends BulletType {
    /** Number of wave pulses along the path. */
    public int waves = 4;
    /** Hits per pulse (shotgun count). Grows if null: 1,2,4,6… */
    public int[] hitsPerWave = {1, 2, 4, 6};
    /** Cone half-angle of the whole blast. */
    public float cone = 28f;
    /** Length of each shrapnel-like ray. */
    public float rayLength = 56f;
    /** Visual thickness of a ray. */
    public float rayWidth = 10f;
    /** Pitch shift per successive wave. */
    public float soundPitchStep = 0.06f;

    public Color waveColor = Color.valueOf("c8d0e0");
    public Color waveColorDark = Color.valueOf("4a5060");

    public ConcussiveWaveBulletType() {
        speed = 14f;
        lifetime = 18f;
        damage = 60f;
        collides = false;
        collidesTiles = false;
        collidesAir = false;
        collidesGround = false;
        hittable = false;
        reflectable = false;
        absorbable = false;
        hitEffect = Fx.none;
        despawnEffect = Fx.none;
        shootEffect = Fx.none;
        smokeEffect = Fx.none;
        keepVelocity = false;
        despawnSound = Sounds.none;
        hitSound = Sounds.none;
    }

    @Override
    public void init(Bullet b) {
        super.init(b);
        b.data = 0;
    }

    @Override
    public void update(Bullet b) {
        int wave = b.data instanceof Integer i ? i : 0;
        if (wave >= waves) return;

        float step = lifetime / (waves + 0.25f);
        float due = step * (wave + 1);

        if (b.time < due) return;

        pulse(b, wave);
        b.data = wave + 1;
    }

    void pulse(Bullet b, int wave) {
        int rays = hitsPerWave != null && wave < hitsPerWave.length
                ? Math.max(1, hitsPerWave[wave])
                : (1 << Math.min(wave, 3));

        float base = b.rotation();
        float dmg = damage * b.damageMultiplier();

        for (int i = 0; i < rays; i++) {
            float ang = base;
            if (rays > 1) {
                ang += Mathf.lerp(-cone, cone, i / (rays - 1f));
            } else {
                ang += Mathf.range(cone * 0.15f);
            }

            Tmp.v1.trns(ang, rayLength).add(b.x, b.y);

            float x1 = b.x, y1 = b.y, x2 = Tmp.v1.x, y2 = Tmp.v1.y;
            float finalAng = ang;
            Units.nearbyEnemies(b.team, b.x, b.y, rayLength + 16f, u -> {
                float dist = distToSegment(u.x, u.y, x1, y1, x2, y2);
                if (dist > rayWidth + u.hitSize / 2f) return;
                if (!Angles.within(base, b.angleTo(u), cone + 8f)) return;

                u.damage(dmg);
                if (RunState.active()) {
                    RunState.current.noteDamageDealt(dmg);
                }
                u.impulse(Tmp.v2.trns(finalAng, knockback * (1f + wave * 0.15f)));
            });
            if(b.owner instanceof Unit u){
                u.impulse(Tmp.v2.trns(b.rotation()-180, recoil));
            }

        }
        LensWarp.add(b.x, b.y, rayLength * 2f, 4f, 40f, b.rotation(), cone/2);
        float pitch = 1f + wave * soundPitchStep;
        Sounds.shootMissileLong.at(b.x, b.y, pitch, 0.7f + wave * 0.05f);
    }

    static float distToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float len2 = dx * dx + dy * dy;
        if (len2 < 0.0001f) return Mathf.dst(px, py, x1, y1);
        float t = Mathf.clamp(((px - x1) * dx + (py - y1) * dy) / len2);
        return Mathf.dst(px, py, x1 + dx * t, y1 + dy * t);
    }
}
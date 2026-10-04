package riskod.world.unit;

import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.entities.Units;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import riskod.world.relic.GearType;
import riskod.world.run.PlayerLoadout;

/**
 * Follower drone variants: attack, heal, or hold a gear relic and activate it.
 */
public class CompanionDroneType extends UnitType {
    public enum Role {
        attack, heal, gear
    }

    public Role role = Role.attack;
    public float followDistance = 28f;
    public float followSpeed = 3.2f;
    public float actionRange = 56f;
    public float actionCooldown = 45f;
    public float healAmount = 8f;

    public BulletType attackBullet = new BasicBulletType(3.5f, 8f) {{
        lifetime = 30f;
        width = 5f;
        height = 8f;
    }};

    public CompanionDroneType(String name) {
        super(name);
        flying = true;
        speed = 4f;
        accel = 0.12f;
        drag = 0.08f;
        hitSize = 8f;
        health = 80f;
        itemCapacity = 0;
        useUnitCap = false;
        playerControllable = false;
        isEnemy = false;
    }

    @Override
    public void update(Unit unit) {
        super.update(unit);

        Unit owner = findOwner(unit);
        Seq<Unit> allies = findAllies(unit);
        if (owner == null || !owner.isValid()) {
            unit.vel.scl(0.9f);
            return;
        }

        follow(unit, owner);
        tickAction(unit, owner, allies);
    }

    Unit findOwner(Unit unit) {
        if (mindustry.Vars.player != null) {
            Unit p = mindustry.Vars.player.unit();
            if (p != null && p.isValid() && p.team == unit.team) return p;
        }
        return Units.closest(unit.team, unit.x, unit.y, 200f * 8f,
                u -> u.type instanceof PlayerCharUnitType && u.isValid());
    }

    Seq<Unit> findAllies(Unit unit) {
        Seq<Unit> units = new Seq<>();

        Units.nearby(unit.team, unit.x, unit.y, 200f * 8f, u -> {
            if(u.type instanceof CompanionDroneType && u.isValid()){
             units.add(u);
            }
        });

        return units;
    }

    void follow(Unit unit, Unit owner) {
        float dx = owner.x - unit.x;
        float dy = owner.y - unit.y;
        float dist = Mathf.len(dx, dy);
        if (dist > followDistance) {
            float ang = Angles.angle(unit.x, unit.y, owner.x, owner.y);
            unit.vel.lerp(arc.util.Tmp.v1.trns(ang, followSpeed), 0.15f * Time.delta);
            unit.rotation = ang;
        } else {
            unit.vel.scl(0.92f);
        }
    }

    void tickAction(Unit unit, Unit owner,Seq<Unit> allies) {
        float cd = unit.elevation;
        ActionState s = ActionState.get(unit);
        s.timer += Time.delta;
        if (s.timer < actionCooldown) return;

        if (role == Role.heal) {
            if (owner.damaged()) {
                owner.heal(healAmount);
                s.timer = 0f;
            } else if (!allies.isEmpty()){
                for (Unit units : allies){
                    if (units.damaged()) {
                        units.heal(healAmount);
                        s.timer = 0f;
                        break;
                    }
                }
            }
            return;
        }

        if (role == Role.attack) {
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, actionRange, u -> true);
            if (target != null && attackBullet != null) {
                float ang = Angles.angle(unit.x, unit.y, target.x, target.y);
                attackBullet.create(unit, unit.team, unit.x, unit.y, ang);
                unit.rotation = ang;
                s.timer = 0f;
            }
            return;
        }

        if (role == Role.gear) {
            GearType gear = s.gear;
            if (gear == null) return;
            if (s.gearCharges <= 0) {
                s.gearCd += Time.delta;
                if (s.gearCd >= gear.cooldown) {
                    s.gearCd = 0f;
                    s.gearCharges = Math.min(gear.maxCharges, s.gearCharges + gear.chargesOnReady);
                }
                return;
            }
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, actionRange, u -> true);
            if (target != null || gear.healAmount > 0f) {
                if (target != null) {
                    unit.rotation = Angles.angle(unit.x, unit.y, target.x, target.y);
                }
                PlayerLoadout dummy = null;
                gear.activate(unit, dummy);
                s.gearCharges--;
                s.timer = 0f;
            }
        }
    }

    /** Assign a gear relic to a gear-role drone. */
    public static void giveGear(Unit drone, GearType gear) {
        if (drone == null || gear == null) return;
        ActionState s = ActionState.get(drone);
        s.gear = gear;
        s.gearCharges = gear.maxCharges;
        s.gearCd = 0f;
    }

    public static class ActionState {
        public static final arc.struct.IntMap<ActionState> map = new arc.struct.IntMap<>();
        public float timer;
        public GearType gear;
        public int gearCharges;
        public float gearCd;

        public static ActionState get(Unit u) {
            ActionState s = map.get(u.id);
            if (s == null) {
                s = new ActionState();
                map.put(u.id, s);
            }
            return s;
        }
    }
}

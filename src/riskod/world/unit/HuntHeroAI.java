package riskod.world.unit;

import arc.Core;
import arc.func.Boolf;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.math.geom.Position;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.ai.Astar;
import mindustry.core.World;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.world.Tile;
import riskod.world.block.Shrine;
import riskod.world.block.Teleporter;

/**
 * Hero-only AI with pathing and stance types.
 */
public class HuntHeroAI extends AIController {

    public enum HuntStyle {
        /** basic run up and shoot. */
        aggressive,
        /** shoots from a moderate distance try to stand in between the hero and allies */
        defensive,
        /** shoots from a High distance runs if hero is too close. */
        shy,
        /** attacks at an off angle */
        flanker,
        /** Group up attack together */
        swarm,
        /** Camp nearest shrine/teleporter; intercept if the hero approaches it. */
        guard,
        /** Stick to a hurt/nearby ally, still shoot the hero. */
        support,
        /** Ignore range, crash into the hero. */
        kamikaze
    }

    public static final ObjectMap<UnitType, HuntStyle> styles = new ObjectMap<>();

    /// Ground units farther than this many tiles from their destination skip A* and use the fallback movement.
    public static float pathMaxTiles = 100f;
    /// Ticks between movement decisions for units the player cannot see.
    public static float offscreenInterval = 8f;
    /// Extra world units around the camera that still count as visible.
    public static float screenMargin = Vars.tilesize * 6f;

    static Unit heroCache;
    static float heroStamp = -1f;

    static final Rect view = new Rect();
    static float viewStamp = -1f;

    /// Shrines and teleporters, rebuilt at most once a second and shared by every unit.
    static final Seq<Building> posts = new Seq<>();
    static float postStamp = -999f;

    public HuntStyle style = HuntStyle.aggressive;

    public Seq<Tile> path = new Seq<>();
    public int pathIndex;
    public float pathTimer;
    public int lastDest = -1;
    public float stuckTimer;
    public float lastX, lastY;

    /// Backoff after a failed A* search so unreachable destinations are not searched every frame.
    float failTimer;

    /// Cached result of the line-of-sight check.
    boolean rayCache;
    float rayTimer;

    /// Last issued move command, replayed on frames where an off-screen unit skips its decision.
    final Vec2 replay = new Vec2();
    float replayRadius;
    boolean hasMove;
    float lodTimer;

    /// Cached ally for swarm and support styles.
    Unit ally;
    float allyTimer;
    /// Cached ally for the defensive style.
    Unit defAlly;
    float defTimer;

    /// Cached shrine/teleporter for the guard style.
    Building postCache;
    float postTimer;

    /// Air/ground flags read by the preallocated target predicates.
    boolean tAir, tGround;

    final Astar.TileHeuristic pathCost = t -> 1f + (onDeep(t) ? 8f : 0f);
    final Boolf<Tile> pathPass = this::walkable;

    final Geometry.Raycaster rayCheck = (x, y) -> {
        Tile t = Vars.world.tile(x, y);
        return t != null && (t.solid() || onDeep(t));
    };

    final Boolf<Unit> allyAny = u -> u != unit && u.isValid();
    final Boolf<Unit> allySwarm = u -> u != unit && u.isValid() && u.controller() instanceof HuntHeroAI;
    final Boolf<Unit> allyHurt = u -> u != unit && u.isValid() && u.damaged();

    final Boolf<Unit> targetUnitPred = u -> u.checkTarget(tAir, tGround);
    final Boolf<Building> targetBuildPred = b -> tGround && !(b.block instanceof Shrine || b.block instanceof Teleporter);

    public static Unit hero(Unit from) {
        if (heroStamp == Time.time && (heroCache == null || heroCache.isValid())) return heroCache;
        heroStamp = Time.time;
        heroCache = null;
        if (mindustry.Vars.player != null) {
            Unit p = mindustry.Vars.player.unit();
            if (p != null && p.isValid() && p.type instanceof PlayerCharUnitType) heroCache = p;
        }
        if (heroCache == null) {
            heroCache = Units.closestEnemy(from.team, from.x, from.y, 8000f,
                    u -> u.isValid() && u.type instanceof PlayerCharUnitType);
        }
        return heroCache;
    }

    public static HuntStyle infer(UnitType type) {
        if (type == null) return HuntStyle.aggressive;
        HuntStyle set = styles.get(type);
        if (set != null) return set;
        float r = type.range;
        if (r >= 200f) return HuntStyle.shy;
        if (r >= 90f) return HuntStyle.defensive;
        return HuntStyle.aggressive;
    }

    public static void setStyle(UnitType type, HuntStyle style) {
        if (type != null && style != null) styles.put(type, style);
    }

    public static void apply(Unit unit) {
        if (unit == null || unit.controller() instanceof HuntHeroAI) return;
        HuntHeroAI ai = new HuntHeroAI();
        ai.style = infer(unit.type);
        unit.controller(ai);
    }

    static boolean onScreen(Unit u) {
        if (Vars.headless || Core.camera == null) return true;
        if (viewStamp != Time.time) {
            viewStamp = Time.time;
            Core.camera.bounds(view);
            view.grow(screenMargin);
        }
        return view.contains(u.x, u.y);
    }

    static void refreshPosts() {
        if (Time.time >= postStamp && Time.time - postStamp < 60f) return;
        postStamp = Time.time;
        posts.clear();
        for (Building b : Groups.build) {
            if (b == null || !b.isValid()) continue;
            if (b.block instanceof Shrine || b.block instanceof Teleporter) posts.add(b);
        }
    }

    @Override
    public Teamc findTarget(float x, float y, float range, boolean air, boolean ground) {
        Unit h = hero(unit);
        if (h != null && h.checkTarget(air, ground) && h.within(x, y, range + h.hitSize / 2f)) return h;
        tAir = air;
        tGround = ground;
        return Units.closestTarget(unit.team, x, y, range, targetUnitPred, targetBuildPred);
    }

    @Override
    public void updateMovement() {
        Unit h = hero(unit);
        if (h == null) return;

        unit.lookAt(h);
        tickStuck();

        lodTimer -= Time.delta;
        if (lodTimer > 0f) {
            if (hasMove) moveTo(replay, replayRadius);
            return;
        }
        lodTimer = onScreen(unit) ? 0f : offscreenInterval + unit.id % 4;
        hasMove = false;

        switch (style) {
            case defensive -> moveDefensive(h);
            case shy -> moveShy(h);
            case flanker -> moveFlanker(h);
            case swarm -> moveSwarm(h);
            case guard -> moveGuard(h);
            case support -> moveSupport(h);
            case kamikaze -> moveKamikaze(h);
            default -> moveAggressive(h);
        }
    }

    void go(Position p, float radius) {
        replay.set(p.getX(), p.getY());
        replayRadius = radius;
        hasMove = true;
        moveTo(p, radius);
    }

    void moveAggressive(Unit h) {
        float standoff = Math.max(8f, unit.type.range * 0.25f);
        moveSmart(h, standoff);
    }

    void moveDefensive(Unit h) {
        float ideal = Math.max(24f, unit.type.range * 0.7f);

        if (defAlly != null && !defAlly.isValid()) defAlly = null;
        defTimer -= Time.delta;
        if (defTimer <= 0f) {
            defTimer = 20f + unit.id % 8;
            defAlly = Units.closest(unit.team, h.x, h.y, 240f, allyAny);
        }

        if (defAlly != null) {
            Tmp.v2.set(h).lerp(defAlly, 0.4f);
            moveSmart(Tmp.v2, 10f);
            return;
        }
        keepRange(h, ideal * 0.75f, ideal * 1.1f);
    }

    void moveShy(Unit h) {
        float far = Math.max(40f, unit.type.range * 0.92f);
        float panic = Math.max(24f, unit.type.range * 0.45f);
        float dst = unit.dst(h);
        if (dst < panic) {
            Tmp.v1.set(unit.x - h.x, unit.y - h.y).setLength(unit.speed() * 3f);
            moveSmart(Tmp.v2.set(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y), 4f);
        } else {
            keepRange(h, far * 0.85f, far * 1.05f);
        }
    }

    void moveFlanker(Unit h) {
        float orbit = Math.max(28f, unit.type.range * 0.65f);
        float dir = (unit.id % 2 == 0) ? 90f : -90f;
        Tmp.v1.set(unit.x - h.x, unit.y - h.y);
        if (Tmp.v1.isZero()) Tmp.v1.trns(unit.rotation, 8f);
        Tmp.v1.rotate(dir).setLength(orbit);
        moveSmart(Tmp.v2.set(h.x + Tmp.v1.x, h.y + Tmp.v1.y), 10f);
    }

    void moveSwarm(Unit h) {
        if (ally != null && !ally.isValid()) ally = null;
        allyTimer -= Time.delta;
        if (allyTimer <= 0f) {
            allyTimer = 20f + unit.id % 8;
            ally = Units.closest(unit.team, unit.x, unit.y, 140f, allySwarm);
        }

        if (ally != null && !unit.within(ally, 36f)) {
            moveSmart(ally, 20f);
        } else {
            moveAggressive(h);
        }
    }

    void moveGuard(Unit h) {
        Building post = holdPost();
        if (post == null) {
            moveDefensive(h);
            return;
        }
        if (h.within(post, Math.max(80f, unit.type.range + 48f))) {
            Tmp.v2.set(post).lerp(h, 0.4f);
            moveSmart(Tmp.v2, 8f);
        } else {
            moveSmart(post, 14f);
        }
    }

    Building holdPost() {
        if (postCache != null && !postCache.isValid()) {
            postCache = null;
            postTimer = 0f;
        }
        postTimer -= Time.delta;
        if (postTimer > 0f) return postCache;
        postTimer = 30f + unit.id % 10;

        refreshPosts();
        Building best = null;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < posts.size; i++) {
            Building b = posts.get(i);
            if (!b.isValid()) continue;
            float d = unit.dst2(b);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        postCache = best;
        return postCache;
    }

    void moveSupport(Unit h) {
        if (ally != null && !ally.isValid()) ally = null;
        allyTimer -= Time.delta;
        if (allyTimer <= 0f) {
            allyTimer = 20f + unit.id % 8;
            Unit hurt = Units.closest(unit.team, unit.x, unit.y, 220f, allyHurt);
            ally = hurt != null ? hurt : Units.closest(unit.team, unit.x, unit.y, 220f, allyAny);
        }

        if (ally == null) {
            moveDefensive(h);
            return;
        }
        moveSmart(ally, 18f);
    }

    void moveKamikaze(Unit h) {
        stuckTimer += 8f;
        moveSmart(h, Math.max(0f, unit.hitSize * 0.15f));
    }

    void keepRange(Unit h, float min, float max) {
        float dst = unit.dst(h);
        if (dst < min) {
            Tmp.v1.set(unit.x - h.x, unit.y - h.y).setLength(min);
            moveSmart(Tmp.v2.set(h.x + Tmp.v1.x, h.y + Tmp.v1.y), 6f);
        } else if (dst > max) {
            moveSmart(h, max);
        } else {
            strafe(h);
        }
    }

    void strafe(Unit h) {
        float rot = (unit.id % 2 == 0 ? 1 : -1) * 70f;
        Tmp.v1.trns(Angles.angle(h.x, h.y, unit.x, unit.y) + rot, unit.speed());
        moveSmart(Tmp.v2.set(unit.x + Tmp.v1.x, unit.y + Tmp.v1.y), 2f);
    }

    void tickStuck() {
        if (Mathf.dst(unit.x, unit.y, lastX, lastY) < 1.2f * Time.delta) {
            stuckTimer += Time.delta;
        } else {
            stuckTimer = 0f;
        }
        lastX = unit.x;
        lastY = unit.y;
    }

    void moveSmart(Position dest, float radius) {
        if (dest == null) return;
        if (unit.within(dest, radius)) {
            setBoost(false);
            return;
        }

        boolean flying = unit.type.flying || unit.isFlying();
        boolean canBoost = unit.type.canBoost && !unit.type.flying;

        if (flying) {
            setBoost(false);
            go(dest, radius);
            return;
        }

        boolean blocked = rayBlocked(unit.x, unit.y, dest.getX(), dest.getY());
        boolean deep = onDeep(unit.tileOn());

        if (canBoost && (blocked || deep || stuckTimer > 40f)) {
            setBoost(true);
            go(dest, radius);
            return;
        }

        if (!blocked && !deep && !unit.type.naval && stuckTimer <= 40f) {
            setBoost(false);
            go(dest, radius);
            return;
        }

        setBoost(false);
        if (!followPath(dest, radius)) {
            if (canBoost) {
                setBoost(true);
                go(dest, radius);
            } else {
                stepGreedy(dest);
            }
        }
    }

    boolean followPath(Position dest, float radius) {
        if (failTimer > 0f) {
            failTimer -= Time.delta;
            return false;
        }

        Tile start = unit.tileOn();
        Tile end = Vars.world.tileWorld(dest.getX(), dest.getY());
        if (start == null || end == null) return false;
        if (!unit.within(dest, pathMaxTiles * Vars.tilesize)) return false;

        int destPos = end.pos();
        boolean moved = lastDest == -1
                || Math.abs(end.x - Point2.x(lastDest)) + Math.abs(end.y - Point2.y(lastDest)) > 6;

        pathTimer -= Time.delta;
        if (pathTimer <= 0f || moved) {
            float tiles = unit.dst(dest) / Vars.tilesize;
            pathTimer = Mathf.clamp(18f + tiles * 0.6f, 18f, 90f) + unit.id % 6;
            lastDest = destPos;
            pathIndex = 0;
            try {
                path.set(Astar.pathfind(start, end, pathCost, pathPass));
            } catch (Throwable ignored) {
                path.clear();
            }
            if (path.size < 2) {
                failTimer = 45f + unit.id % 15;
                return false;
            }
        }

        if (path.size < 2) return false;

        while (pathIndex < path.size && unit.within(path.get(pathIndex), 10f)) {
            pathIndex++;
        }
        if (pathIndex >= path.size) {
            go(dest, radius);
            return true;
        }
        Tile next = path.get(pathIndex);
        go(next, 0f);
        return true;
    }

    void stepGreedy(Position dest) {
        Tile cur = unit.tileOn();
        if (cur == null) {
            go(dest, 0f);
            return;
        }
        Tile best = null;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Tile n = cur.nearby(Geometry.d8[i].x, Geometry.d8[i].y);
            if (n == null || !walkable(n)) continue;
            float d = Mathf.dst(n.worldx(), n.worldy(), dest.getX(), dest.getY());
            if (d < bestD) {
                bestD = d;
                best = n;
            }
        }
        if (best != null) go(best, 0f);
        else go(dest, 0f);
    }

    boolean walkable(Tile t) {
        if (t == null || t.solid()) return false;
        if (unit.type.naval) return t.floor() != null && t.floor().isLiquid;
        return !onDeep(t);
    }

    boolean onDeep(Tile t) {
        return t != null && t.floor() != null && t.floor().isDeep();
    }

    boolean rayBlocked(float x1, float y1, float x2, float y2) {
        rayTimer -= Time.delta;
        if (rayTimer <= 0f) {
            rayTimer = 10f + unit.id % 5;
            rayCache = World.raycast(
                    World.toTile(x1), World.toTile(y1),
                    World.toTile(x2), World.toTile(y2),
                    rayCheck
            );
        }
        return rayCache;
    }

    void setBoost(boolean on) {
        if (unit.type.canBoost) unit.updateBoosting(on);
    }
}
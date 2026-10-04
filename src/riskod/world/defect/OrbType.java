package riskod.world.defect;

import mindustry.entities.Units;
import mindustry.gen.Unit;
import riskod.world.run.RunState;

public enum OrbType {
    lightning {
        public static final int flatdamage = 80;
        public static final float evokedMult = 0.8f;

        @Override
        public int[] callValues() {
            return new int[]{flatdamage, (int) (flatdamage*evokedMult)};
        }

        @Override
        public void onChannel(Unit unit, DefectState s, Orb orb) {
            orb.value = flatdamage;
        }

        @Override
        public void passive(Unit unit, DefectState s, Orb orb) {
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, DefectState.ORB_RANGE, u -> true);
            if (target != null) {
                float dmg = orb.value + s.totalFocus();
                if (RunState.active()) {
                    RunState.current.noteDamageDealt(dmg);
                }
                target.damage(Math.max(0,dmg));
            }
        }

        @Override
        public void evoke(Unit unit, DefectState s, Orb orb) {
            Units.nearbyEnemies(unit.team, unit.x, unit.y, DefectState.ORB_RANGE, u -> {
                float dmg = (orb.value*evokedMult) + s.totalFocus();
                if (RunState.active()) {
                    RunState.current.noteDamageDealt(dmg);
                }
                u.damage(Math.max(0,dmg));
            });
        }
    },

    frost {
        public static final int flatsheilding = 14;
        public static final float evokedMult = 1.8f;

        @Override
        public int[] callValues() {
            return new int[]{flatsheilding, (int) (flatsheilding*evokedMult)};
        }


        @Override
        public void onChannel(Unit unit, DefectState s, Orb orb) {
            orb.value = flatsheilding;
        }

        @Override
        public void passive(Unit unit, DefectState s, Orb orb) {
            s.addShield(unit, Math.max(0,orb.value + s.totalFocus()));
        }

        @Override
        public void evoke(Unit unit, DefectState s, Orb orb) {
            s.addShield(unit, Math.max(0,(orb.value * evokedMult) + s.totalFocus()));
        }
    },

    dark {
        public static final int flatdamage = 10;
        public static final int flatincrementdamage = 6;

        @Override
        public int[] callValues() {
            return new int[]{flatdamage,flatincrementdamage};
        }

        @Override
        public void onChannel(Unit unit, DefectState s, Orb orb) {
            orb.value = flatdamage;
        }

        @Override
        public void passive(Unit unit, DefectState s, Orb orb) {
            orb.value += Math.max(0,flatincrementdamage + s.totalFocus());
        }

        @Override
        public void evoke(Unit unit, DefectState s, Orb orb) {
            Unit target = null;
            float best = Float.MAX_VALUE;
            for (Unit u : mindustry.gen.Groups.unit) {
                if (u.team == unit.team || !u.isValid() || u.dead) continue;
                if (!u.within(unit, DefectState.ORB_RANGE * 1.5f)) continue;
                if (u.health < best) {
                    best = u.health;
                    target = u;
                }
            }
            if (target != null) {
                if (RunState.active()) {
                    RunState.current.noteDamageDealt(orb.value);
                }
                target.damage(orb.value);
            }
        }
    },

    plasma {
        public static final int flatenergy = 1;

        @Override
        public int[] callValues() {
            return new int[]{flatenergy};
        }

        @Override
        public void onChannel(Unit unit, DefectState s, Orb orb) {
            orb.value = flatenergy;
        }

        @Override
        public void passive(Unit unit, DefectState s, Orb orb) {
            s.addEnergy(orb.value, false);
        }

        @Override
        public void evoke(Unit unit, DefectState s, Orb orb) {
            s.addEnergy(Math.max(0,orb.value + s.totalFocus()), true);
        }
    };

    public abstract void onChannel(Unit unit, DefectState s, Orb orb);
    public abstract void passive(Unit unit, DefectState s, Orb orb);
    public abstract void evoke(Unit unit, DefectState s, Orb orb);
    public abstract int[] callValues();
}
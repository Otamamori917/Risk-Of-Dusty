package riskod.world.ui;

import arc.struct.ObjectMap;
import arc.util.Strings;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Unit;
import riskod.world.abilites.BulletReflectAbility;
import riskod.world.abilites.ChargedAbility;
import riskod.world.abilites.DashAbility;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.abilites.SlotShootAbility;
import riskod.world.defect.DefectAbilities;
import riskod.world.defect.DefectAbility;
import riskod.world.defect.DefectState;
import riskod.world.defect.OrbType;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;

public class AbilityGlossary {
    public static final ObjectMap<String, String> TERMS = new ObjectMap<>();

    static {
        TERMS.put("channel", "Add an orb to storage. If storage is full, the oldest orb is evoked.");
        TERMS.put("evoke", "Trigger the oldest orb's evoke effect, then remove it.");
        TERMS.put("energy", "Spent to activate skills. Refills to your max each pulse.");
        TERMS.put("focus", "Each point of Focus adds +1 to effects that scale with it.");
        TERMS.put("Lightning Orb", "Passive: deal [stat]"+ OrbType.lightning.callValues()[0] +"[] damage to a random enemy in range. \nEvoke: deal [stat]"+ OrbType.lightning.callValues()[1] +"[] damage to all enemies in range. \nDamage scales with Focus.");
        TERMS.put("Frost Orb", "Passive: gain [stat]"+ OrbType.frost.callValues()[0] +"[] shielding (scales with Focus). \nEvoke: gain [stat]"+ OrbType.frost.callValues()[1] +"[] shielding (scales with Focus).");
        TERMS.put("Dark Orb", "Channel sets base damage to [stat]"+ OrbType.dark.callValues()[0] +"[]. \nPassive: increases stored damage by [stat]"+ OrbType.dark.callValues()[1] +"[] (scales with Focus). \nEvoke: deal stored damage to the lowest-HP enemy in range.");
        TERMS.put("Plasma Orb", "Passive: [stat]+"+ OrbType.plasma.callValues()[0] +"[] Energy this pulse cycle (doesn't scale with Focus). \nEvoke: bank [stat]+"+ OrbType.plasma.callValues()[0] +"[] Energy for a few pulses (scales with Focus).");
        TERMS.put("buffer", "Prevent the next unblocked damage you would take. Consumes 1 stack on that hit.");
        TERMS.put("echo form", "The next ability or gear you use activates twice. The second activation does not spend Energy.");
        TERMS.put("discharge", "While active, taking unblocked damage [stat]Channels 1 Lightning[] (short cooldown between channels).");
        TERMS.put("Counter", "Activating this ability frame perfect grants [stat]Refresh[].");
        TERMS.put("Refresh", "ALL abilities and gear gets all activation charges and cooldowns finished \n(even if not currently equipped).");
    }

    public static String term(String key) {
        return TERMS.get(key, key);
    }

    public static String focusLine(String label, float base, DefectState s) {
        int f = s == null ? 0 : s.totalFocus();
        return label + " (" + Strings.autoFixed(base, 0) + " + focus) [" + Strings.autoFixed(base + f, 0) + "]";
    }

    public static String buildBody(RelicType relic, Unit unit, PlayerLoadout loadout, int slot) {
        if (relic == null) return "Empty slot";
        StringBuilder sb = new StringBuilder();
        sb.append(relic.localizedName);
        if (relic.description != null && !relic.description.isEmpty()) {
            sb.append("\n").append(relic.description);
        }

        if (relic.ability instanceof DefectAbility d) {
            DefectState st = unit != null ? DefectState.get(unit) : null;
            sb.append("\n\n").append(defectBody(d, st));
            if (d.energyCost > 0f) {
                sb.append("\nCost: -").append(Strings.autoFixed(d.energyCost, 0)).append(" Energy");
            }
        } else if (relic.ability instanceof KitSwapAbility) {
            sb.append("\n\nCycle the active kit.");
        } else if (relic.ability instanceof ChargedAbility c) {
            String extra = chargedBody(c, loadout, slot);
            if (extra != null) sb.append("\n\n").append(extra);
            sb.append("\nCharges: ").append(c.maxCharges);
            sb.append("\nCooldown: ").append(Strings.autoFixed(c.cooldown / 60f, 1)).append("s");
        } else if (relic instanceof GearType g) {
            sb.append("\nGear charges: ").append(g.maxCharges);
            sb.append("\nCooldown: ").append(Strings.autoFixed(g.cooldown / 60f, 1)).append("s");
            if (g.healAmount > 0f) sb.append("\nHeal: ").append(Strings.autoFixed(g.healAmount, 0));
        }
        return sb.toString();
    }

    public static String chargedBody(ChargedAbility c, PlayerLoadout loadout, int slot) {
        if (c instanceof DashAbility d) return dashBody(d, loadout);
        if (c instanceof BulletReflectAbility b) return clearBody(b, loadout, slot);
        if (c instanceof SlotShootAbility s) return shootBody(s, loadout, slot);
        return null;
    }

    static String n1(float v) {
        return Strings.autoFixed(v, 1);
    }

    static String dashBody(DashAbility d, PlayerLoadout l) {
        float spd = d.dashSpeed * (l != null ? Math.max(0.25f, l.speedMul) : 1f);
        return "Dash in the direction you are aiming.\nDash speed: [stat]" + n1(spd) + "[]";
    }

    static String clearBody(BulletReflectAbility b, PlayerLoadout l, int slot) {
        float blocks = b.radius * b.rangeMul(l, slot) / 8f;
        return "Reflect all enemy bullets within [stat]" + n1(blocks) + "[] blocks. \n[stat]Counter[]";
    }

    static String shootBody(SlotShootAbility s, PlayerLoadout l, int slot) {
        StringBuilder sb = new StringBuilder();
        int shots = Math.max(1, s.burstCount);
        sb.append("Fires [stat]").append(shots).append("[] bullet").append(shots > 1 ? "s" : "");
        if (s.windup > 0f) sb.append(" after a [stat]").append(n1(s.windup / 60f)).append("[]s windup");
        sb.append(".");
        if (shots > 1 && s.spread > 0f) sb.append("\nSpread: [stat]+/-").append(n1(s.spread)).append("[] degrees");
        if (s.inaccuracy > 0f) sb.append("\nInaccuracy: [stat]+/-").append(n1(s.inaccuracy)).append("[] degrees");

        BulletType b = s.bullet;
        if (b == null) return sb.toString();
        bulletLines(sb, b, b.damage * s.damageMul(l, slot), "");
        return sb.toString();
    }

    static void bulletLines(StringBuilder sb, BulletType b, float direct, String pre) {
        if (direct > 0f) {
            sb.append("\n").append(pre).append("Damage: [stat]").append(n1(direct)).append("[]");
        }
        if (b.splashDamage > 0f) {
            sb.append("\n").append(pre).append("Splash damage: [stat]").append(n1(b.splashDamage)).append("[]");
            if (b.splashDamageRadius > 0f) {
                sb.append(" in [stat]").append(n1(b.splashDamageRadius / 8f)).append("[] blocks");
            }
        }
        if (b.lightning > 0) {
            float ld = b.lightningDamage < 0f ? b.damage : b.lightningDamage;
            sb.append("\n").append(pre).append("Lightning: [stat]").append(b.lightning).append("[] bolts of [stat]")
                    .append(n1(ld)).append("[] damage");
        }
        if (b.pierce) {
            sb.append("\n").append(pre).append(b.pierceCap > 0
                    ? "Pierces up to [stat]" + b.pierceCap + "[] targets"
                    : "Pierces enemies");
        }
        if (b.status != null && b.status != StatusEffects.none) {
            sb.append("\n").append(pre).append("Applies [stat]").append(b.status.localizedName)
                    .append("[] for [stat]").append(n1(b.statusDuration / 60f)).append("[]s");
        }

        if (!pre.isEmpty()) return;

        if (b.fragBullet != null && b.fragBullets > 0) {
            sb.append("\nFrag bullets: [stat]").append(b.fragBullets).append("[]");
            bulletLines(sb, b.fragBullet, b.fragBullet.damage, "  ");
        }
        if (b.intervalBullet != null && b.intervalBullets > 0) {
            sb.append("\nEvery [stat]").append(n1(b.bulletInterval / 60f)).append("[]s: [stat]")
                    .append(b.intervalBullets).append("[] bullets");
            bulletLines(sb, b.intervalBullet, b.intervalBullet.damage, "  ");
        }
    }

    static String defectBody(DefectAbility d, DefectState s) {
        if (d instanceof DefectAbilities.Zap) return "[stat]Channel 1 Lightning[].";
        if (d instanceof DefectAbilities.StaticDischarge sd)
            return "gain the [stat]Discharge[] enhancement for [stat]"+(int)sd.duration/60+"[] seconds.";
        if (d instanceof DefectAbilities.ThunderStrike ts)
            return "Fire a shockwave in-front of you hitting all enemies within a cone of [stat]"+(int)ts.cone+"[] degrees dealing [stat]"+(int)ts.basePerChannel+"[] damage per lighting you've channeled this sector \n(currently [stat]"+(int)ts.value+"[] damage) \nrange of [stat]"+(int)ts.range/8+"[] blocks";
        if (d instanceof DefectAbilities.ColdSnap cs)
            return "Fire a shot that deals [stat]"+(int)cs.value+"[] damage (equal to your current shield) hitting all enemies within a cone of [stat]"+(int)cs.cone+"[]degrees\nrange of [stat]"+(int)cs.range/8+"[] blocks \n[stat]Channel 1 Frost[].";
        if (d instanceof DefectAbilities.Glacier gl)
            return "Gain [stat]"+(int)gl.shieldBase+"[] shielding. \n[stat]Channel 2 Frost[].\n[stat]Counter[].";
        if (d instanceof DefectAbilities.Buffer bf) return "Grants [stat]"+bf.stacksPerUse+"[] stack"+(bf.stacksPerUse > 1 ? "s" : "")+" of [stat]Buffer[] enhancement \nbut you lose [scarlet]"+bf.shieldCost+"[] shielding";
        if (d instanceof DefectAbilities.Darkness)
            return "[stat]Channel 1 Dark[]. \nTrigger the passive of all Dark orbs [stat]1[] time.";
        if (d instanceof DefectAbilities.DoomAndGloom dg)
            return "Slash in front of you dealing [stat]"+(int)dg.value+"[] damage to all enemies within a cone of [stat]"+(int)dg.cone+"[] degrees \nrange of [stat]"+(int)dg.range/8+"[] blocks.. \n[stat]Channel 2 Dark.[]";
        if (d instanceof DefectAbilities.Sunder sd)
            return "Deal [stat]"+(int)sd.value+"[] damage to the closest enemy within a range of [stat]"+(int)sd.range/8+"[] blocks. \nIf it dies, trigger Dark passives [stat]"+ sd.triggerAmount +"[] times.";
        if (d instanceof DefectAbilities.Fusion) return "[stat]Channel 1 Plasma[].";
        if (d instanceof DefectAbilities.Fission fi)
            return "[scarlet]Remove all orbs[],gain [stat]Refresh[]. Gain [stat]1[] banked Energy and [stat]1[] Focus per orb removed.\n"+(fi.value > 0 ? "(You will get [stat]"+(int)fi.value+"[] energy and focus" : "you cannot use this without orbs");
        if (d instanceof DefectAbilities.MeteorStrike ms)
            return "Deal [stat]"+(int)ms.value+"[] damage to the closest enemy within a range of [stat]"+(int)ms.range/8+"[] blocks. \n[stat]Channel 3 Plasma[]." ;
        if (d instanceof DefectAbilities.HyperBeam hy)
            return "Deal [stat]"+(int)hy.value+"[] damage in front of you to all enemies within a cone of [stat]"+(int)hy.cone+"[] degrees \nrange of [stat]"+(int)hy.range/8+"[] blocks \nbut you lose [scarlet]"+ hy.focusCost +"[] focus";
        if (d instanceof DefectAbilities.Multicast mc)
            return "Spend all Energy as X. and evokes (X+1) times on the same orb (can pay 0) \n"+(s == null ? "" : !s.orbs.isEmpty() ? "(you will evoke a [stat]"+s.orbs.get(0).type.name()+" orb "+(int)mc.value+ "[] times on use)" : "you have no orbs to evoke");
        if (d instanceof DefectAbilities.EchoForm) return "Grants the [stat]Echo Form[] enhancement.";
        return "Defect skill.";
    }

    public static String[] keywordsFor(RelicType relic) {
        if (relic == null || !(relic.ability instanceof ChargedAbility d)) return new String[]{};
        if (d instanceof DefectAbilities.Zap) return new String[]{"channel", "Lightning Orb"};
        if (d instanceof DefectAbilities.StaticDischarge) return new String[]{"discharge", "channel", "Lightning Orb", "energy"};
        if (d instanceof DefectAbilities.ThunderStrike) return new String[]{"Lightning Orb", "energy"};
        if (d instanceof DefectAbilities.ColdSnap) return new String[]{"channel", "Frost Orb", "energy"};
        if (d instanceof DefectAbilities.Glacier) return new String[]{"channel", "Frost Orb", "energy", "Counter", "Refresh"};
        if (d instanceof DefectAbilities.Buffer) return new String[]{"buffer", "energy"};
        if (d instanceof DefectAbilities.Darkness) return new String[]{"channel", "Dark Orb", "energy"};
        if (d instanceof DefectAbilities.DoomAndGloom) return new String[]{"channel", "Dark Orb", "energy"};
        if (d instanceof DefectAbilities.Sunder) return new String[]{"Dark Orb", "energy"};
        if (d instanceof DefectAbilities.Fusion) return new String[]{"channel", "Plasma Orb", "energy"};
        if (d instanceof DefectAbilities.Fission) return new String[]{"energy", "focus", "Refresh"};
        if (d instanceof DefectAbilities.MeteorStrike) return new String[]{"channel", "Plasma Orb", "energy"};
        if (d instanceof DefectAbilities.HyperBeam) return new String[]{"energy", "focus"};
        if (d instanceof DefectAbilities.Multicast) return new String[]{"evoke", "energy"};
        if (d instanceof DefectAbilities.EchoForm) return new String[]{"echo form", "energy"};
        if (d instanceof BulletReflectAbility) return new String[]{"Counter", "Refresh"};
        return new String[]{};
    }
}
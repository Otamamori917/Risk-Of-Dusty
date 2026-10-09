package riskod.world.defect;

import mindustry.type.UnitType;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.meta.UnlockReq;
import riskod.world.relic.RelicType;

import static riskod.world.relic.RelicType.SlotKind;

public class DefectKits {
    public static RelicType lightningZap, lightningStatic, lightningThunder;
    public static RelicType frostSnap, frostGlacier, frostBuffer;
    public static RelicType darkDarkness, darkDoom, darkSunder;
    public static RelicType plasmaFusion, plasmaFission, plasmaMeteor;
    public static RelicType hyperBeam, evokeMulti, evokeEcho;

    public static RelicType voidSiphon, voidRift, voidCollapse;
    public static RelicType kitSwap;

    public static final RelicType[] ALL = new RelicType[40];

    public static void load() {
        lightningZap = abilityRelic("defect-zap", RelicType.ApplyType.primary, new DefectAbilities.Zap());
        lightningStatic = abilityRelic("defect-static", RelicType.ApplyType.secondary, new DefectAbilities.StaticDischarge());
        lightningThunder = abilityRelic("defect-thunder", RelicType.ApplyType.utility, new DefectAbilities.ThunderStrike());

        frostSnap = abilityRelic("defect-cold-snap", RelicType.ApplyType.primary, new DefectAbilities.ColdSnap());
        frostGlacier = abilityRelic("defect-glacier", RelicType.ApplyType.secondary, new DefectAbilities.Glacier());
        frostBuffer = abilityRelic("defect-buffer", RelicType.ApplyType.utility, new DefectAbilities.Buffer());

        darkDarkness = abilityRelic("defect-darkness", RelicType.ApplyType.primary, new DefectAbilities.Darkness());
        darkDoom = abilityRelic("defect-doom", RelicType.ApplyType.secondary, new DefectAbilities.DoomAndGloom());
        darkSunder = abilityRelic("defect-sunder", RelicType.ApplyType.utility, new DefectAbilities.Sunder());

        plasmaFusion = abilityRelic("defect-fusion", RelicType.ApplyType.primary, new DefectAbilities.Fusion());
        plasmaFission = abilityRelic("defect-fission", RelicType.ApplyType.secondary, new DefectAbilities.Fission());
        plasmaMeteor = abilityRelic("defect-meteor", RelicType.ApplyType.utility, new DefectAbilities.MeteorStrike());

        evokeMulti = abilityRelic("defect-multicast", RelicType.ApplyType.primary, new DefectAbilities.Multicast());
        hyperBeam = abilityRelic("defect-hyperbeam", RelicType.ApplyType.secondary, new DefectAbilities.HyperBeam());
        evokeEcho = abilityRelic("defect-echo", RelicType.ApplyType.utility, new DefectAbilities.EchoForm());

        voidSiphon = abilityRelic("defect-void-siphon", RelicType.ApplyType.primary, new DefectAbilities.Darkness());
        voidRift = abilityRelic("defect-void-rift", RelicType.ApplyType.secondary, new DefectAbilities.DoomAndGloom());
        voidCollapse = abilityRelic("defect-void-collapse", RelicType.ApplyType.utility, new DefectAbilities.Sunder());

        KitSwapAbility.Kits lightning = new KitSwapAbility.Kits(
                "Lightning", "Abilities based on generating lightning and dealing area damage",
                lightningZap, lightningStatic, lightningThunder);
        KitSwapAbility.Kits frost = new KitSwapAbility.Kits(
                "Frost", "Abilities based on defence and generating it",
                frostSnap, frostGlacier, frostBuffer);
        KitSwapAbility.Kits dark = new KitSwapAbility.Kits(
                "Dark", "Abilities based on building up High one shot damage",
                darkDarkness, darkDoom, darkSunder);
        KitSwapAbility.Kits plasma = new KitSwapAbility.Kits(
                "Plasma", "Abilities based on generating excess Energy and Focus \nfor use as burst damage or with other kits",
                plasmaFusion, plasmaFission, plasmaMeteor);
        KitSwapAbility.Kits evoke = new KitSwapAbility.Kits(
                "Evoke", "Abilities based on the usage of other kits maximizing their effect",
                hyperBeam, evokeMulti, evokeEcho);

        KitSwapAbility.Kits voidKit = new KitSwapAbility.Kits(
                "Void", "Spend focus for delete-button damage. Swap it in by dropping another kit.",
                voidSiphon, voidRift, voidCollapse);
        voidKit.unlock = UnlockReq.relicFound("Superconductor");

        KitSwapAbility swap = new KitSwapAbility(lightning, frost, dark, plasma, evoke, voidKit);
        swap.baseKitCount = 5;

        kitSwap = new RelicType("defect-kit-swap") {{
            slotKind = SlotKind.ability;
            equipApplyto = ApplyType.special;
            ability = swap;
        }};

        int i = 0;
        ALL[i++] = lightningZap; ALL[i++] = lightningStatic; ALL[i++] = lightningThunder;
        ALL[i++] = frostSnap; ALL[i++] = frostGlacier; ALL[i++] = frostBuffer;
        ALL[i++] = darkDarkness; ALL[i++] = darkDoom; ALL[i++] = darkSunder;
        ALL[i++] = plasmaFusion; ALL[i++] = plasmaFission; ALL[i++] = plasmaMeteor;
        ALL[i++] = hyperBeam; ALL[i++] = evokeMulti; ALL[i++] = evokeEcho;
        ALL[i++] = voidSiphon; ALL[i++] = voidRift; ALL[i++] = voidCollapse;
        ALL[i++] = kitSwap;
    }

    public static void bindHero(UnitType defect) {
        if (defect == null) return;
        for (RelicType r : ALL) {
            if (r != null) r.forHero(defect);
        }
    }

    static RelicType abilityRelic(String name, RelicType.ApplyType equipSlo, mindustry.entities.abilities.Ability abilit) {
        return new RelicType(name) {{
            this.slotKind = SlotKind.ability;
            this.equipApplyto = equipSlo;
            this.ability = abilit;
        }};
    }
}
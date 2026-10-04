package riskod.world.defect;

import mindustry.type.UnitType;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.relic.RelicType;

import static riskod.world.relic.RelicType.SlotKind;

public class DefectKits {
    public static RelicType lightningZap, lightningStatic, lightningThunder;
    public static RelicType frostSnap, frostGlacier, frostBuffer;
    public static RelicType darkDarkness, darkDoom, darkSunder;
    public static RelicType plasmaFusion, plasmaFission, plasmaMeteor;
    public static RelicType hyperBeam, evokeMulti, evokeEcho;
    public static RelicType kitSwap;

    public static final RelicType[] ALL = new RelicType[16];

    public static void load() {
        lightningZap = abilityRelic("defect-zap", 0, new DefectAbilities.Zap());
        lightningStatic = abilityRelic("defect-static", 1, new DefectAbilities.StaticDischarge());
        lightningThunder = abilityRelic("defect-thunder", 2, new DefectAbilities.ThunderStrike());

        frostSnap = abilityRelic("defect-cold-snap", 0, new DefectAbilities.ColdSnap());
        frostGlacier = abilityRelic("defect-glacier", 1, new DefectAbilities.Glacier());
        frostBuffer = abilityRelic("defect-buffer", 2, new DefectAbilities.Buffer());

        darkDarkness = abilityRelic("defect-darkness", 0, new DefectAbilities.Darkness());
        darkDoom = abilityRelic("defect-doom", 1, new DefectAbilities.DoomAndGloom());
        darkSunder = abilityRelic("defect-sunder", 2, new DefectAbilities.Sunder());

        plasmaFusion = abilityRelic("defect-fusion", 0, new DefectAbilities.Fusion());
        plasmaFission = abilityRelic("defect-fission", 1, new DefectAbilities.Fission());
        plasmaMeteor = abilityRelic("defect-meteor", 2, new DefectAbilities.MeteorStrike());

        evokeMulti = abilityRelic("defect-multicast", 0, new DefectAbilities.Multicast());
        hyperBeam = abilityRelic("defect-hyperbeam", 1, new DefectAbilities.HyperBeam());
        evokeEcho = abilityRelic("defect-echo", 2, new DefectAbilities.EchoForm());

        kitSwap = new RelicType("defect-kit-swap") {{
            slotKind = SlotKind.ability;
            equipSlot = KitSwapAbility.KIT_SLOT;
            ability = new KitSwapAbility(
                    new KitSwapAbility.Kits("Lightning", "Zap / Static / Thunder",
                            lightningZap, lightningStatic, lightningThunder),
                    new KitSwapAbility.Kits("Frost", "Cold Snap / Glacier / Buffer",
                            frostSnap, frostGlacier, frostBuffer),
                    new KitSwapAbility.Kits("Dark", "Darkness / Doom / Sunder",
                            darkDarkness, darkDoom, darkSunder),
                    new KitSwapAbility.Kits("Plasma", "Fusion / Fission / Meteor",
                            plasmaFusion, plasmaFission, plasmaMeteor),
                    new KitSwapAbility.Kits("Evoke", "Dualcast / Multicast / Echo Form",
                            hyperBeam, evokeMulti, evokeEcho)
            );
        }};

        int i = 0;
        ALL[i++] = lightningZap; ALL[i++] = lightningStatic; ALL[i++] = lightningThunder;
        ALL[i++] = frostSnap; ALL[i++] = frostGlacier; ALL[i++] = frostBuffer;
        ALL[i++] = darkDarkness; ALL[i++] = darkDoom; ALL[i++] = darkSunder;
        ALL[i++] = plasmaFusion; ALL[i++] = plasmaFission; ALL[i++] = plasmaMeteor;
        ALL[i++] = hyperBeam; ALL[i++] = evokeMulti; ALL[i++] = evokeEcho;
        ALL[i++] = kitSwap;
    }

    public static void bindHero(UnitType defect) {
        if (defect == null) return;
        for (RelicType r : ALL) {
            if (r != null) r.forHero(defect);
        }
    }

    static RelicType abilityRelic(String name, int equipSlo, mindustry.entities.abilities.Ability abilit) {
        return new RelicType(name) {{
            this.slotKind = SlotKind.ability;
            this.equipSlot = equipSlo;
            this.ability = abilit;
        }};
    }
}
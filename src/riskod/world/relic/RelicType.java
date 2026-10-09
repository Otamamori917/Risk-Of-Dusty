package riskod.world.relic;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.type.UnitType;
import riskod.world.meta.Meta;
import riskod.world.meta.UnlockReq;
import riskod.world.run.RunState;

public class RelicType {
    public static final Seq<RelicType> all = new Seq<>();

    public final String name;
    public String localizedName;
    public String description = "";
    public boolean outline = true;
    public TextureRegion icon;
    public int rarity = 1;

    public SlotKind slotKind = SlotKind.passive;
    public ApplyType equipApplyto = ApplyType.primary;
    public ApplyType bonusApplyTo = ApplyType.ALL;
    public Ability ability;

    public float healthMul = 1f;
    public float speedMul = 1f;
    public int bonusGearCharges;
    public float gearCooldownMul = 1f;
    public float bonusLuck;
    public float luckMul = 1f;
    public int bonusAbilityCharges;
    public float abilityCooldownMul = 1f;
    public float abilityDamageMul = 1f;
    public float abilityRangeMul = 1f;

    public int bonusFocus = 0;
    public int bonusOrbCapacity = 0;
    public float bonusEnergyCap = 0f;
    public float pulseIntervalMul = 1f;
    public int grantFocus = 0;
    public boolean plasmaBankPermanent = false;

    public boolean unstackable = false;
    public boolean smeared;
    public boolean stuntMan;
    public boolean obelisk;
    public boolean negativeObelisk;
    public boolean mrBones;

    public UnlockReq unlock = UnlockReq.none();

    public UnitType requiredHero;
    public final Seq<UnitType> onlyHeroes = new Seq<>();

    public RelicType(String name) {
        this.name = name;
        this.localizedName = name;
        all.add(this);
    }

    public boolean unlocked() {
        if (Meta.isForcedUnlocked("relic", name)) return true;
        return unlock == null || unlock.met();
    }

    public RelicType validate() {
        if (equipApplyto == ApplyType.ALL || equipApplyto == ApplyType.allMain || equipApplyto == ApplyType.gear) {
            throw new IllegalArgumentException("Relic '" + name + "': equipApplyTo cannot be " + equipApplyto.name());
        }
        return this;
    }

    public RelicType forHero(UnitType hero) {
        if (hero != null) requiredHero = hero;
        return this;
    }

    public RelicType forHeroes(UnitType... heroes) {
        onlyHeroes.clear();
        if (heroes != null) {
            for (UnitType h : heroes) {
                if (h != null) onlyHeroes.add(h);
            }
        }
        return this;
    }

    public boolean canDropFor(UnitType hero) {
        if (!unlocked()) return false;
        if (requiredHero == null && onlyHeroes.isEmpty()) return true;
        if (hero == null) return false;
        if (requiredHero != null && !sameHero(requiredHero, hero)) return false;
        if (onlyHeroes.any()) {
            for (UnitType h : onlyHeroes) {
                if (sameHero(h, hero)) return true;
            }
            return false;
        }
        return true;
    }

    private static boolean sameHero(UnitType a, UnitType b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        return a.name != null && a.name.equals(b.name);
    }

    public boolean canDropForCurrentRun() {
        if (RunState.consumedUniques.contains(name)) return false;
        UnitType hero = RunState.current != null ? RunState.current.heroType
                : (Vars.player != null && Vars.player.unit() != null ? Vars.player.unit().type : null);
        return canDropFor(hero);
    }

    public enum SlotKind {
        passive, ability, weapon, gear
    }

    public enum ApplyType {
        primary, secondary, utility, special, gear, allMain, ALL
    }

    public void loadIcon() {
        icon = Core.atlas.find("riskod-" + name, Core.atlas.find("riskod-sprite"));
    }
}
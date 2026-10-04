package riskod.world.relic;

import arc.graphics.g2d.TextureRegion;
import arc.struct.Seq;
import mindustry.entities.abilities.Ability;
import mindustry.type.UnitType;
import riskod.world.run.RunState;

public class RelicType {
    public static final Seq<RelicType> all = new Seq<>();

    public String name;
    public String localizedName;
    public String description = "";
    public TextureRegion icon;

    public int rarity = 1;
    public SlotKind slotKind = SlotKind.passive;
    public int equipSlot = 0;
    public int bonusSlot = -1;
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
    public float abilityReloadMul = 1f;

    public UnitType requiredHero;
    public final Seq<UnitType> onlyHeroes = new Seq<>();

    public int bonusFocus = 0;
    public int bonusOrbCapacity = 0;
    public float bonusEnergyCap = 0f;
    public float pulseIntervalMul = 1f;
    public int grantFocus = 0;
    public boolean plasmaBankPermanent = false;

    public RelicType(String name) {
        this.name = name;
        this.localizedName = name;
        all.add(this);
    }

    /** No-op if hero is null — prevents early load from unlocking by accident. */
    public RelicType forHero(UnitType hero) {
        if (hero == null) return this;
        requiredHero = hero;
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
        if (requiredHero == null && onlyHeroes.isEmpty()) return true;
        if (hero == null) return false;
        if (requiredHero != null && !sameHero(requiredHero, hero)) return false;
        if (onlyHeroes.any()) {
            boolean ok = false;
            for (UnitType h : onlyHeroes) {
                if (sameHero(h, hero)) { ok = true; break; }
            }
            if (!ok) return false;
        }
        return true;
    }

    static boolean sameHero(UnitType a, UnitType b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        return a.name != null && a.name.equals(b.name);
    }

    public boolean canDropForCurrentRun() {
        UnitType hero = RunState.current != null ? RunState.current.heroType : null;
        return canDropFor(hero);
    }

    public enum SlotKind {
        passive, ability, weapon, gear
    }
}
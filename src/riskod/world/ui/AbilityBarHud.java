package riskod.world.ui;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Reflect;
import arc.util.Strings;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.gen.Unit;
import mindustry.ui.Styles;
import riskod.RiskOfDustryLoader;
import riskod.world.abilites.ChargedAbility;
import riskod.world.defect.DefectAbility;
import riskod.world.defect.DefectState;
import riskod.world.defect.DefectUnitType;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.run.PlayerLoadout;
import riskod.world.unit.PlayerCharUnitType;

import static mindustry.Vars.*;

public class AbilityBarHud {
    static Table root;
    static Table slotsRow;
    static Table resourceRow;
    static final SlotUi[] slots = new SlotUi[PlayerLoadout.SLOT_COUNT];
    static Label shieldLab, energyLab, focusLab;
    static Label echoLab, bufLab, disLab;
    static boolean built;

    static final float BOX = 112f;
    static final float ICON = 72f;
    static final float PIP = 9f;
    static final float SLOT_PAD = 18f;
    static final float TIP_KEY = 260f;

    /// Scale applied to the four flex/kit slots.
    static final float SLOT_SCALE = 0.75f;

    /// Scale applied to the gear slot.
    static final float GEAR_SCALE = 0.9f;

    /// Gap between the bar and the screen edge.
    static final float EDGE_PAD = 4f;

    public static void register() {
        Events.on(EventType.ClientLoadEvent.class, e -> Core.app.post(AbilityBarHud::build));
        Events.run(EventType.Trigger.update, AbilityBarHud::update);
    }

    static float scaleFor(int slot) {
        return slot == PlayerLoadout.GEAR_SLOT ? GEAR_SCALE : SLOT_SCALE;
    }

    static Unit heroUnit() {
        return player != null ? player.unit() : null;
    }

    static PlayerLoadout heroLoadout() {
        Unit u = heroUnit();
        return u != null && u.type instanceof PlayerCharUnitType ? PlayerCharUnitType.loadout(u) : null;
    }

    static void place() {
        root.pack();
        root.setPosition(
                Core.graphics.getWidth() - Core.scene.marginRight - root.getWidth() - EDGE_PAD,
                Core.scene.marginBottom + EDGE_PAD
        );
    }

    public static void build() {
        if (built || ui == null || ui.hudGroup == null) return;
        built = true;

        root = new Table();
        root.touchable = Touchable.childrenOnly;
        root.visible(() -> shouldShow());

        resourceRow = new Table();
        resourceRow.left();

        shieldLab = new Label("", Styles.outlineLabel);
        energyLab = new Label("", Styles.outlineLabel);
        focusLab = new Label("", Styles.outlineLabel);

        energyLab.touchable = Touchable.enabled;
        focusLab.touchable = Touchable.enabled;
        attachTermTip(energyLab, "energy");
        attachTermTip(focusLab, "focus");

        echoLab = makeFlagLab("Echo Form", "echo form", Color.valueOf("ff9be0"));
        bufLab = makeFlagLab("Buffer", "buffer", Color.valueOf("9bffc8"));
        disLab = makeFlagLab("Discharge", "discharge", Color.valueOf("ffe08a"));
        echoLab.visible = false;
        bufLab.visible = false;
        disLab.visible = false;

        resourceRow.add(shieldLab).padRight(14f);
        resourceRow.add(energyLab).padRight(14f);
        resourceRow.add(focusLab).padRight(14f);
        resourceRow.add(echoLab).padRight(10f);
        resourceRow.add(bufLab).padRight(10f);
        resourceRow.add(disLab).padRight(10f);

        slotsRow = new Table();
        for (int i = 0; i < PlayerLoadout.SLOT_COUNT; i++) {
            slots[i] = new SlotUi(i);
            slotsRow.add(slots[i].root).pad(SLOT_PAD * scaleFor(i)).bottom();
        }

        root.add(resourceRow).right().padBottom(10f).row();
        root.add(slotsRow).right().row();

        ui.hudGroup.addChild(root);
        place();
        root.toFront();
    }

    static Label makeFlagLab(String text, String termKey, Color color) {
        Label lab = new Label(text, Styles.outlineLabel);
        lab.setColor(color);
        lab.touchable = Touchable.enabled;
        attachTermTip(lab, termKey);
        return lab;
    }

    static boolean shouldShow() {
        if (player == null || player.unit() == null || ui == null || ui.hudfrag == null || !ui.hudfrag.shown) {
            return false;
        }
        return player.unit().type instanceof PlayerCharUnitType;
    }

    static void update() {
        if (!built) build();
        if (root == null) return;

        boolean hero = shouldShow();
        if (!hero) return;

        place();
        root.toFront();

        Unit unit = player.unit();
        PlayerLoadout loadout = PlayerCharUnitType.loadout(unit);
        if (loadout == null) return;

        boolean defect = unit.type instanceof DefectUnitType;
        DefectState st = defect ? DefectState.get(unit) : null;

        if (defect && st != null) {
            resourceRow.visible = true;
            float sh = Math.max(0f, unit.shield);
            shieldLab.setText("[#" + (sh > 0.5f ? "7fd7ff" : "888888") + "]Shielding " + n(sh) + "[]");

            if (st.plasmaBank > 0.01f) {
                energyLab.setText("[#9be7ff]Energy " + n(st.energy)
                        + " (" + n(st.energyCap)
                        + "+" + n(st.plasmaBank) + ")[]");
            } else {
                energyLab.setText("[#9be7ff]Energy " + n(st.energy) + "/" + n(st.energyCap) + "[]");
            }

            int f = st.totalFocus();
            String fColor = f > 0 ? "f2e96b" : (f < 0 ? "ff6666" : "888888");
            focusLab.setText("[#" + fColor + "]Focus " + f + "[]");

            echoLab.visible = st.echoForm;
            bufLab.visible = st.bufferStacks > 0;
            if (st.bufferStacks > 0) {
                bufLab.setText(st.bufferStacks > 1 ? "Buffer x" + st.bufferStacks : "Buffer");
            } else {
                bufLab.setText("Buffer");
            }
            if (st.staticDischargeTime > 0f) {
                disLab.visible = true;
                disLab.setText("Discharge " + n(st.staticDischargeTime / 60f) + "s");
            } else {
                disLab.visible = false;
                disLab.setText("Discharge");
            }
        } else {
            resourceRow.visible = false;
        }

        for (SlotUi s : slots) s.refresh(unit, loadout, st);
    }

    static String n(float v) {
        if (Math.abs(v - Math.round(v)) < 0.001f) return String.valueOf(Math.round(v));
        return Strings.autoFixed(v, 1);
    }

    static void attachTermTip(Label lab, String termKey) {
        lab.addListener(new Tooltip(t -> {
            t.background(Tex.clear);
            Table box = new Table(Tex.button);
            box.defaults().pad(8f);
            Label body = new Label(AbilityGlossary.term(termKey), Styles.outlineLabel);
            body.setWrap(true);
            box.add(body).width(TIP_KEY).left();
            t.add(box);
        }));
    }

    static KeyCode bindFor(int slot) {
        return switch (slot) {
            case 0 -> RiskOfDustryLoader.bindSlot0;
            case 1 -> RiskOfDustryLoader.bindSlot1;
            case 2 -> RiskOfDustryLoader.bindSlot2;
            case 3 -> RiskOfDustryLoader.bindSlot3;
            case 4 -> RiskOfDustryLoader.bindSlot4;
            default -> KeyCode.unknown;
        };
    }

    static class SlotUi {
        final int slot;
        final float scale;
        final Table root, box, pips;
        final Image icon;
        final Label cdLab, costLab, keyLab;

        SlotUi(int slot) {
            this.slot = slot;
            this.scale = scaleFor(slot);
            root = new Table();
            pips = new Table();
            box = new Table(Tex.button);
            box.touchable = Touchable.enabled;

            icon = new Image(Icon.units);
            cdLab = new Label("", Styles.outlineLabel);
            cdLab.setAlignment(Align.center);
            cdLab.setFontScale(scale);
            costLab = new Label("", Styles.outlineLabel);
            costLab.setFontScale(scale);
            keyLab = new Label("", Styles.outlineLabel);
            keyLab.setAlignment(Align.center);
            keyLab.setFontScale(scale);

            float boxSize = BOX * scale;

            root.add(pips).height(14f * scale).growX().row();
            box.stack(
                    new Table(t -> t.add(icon).size(ICON * scale)),
                    new Table(t -> t.add(cdLab).grow()),
                    new Table(t -> {
                        t.top().left();
                        t.add(costLab).pad(6f * scale);
                    })
            ).size(boxSize);
            root.add(box).size(boxSize).row();
            root.add(keyLab).width(boxSize + 8f).padTop(6f * scale);

            box.clicked(() -> {
                if (player == null || player.unit() == null) return;
                if (!(player.unit().type instanceof PlayerCharUnitType)) return;
                PlayerLoadout l = PlayerCharUnitType.loadout(player.unit());
                if (l != null) l.tryActivateSlot(player.unit(), slot);
            });

            RelicTooltip.attach(box, () -> {
                PlayerLoadout l = heroLoadout();
                return l != null ? l.slots[slot] : null;
            }, AbilityBarHud::heroUnit, AbilityBarHud::heroLoadout, slot);
        }

        void refresh(Unit unit, PlayerLoadout loadout, DefectState st) {
            RelicType r = loadout.slots[slot];
            keyLab.setText(bindFor(slot).toString());

            if (r == null) {
                icon.setDrawable(Icon.units);
                icon.setColor(Color.darkGray);
                cdLab.setText("");
                costLab.setText("");
                pips.clearChildren();
                return;
            }

            if (r.icon != null) icon.setDrawable(r.icon);
            else icon.setDrawable(Icon.units);

            int charges = 0, max = 0;
            float cdLeft = 0f;
            float energyCost = 0f;

            if (slot == PlayerLoadout.GEAR_SLOT && r instanceof GearType g) {
                charges = loadout.gearCharges;
                max = loadout.effectiveGearMax();
                if (charges < max) {
                    cdLeft = Math.max(0f, loadout.effectiveGearCooldown() - loadout.gearCooldownTimer);
                }
            } else if (r.ability instanceof ChargedAbility c) {
                ChargedAbility.ChargeState cs = c.state(unit);
                max = c.effectiveMax(loadout, slot);
                charges = cs.inited ? cs.charges : max;
                if (charges < max) {
                    cdLeft = Math.max(0f, c.effectiveCooldown(loadout, slot) - cs.timer);
                }
                if (c instanceof DefectAbility d) energyCost = d.energyCost;
            }

            pips.clearChildren();
            int show = Math.min(Math.max(max, 0), 10);
            for (int i = 0; i < show; i++) {
                Image pip = new Image(Tex.whiteui);
                pip.setColor(i < charges ? Color.valueOf("9be7ff") : Color.darkGray);
                pips.add(pip).size(PIP * scale).pad(2f * scale);
            }

            boolean onCd = cdLeft > 0.5f && charges < max;
            icon.setColor(onCd || charges <= 0 ? Color.gray : Color.white);
            cdLab.setText(onCd ? n(cdLeft / 60f) + "s" : "");

            if (energyCost > 0.01f) {
                boolean can = st == null || st.energy >= energyCost;
                costLab.setText((can ? "[#ffb0b0]" : "[#ff5555]") + "-" + n(energyCost) + "[]");
            } else {
                costLab.setText("");
            }
        }
    }
}
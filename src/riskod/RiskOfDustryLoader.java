package riskod;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Log;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.SettingsMenuDialog;
import mindustry.ui.fragments.MenuFragment;
import mindustry.ui.fragments.PlacementFragment;
import riskod.content.RiskodCont;
import riskod.world.bullets.LensWarp;
import riskod.world.ui.LogbookUi;
import riskod.world.RiskodMaps;
import riskod.world.RiskodPlanet;
import riskod.world.RunHooks;
import riskod.world.unit.PlayerCharUnitType;
import template.gen.EntityRegistry;

import static mindustry.Vars.ui;

public class RiskOfDustryLoader extends Mod {

    // Do NOT use 1-9 — control groups eat them.
    public static KeyCode bindSlot0 = KeyCode.z;
    public static KeyCode bindSlot1 = KeyCode.x;
    public static KeyCode bindSlot2 = KeyCode.c;
    public static KeyCode bindSlot3 = KeyCode.v;
    public static KeyCode bindSlot4 = KeyCode.b;

    public static boolean rebinding;
    public static int rebindSlot = -1;

    /// last placement toggler given the hero-aware visibility; rebuild() replaces it, which triggers a re-apply
    static Table hookedToggler;

    public static KeyCode slotKey(int slot) {
        return switch (slot) {
            case 0 -> bindSlot0;
            case 1 -> bindSlot1;
            case 2 -> bindSlot2;
            case 3 -> bindSlot3;
            case 4 -> bindSlot4;
            default -> KeyCode.unknown;
        };
    }

    public static void setSlotKey(int slot, KeyCode k) {
        switch (slot) {
            case 0 -> bindSlot0 = k;
            case 1 -> bindSlot1 = k;
            case 2 -> bindSlot2 = k;
            case 3 -> bindSlot3 = k;
            case 4 -> bindSlot4 = k;
            default -> {
            }
        }
        Core.settings.put("riskod-bind-slot" + slot, k.name());
    }

    public static boolean slotTapped(int slot) {
        if (rebinding) return false;
        if (Vars.ui != null) {
            try {
                if (Vars.ui.chatfrag != null && Vars.ui.chatfrag.shown()) return false;
            } catch (Throwable ignored) {}
        }
        KeyCode k = slotKey(slot);
        return k != null && k != KeyCode.unknown && Core.input.keyTap(k);
    }

    public static void startRebind(int slot) {
        rebinding = true;
        rebindSlot = slot;
        Vars.ui.showInfoToast(Core.bundle.get("setting.riskod-rebind-press"), 2f);
    }

    static boolean heroActive() {
        return Vars.player != null && Vars.player.unit() != null && Vars.player.unit().type instanceof PlayerCharUnitType;
    }

    static void hookPlacementVisibility() {
        if (ui == null || ui.hudfrag == null || ui.hudfrag.blockfrag == null) return;

        Table toggler = Reflect.get(PlacementFragment.class, ui.hudfrag.blockfrag, "toggler");
        if (toggler == null || toggler == hookedToggler) return;

        toggler.visible(() -> ui.hudfrag.shown() && !heroActive());
        hookedToggler = toggler;
    }

    void loadKeybinds() {
        String[] defs = {
                KeyCode.z.name(),
                KeyCode.x.name(),
                KeyCode.c.name(),
                KeyCode.v.name(),
                KeyCode.b.name()
        };
        for (int i = 0; i < 5; i++) {
            try {
                setSlotKey(i, KeyCode.valueOf(Core.settings.getString("riskod-bind-slot" + i, defs[i])));
            } catch (Throwable ignored) {
                setSlotKey(i, KeyCode.valueOf(defs[i]));
            }
        }
    }

    void pollRebind() {
        if (!rebinding) return;
        for (KeyCode k : KeyCode.all) {
            if (Core.input.keyTap(k)) {
                if (k == KeyCode.escape) {
                    rebinding = false;
                    rebindSlot = -1;
                    return;
                }
                setSlotKey(rebindSlot, k);
                rebinding = false;
                rebindSlot = -1;
                Vars.ui.showInfoToast(k.toString(), 1.5f);
                return;
            }
        }
    }

    @Override
    public void init() {
        super.init();
        if (!Vars.headless) LensWarp.register();
        RunHooks.register();
        loadKeybinds();

        Vars.ui.settings.addCategory("@setting.riskod-title", Icon.units, t -> {
            t.pref(new SettingsMenuDialog.SettingsTable.Setting("riskod-binds-header") {
                {
                    title = "setting.riskod-binds";
                }

                @Override
                public void add(SettingsMenuDialog.SettingsTable table) {
                    table.add("@setting.riskod-binds").colspan(2).padTop(8f).padBottom(4f).row();
                    table.add("Defaults: Z X C V B  (1-9 = unit groups)").colspan(2)
                            .color(arc.graphics.Color.gray).padBottom(6f).row();
                    for (int i = 0; i < 5; i++) {
                        final int slot = i;
                        table.table(row -> {
                            row.left();
                            row.add(Core.bundle.format("setting.riskod-bind-slot", slot + 1))
                                    .left().width(Core.graphics.getWidth() > 400 ? 220f : 160f);
                            row.button(
                                    b -> b.label(() -> slotKey(slot).toString()).labelAlign(Align.center),
                                    Styles.defaultt,
                                    () -> startRebind(slot)
                            ).width(160f).height(40f).left();
                        }).left().padTop(4f).padBottom(2f).growX().row();
                    }
                    table.button("@riskod.reset", Icon.info, () -> {
                        BaseDialog first = new BaseDialog("Reset all data?");
                        first.cont.add("are you sure? this abandons the run in progress.")
                                .pad(10f).row();
                        first.buttons.button("Cancel", Icon.cancel, first::hide).size(160f, 56f);
                        first.buttons.button("Continue", Icon.right, () -> {
                            first.hide();
                            RiskodMaps.exitRun();
                        }).size(160f, 56f);
                        first.show();
                    }).colspan(2).width(220f).height(40f).padTop(10f).left().row();

                    // table.button("@riskod.logbook", Icon.info, LogbookUi::show)
                    //        .colspan(2).width(220f).height(40f).padTop(10f).left().row();
                }
            });
        });

        Events.run(EventType.Trigger.update, () -> {
            pollRebind();
            hookPlacementVisibility();
        });

        Events.on(EventType.ClientLoadEvent.class, event -> {
            registerRiskodLogBookButton();
        });
    }

    @Override
    public void loadContent() {
        Log.info("Risk of Dustry loading");
        EntityRegistry.register();
        RiskodCont.loadRelics();
        RiskodCont.loadUnits();  //<unit
        RiskodCont.loadHeros(); //<unit
        EntityRegistry.registerUnits();
        RiskodCont.loadBlocks();
        RiskodPlanet.load();
        RiskodMaps.load();
        RiskodCont.afterPlanet();
    }

    private void registerRiskodLogBookButton() {
        Core.app.post(() -> {
            try {
                ui.menufrag.desktopButtons.get(1).submenu.add(new MenuFragment.MenuButton("@riskod-logbook", Icon.info, LogbookUi::show));

            } catch (Exception err) {
                Vars.ui.showException(err);
            }
        });
    }
}
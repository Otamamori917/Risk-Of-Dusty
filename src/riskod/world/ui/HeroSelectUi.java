package riskod.world.ui;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Time;
import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import riskod.world.RiskodMaps;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.relic.RelicType;
import riskod.world.run.HeroKitPrefs;
import riskod.world.run.PlayerLoadout;
import riskod.world.run.RunState;
import riskod.world.unit.HeroAlt;
import riskod.world.unit.PlayerCharUnitType;

/**
 * Hero pick + kit/ability customize.
 * ESC cannot dismiss while the start sector still needs a hero.
 * Return to menu clears run state and leaves the sector.
 */
public class HeroSelectUi {
    static BaseDialog open;
    static boolean allowHide;
    static boolean reopenQueued;
    static boolean registered;

    public static UnitType sessionHero;

    static final ObjectMap<String, Integer> pendingReplace = new ObjectMap<>();

    public static void register() {
        if (registered) return;
        registered = true;
        Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> {
            sessionHero = null;
            Time.run(25f, HeroSelectUi::maybePrompt);
        });
    }

    public static void maybePrompt() {
        if (Vars.headless) return;
        if (!shouldForceSelect()) return;
        if (open != null && open.isShown()) return;
        show(hero -> {
            sessionHero = hero;
            if (hero instanceof PlayerCharUnitType pc) {
                HeroKitPrefs.sanitize(pc);
                RunState.pendingLoadout = HeroKitPrefs.buildLoadout(pc);
            }
            RunState.start(hero);
            RiskodMaps.playRunStart();
        });
    }

    public static void defaultStart(UnitType hero) {
        if (hero == null) return;
        sessionHero = hero;
        if (hero instanceof PlayerCharUnitType pc) {
            HeroKitPrefs.sanitize(pc);
            RunState.pendingLoadout = HeroKitPrefs.buildLoadout(pc);
        }
        RunState.start(hero);
        RiskodMaps.playRunStart();
    }

    public static void show(Cons<UnitType> onPick) {
        if (Vars.headless) return;

        if (open != null) {
            allowHide = true;
            open.hide();
            open = null;
        }
        allowHide = false;

        Seq<PlayerCharUnitType> heroes = PlayerCharUnitType.selectable;
        if (heroes.isEmpty()) {
            Vars.ui.showInfoToast("No heroes available", 2f);
            return;
        }

        BaseDialog d = new BaseDialog("Select hero");
        open = d;
        d.keyDown(KeyCode.escape, () -> {
        });

        d.hidden(() -> {
            open = null;
            if (allowHide) {
                allowHide = false;
                return;
            }
            if (shouldForceSelect()) {
                Cons<UnitType> again = onPick != null ? onPick : HeroSelectUi::defaultStart;
                queueReopen(again);
            }
        });

        Table root = new Table();
        root.top().left();

        Table list = new Table();
        list.top().left();
        Table detail = new Table();
        detail.top().left();

        UnitType first = heroes.find(PlayerCharUnitType::unlocked);
        if (first == null) first = heroes.first();
        UnitType[] selected = {first};

        Runnable rebuildDetail = () -> {
            detail.clearChildren();
            UnitType h = selected[0];
            if (h == null) return;

            boolean locked = h instanceof PlayerCharUnitType pc && !pc.unlocked();

            detail.add(locked ? "[gray]???[]" : "[accent]" + h.localizedName + "[]")
                    .left().padBottom(6f).row();

            if (locked) {
                detail.add("[gray]Locked[]").left().row();
                PlayerCharUnitType pc = (PlayerCharUnitType) h;
                if (pc.unlock != null) {
                    detail.add(pc.unlock.describe()).wrap().width(380f).left().padBottom(8f).row();
                }
                return;
            }

            if (h.description != null && !h.description.isEmpty()) {
                detail.add(h.description).wrap().width(380f).left().padBottom(8f).row();
            }

            detail.button("Edit loadout", Icon.pencil, () -> openCustomize(h)).size(160f, 40f).left().row();
            detail.button("Start", Icon.ok, () -> {
                UnitType pick = selected[0];
                if (pick == null) return;
                if (pick instanceof PlayerCharUnitType pc && !pc.unlocked()) {
                    Vars.ui.showInfoToast("Hero locked", 2f);
                    return;
                }
                sessionHero = pick;
                allowHide = true;
                if (onPick != null) onPick.get(pick);
                d.hide();
            }).size(120f, 50f).left().padTop(8f).row();
        };

        float listW = 280f;
        Color lockedSil = Color.valueOf("2a2a2a");
        for (PlayerCharUnitType h : heroes) {
            boolean locked = !h.unlocked();
            list.button(b -> {
                b.left();
                if (h.uiIcon != null) {
                    if (locked) {
                        b.add(new Element() {
                            @Override
                            public void draw() {
                                float cx = x + width / 2f;
                                float cy = y + height / 2f;

                                Draw.shader(FlatIconShader.instance);
                                Draw.color(Color.valueOf("383838"));
                                Draw.rect(h.uiIcon, cx, cy, h.uiIcon.width, h.uiIcon.height, 0);
                                Draw.shader();
                                Draw.reset();
                            }
                        }).scaling(Scaling.fit).size(50f).padRight(8f);
                    } else {
                        b.image(h.uiIcon).size(50f).padRight(8f);
                    }
                }
                b.add(locked ? "???" : h.localizedName).left().growX()
                        .color(locked ? Color.gray : Color.white);
            }, Styles.flatTogglet, () -> {
                selected[0] = h;
                rebuildDetail.run();
            }).width(listW).height(52f).checked(b -> selected[0] == h).pad(2f).row();
        }

        rebuildDetail.run();

        root.add(list).width(listW).top().left().padRight(16f);
        root.add(detail).width(400f).top().left().growY();
        d.cont.add(root).grow().pad(12f);

        d.buttons.button(Vars.state.isCampaign() ? "Return to menu" : "Close", Icon.left, () -> {
            allowHide = true;
            d.hide();
            exitToMenu();
        }).size(180f, 50f);

        d.show();
    }

    static void queueReopen(Cons<UnitType> onPick) {
        if (reopenQueued) return;
        reopenQueued = true;
        Time.run(1f, () -> {
            reopenQueued = false;
            if (shouldForceSelect() && (open == null || !open.isShown())) {
                show(onPick);
            }
        });
    }

    public static boolean shouldForceSelect() {
        if (Vars.state == null || !Vars.state.isGame()) return false;
        if (Vars.headless) return false;
        if (sessionHero != null) return false;
        if (RunState.active() && RunState.current != null && RunState.current.heroType != null) {
            return false;
        }
        return isStartSector();
    }

    public static boolean isStartSector() {
        RiskodMaps.RiskodSector preset = RiskodMaps.currentPreset();
        if (preset == null) return false;
        if (preset.alwaysUnlocked && preset.pool == RiskodMaps.Pool.easy) return true;
        return preset.alwaysUnlocked
                && preset.pool != RiskodMaps.Pool.launch
                && preset.pool != RiskodMaps.Pool.moon;
    }

    static void openCustomize(UnitType heroType) {
        if (!(heroType instanceof PlayerCharUnitType hero)) return;
        HeroKitPrefs.sanitize(hero);

        BaseDialog d = new BaseDialog("Edit — " + hero.localizedName);
        d.keyDown(KeyCode.escape, () -> {
        });

        Table pane = new Table();
        pane.top().left();
        buildCustomize(pane, hero);

        d.cont.pane(pane).grow().pad(10f);
        d.buttons.button("Done", Icon.ok, d::hide).size(120f, 50f);
        d.show();
    }

    static void buildCustomize(Table p, PlayerCharUnitType hero) {
        p.clearChildren();
        String[] labels = {"Primary", "Secondary", "Utility", "Special", "Gear"};
        KitSwapAbility swap = findSwap(hero);

        for (int slot = 0; slot < PlayerLoadout.SLOT_COUNT; slot++) {
            if (swap != null && !showAbilitySlot(swap, slot)) continue;
            iSlot(slot, p, hero, labels);
        }

        if (swap != null && swap.kits != null && swap.kits.length > 0) {
            p.add("[accent]Kits[]").left().padTop(12f).row();
            p.add("[lightgray]Click a selected kit, then a pool kit to replace it.[]")
                    .wrap().width(420f).left().padBottom(6f).row();
            Table kitHost = new Table();
            kitHost.left();
            p.add(kitHost).left().growX().row();
            rebuildKitEditor(kitHost, hero, swap);
        }
    }

    /**
     * With kit-swap: only gear, or a slot that is null in every kit (never swapped).
     * Without kit-swap: always show.
     */
    static boolean showAbilitySlot(KitSwapAbility swap, int slot) {
        if (slot == PlayerLoadout.GEAR_SLOT) return true;
        if (swap.kits == null || swap.kits.length == 0) return true;

        if (slot >= 3) return true;

        boolean anyDefined = false;
        for (KitSwapAbility.Kits k : swap.kits) {
            if (k == null) continue;
            RelicType r = slotRelic(k, slot);
            if (r != null) {
                anyDefined = true;
                break;
            }
        }
        return !anyDefined;
    }

    static RelicType slotRelic(KitSwapAbility.Kits k, int slot) {
        if (k == null) return null;
        return switch (slot) {
            case 0 -> k.slot0;
            case 1 -> k.slot1;
            case 2 -> k.slot2;
            default -> null;
        };
    }

    static void iSlot(int slot, Table p, PlayerCharUnitType hero, String[] labels) {
        p.add("[stat]" + labels[slot] + "[]").left().padTop(8f).row();

        Table row = new Table();
        row.left();
        p.add(row).left().row();

        fillSlotRow(row, hero, slot);
    }

    /** Rebuilds buttons so only the real current pick is checked (radio behavior). */
    static void fillSlotRow(Table row, PlayerCharUnitType hero, int slot) {
        row.clearChildren();

        RelicType def = hero.startingSlots != null && slot < hero.startingSlots.length
                ? hero.startingSlots[slot] : null;
        RelicType current = HeroKitPrefs.picked(hero, slot, def);

        addPickButton(row, hero, slot, def, true, isSame(current, def), null, () -> fillSlotRow(row, hero, slot));

        Seq<HeroAlt> alts = slot < hero.slotAlts.length
                ? hero.slotAlts[slot] : null;
        if (alts != null) {
            for (HeroAlt a : alts) {
                if (a == null || a.relic == null) continue;
                addPickButton(row, hero, slot, a.relic, false, isSame(current, a.relic), a,
                        () -> fillSlotRow(row, hero, slot));
            }
        }
    }

    static boolean isSame(RelicType a, RelicType b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.name.equals(b.name);
    }

    static void addPickButton(Table row, PlayerCharUnitType hero, int slot, RelicType r,
                              boolean isDefault, boolean selected, HeroAlt alt, Runnable refresh) {
        if (r == null) return;

        boolean locked = alt != null && !alt.unlocked();
        String tip = locked && alt.unlock != null
                ? alt.unlock.describe()
                : (r.localizedName != null ? r.localizedName : r.name);

        var b = row.button(b2 -> {
            if (locked) {
                b2.add("?").style(Styles.outlineLabel);
            } else if (r.icon != null) {
                b2.image(r.icon).size(32f);
            } else {
                b2.add(r.localizedName);
            }
        }, Styles.flatt, () -> {
            if (locked) {
                Vars.ui.showInfoToast(tip, 2f);
                return;
            }
            if (isDefault) HeroKitPrefs.setSlotPick(hero, slot, null);
            else HeroKitPrefs.setSlotPick(hero, slot, r);
            if (refresh != null) refresh.run();
            Vars.ui.showInfoToast(r.localizedName, 1f);
        }).size(44f, 44f).pad(2f).get();

        if (selected && !locked) {
            b.setColor(mindustry.graphics.Pal.accent);
        } else {
            b.setColor(arc.graphics.Color.white);
        }

        tip(b, tip);
    }

    static void tip(arc.scene.Element e, String text) {
        if (e == null || text == null || text.isEmpty()) return;
        e.addListener(new Tooltip(t -> {
            t.background(Tex.button);
            t.add(text).style(Styles.outlineLabel).width(240f).wrap().left();
            t.margin(8f);
        }));
    }

    static void rebuildKitEditor(Table host, PlayerCharUnitType hero, KitSwapAbility swap) {
        host.clearChildren();
        Table box = new Table();
        box.left();

        int[] sel = HeroKitPrefs.kitSelection(hero, swap).clone();
        int pending = pendingReplace.get(hero.name, -1);

        box.add("[stat]Selected[]  [lightgray](click one to replace)[]").left().row();
        Table selRow = new Table();
        selRow.left();
        for (int i = 0; i < sel.length; i++) {
            int at = i;
            int poolIdx = sel[i];
            KitSwapAbility.Kits k = validKit(swap, poolIdx);
            boolean marked = pending == at;
            String label = kitLabel(k, poolIdx);
            label = marked ? " [accent]>" + label + "<[]" : " " + label;

            selRow.button(label, Styles.flatt, () -> {
                pendingReplace.put(hero.name, at);
                Core.app.post(() -> rebuildKitEditor(host, hero, swap));
            }).pad(2f);
        }
        box.add(selRow).left().row();

        if (pending >= 0 && pending < sel.length) {
            box.add("[lightgray]Replacing slot " + (pending + 1) + " — pick from pool[]")
                    .left().padTop(4f).row();
        }

        box.add("[stat]Pool[]").left().padTop(8f).row();
        for (int i = 0; i < swap.kits.length; i++) {
            int poolIdx = i;
            KitSwapAbility.Kits k = swap.kits[i];
            boolean selected = contains(sel, poolIdx);
            boolean locked = k != null && k.unlock != null && !k.unlock.met();

            String label;
            if (locked) {
                label = "[gray]????[]";
            } else if (selected) {
                label = "[lightgray]" + kitLabel(k, poolIdx) + " (equipped)[]";
            } else {
                label = kitLabel(k, poolIdx);
            }

            String unlockTip = locked && k.unlock != null ? k.unlock.describe() : kitLabel(k, poolIdx);

            var btn = box.button(label, Styles.flatt, () -> {
                if (locked) {
                    Vars.ui.showInfoToast(unlockTip, 2.5f);
                    return;
                }
                if (selected) {
                    Vars.ui.showInfoToast("Already in your rotation", 1.5f);
                    return;
                }
                int replaceAt = pendingReplace.get(hero.name, -1);
                if (replaceAt < 0 || replaceAt >= sel.length) {
                    Vars.ui.showInfoToast("Click a selected kit first", 2f);
                    return;
                }

                int[] cur = HeroKitPrefs.kitSelection(hero, swap).clone();
                cur[replaceAt] = poolIdx;
                HeroKitPrefs.setKitSelection(hero, cur);
                pendingReplace.remove(hero.name);
                Core.app.post(() -> rebuildKitEditor(host, hero, swap));
            }).pad(2f).left().get();

            tip(btn, unlockTip);
            box.row();
        }

        host.add(box).left().growX();
    }

    static boolean contains(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    static KitSwapAbility.Kits validKit(KitSwapAbility swap, int poolIdx) {
        if (poolIdx < 0 || poolIdx >= swap.kits.length) return null;
        return swap.kits[poolIdx];
    }

    static String kitLabel(KitSwapAbility.Kits k, int poolIdx) {
        if (k != null && k.name != null && !k.name.isEmpty()) return k.name;
        return "Kit " + (poolIdx + 1);
    }

    static KitSwapAbility findSwap(PlayerCharUnitType hero) {
        if (hero.startingSlots == null) return null;
        for (RelicType r : hero.startingSlots) {
            if (r != null && r.ability instanceof KitSwapAbility k) return k;
        }
        return null;
    }

    public static void exitToMenu() {
        if(!Vars.state.isCampaign()) return;
        sessionHero = null;
        if (RunState.current != null) {
            RunState.current.runActive = false;
            RunState.current.runOver = true;
            RunState.current = null;
        }
        RunState.pendingLoadout = null;

        Core.app.post(() -> {
            if (Vars.net != null && Vars.net.client()) {
                Vars.net.disconnect();
            }
            if (Vars.state != null && Vars.state.isCampaign()) {
                Vars.ui.planet.show();
            }
            if (Vars.state != null) {
                Vars.state.set(GameState.State.menu);
                Vars.logic.reset();
            }
        });
    }
}
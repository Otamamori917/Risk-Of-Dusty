package riskod.world.ui;

import arc.Core;
import arc.func.Cons;
import arc.func.Intc;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.ButtonGroup;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectFloatMap;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Scaling;
import arc.util.Strings;
import arc.util.Time;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import riskod.world.RiskodMaps;
import riskod.world.RiskodMaps.RiskodSector;
import riskod.world.abilites.ChargedAbility;
import riskod.world.abilites.KitSwapAbility;
import riskod.world.defect.DefectAbility;
import riskod.world.defect.DefectState;
import riskod.world.meta.Meta;
import riskod.world.relic.GearType;
import riskod.world.relic.RelicType;
import riskod.world.run.EnemySpawnDirector;
import riskod.world.run.HeroKitPrefs;
import riskod.world.run.PlayerLoadout;
import riskod.world.unit.HeroAlt;
import riskod.world.unit.PlayerCharUnitType;

import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;

/**
 * Tabbed meta-progression logbook.
 */
public class LogbookUi {
    static final String[] TABS = {"relics", "enemies", "maps", "heros", "stats", "history"};
    static final String[] TAB_NAMES = {"Relics", "Enemies", "Maps", "Heros", "Stats", "History"};
    static final String[] PIE_TABS = {"Time played", "Win / loss", "Run time", "Died to", "Damage dealt"};

    static String tab = "relics";
    static String pendingKey;
    static final TextButton[] tabButtons = new TextButton[TABS.length];
    static Runnable rebuildBody;
    static int pieTab = 0;

    static final float RESET_LOCKOUT = 3f;
    static final float SQUARE = 44f;
    static final Color LINK = Color.valueOf("9be7ff");
    static final Color WIN = Color.valueOf("8fd16a");
    static final Color LOSS = Color.valueOf("ff7a7a");
    static final Color OTHER = Color.valueOf("777777");
    static final float LOSS_SHADE = 0.45f;
    static final Color LOCKED_SIL = Color.valueOf("2a2a2a");

    static final Color[] PALETTE = {
            Color.valueOf("ff9be0"), Color.valueOf("9bffc8"), Color.valueOf("ffe08a"), Color.valueOf("9be7ff"),
            Color.valueOf("ffb06b"), Color.valueOf("b49bff"), Color.valueOf("8fd16a"), Color.valueOf("ff7a7a")
    };

    static class Entry {
        String key = "";
        String name = "";
        TextureRegion icon;
        boolean unlocked;
        boolean header;
        String unlockText = "";
        Cons<Table> card;
    }

    static class Slice {
        String label;
        String value;
        float weight;
        Color color;
        Runnable click;
    }

    static class Death {
        String name;
        int count;
    }

    static class Pie extends Element {
        final Seq<Slice> slices;
        final float total;
        final boolean border;

        Pie(Seq<Slice> slices, float total) {
            this(slices, total, false);
        }

        Pie(Seq<Slice> slices, float total, boolean border) {
            this.slices = slices;
            this.total = total;
            this.border = border;
            touchable = Touchable.enabled;
            addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Slice hit = sliceAt(x, y);
                    if (hit != null && hit.click != null) hit.click.run();
                }
            });
        }

        Slice sliceAt(float lx, float ly) {
            float dx = lx - width / 2f, dy = ly - height / 2f;
            float r = Math.min(width, height) / 2f;
            if (dx * dx + dy * dy > r * r) return null;
            float a = Angles.angle(dx, dy);
            float from = 0f;
            for (int i = 0; i < slices.size; i++) {
                float span = slices.get(i).weight / total * 360f;
                if (a >= from && a < from + span) return slices.get(i);
                from += span;
            }
            return null;
        }

        @Override
        public void draw() {
            float cx = x + width / 2f, cy = y + height / 2f;
            float r = Math.min(width, height) / 2f - 2f;
            if (border) {
                Draw.color(Color.black, parentAlpha);
                Fill.circle(cx, cy, r + 3f);
            }
            float from = 0f;
            for (int i = 0; i < slices.size; i++) {
                Slice s = slices.get(i);
                float frac = s.weight / total;
                Draw.color(s.color, parentAlpha);
                Fill.arc(cx, cy, r, frac, from);
                from += frac * 360f;
            }
            Draw.reset();
        }
    }

    static Entry header(String name) {
        Entry e = new Entry();
        e.header = true;
        e.name = name;
        return e;
    }

    static Entry entry(String key, String name, TextureRegion icon, boolean unlocked, String unlockText, Cons<Table> card) {
        Entry e = new Entry();
        e.key = key;
        e.name = name;
        e.icon = icon == null ? Icon.info.getRegion() : icon;
        e.unlocked = unlocked;
        e.unlockText = unlockText == null ? "" : unlockText;
        e.card = card;
        return e;
    }

    static Entry entry(String key, String name, TextureRegion icon, boolean unlocked, Cons<Table> card) {
        return entry(key, name, icon, unlocked, "", card);
    }

    /** Logbook: found at least once. */
    static boolean relicLogbookUnlocked(RelicType r) {
        if (r == null) return false;
        Meta.RelicStat s = Meta.relic(r.name);
        return s != null && s.found > 0;
    }

    /** Logbook: at least one win with this hero. */
    static boolean heroLogbookUnlocked(PlayerCharUnitType h) {
        if (h == null) return false;
        Meta.HeroStat s = Meta.hero(h.name);
        return s != null && s.wins > 0;
    }

    static boolean enemyUnlocked(String name) {
        Meta.EnemyStat s = Meta.enemy(name);
        return s != null && (s.kills > 0 || s.deaths > 0);
    }

    public static void show() {
        tab = "relics";
        pendingKey = null;
        BaseDialog d = new BaseDialog("@riskod.logbook");
        Table body = new Table();

        float w = Math.min(Core.graphics.getWidth() * 0.95f, 1100f);
        float h = Math.min(Core.graphics.getHeight() * 0.7f, 480f);

        d.cont.pane(Styles.noBarPane, t -> {
            t.left();
            ButtonGroup<TextButton> group = new ButtonGroup<>();
            for (int i = 0; i < TABS.length; i++) {
                String id = TABS[i];
                TextButton b = t.button(TAB_NAMES[i], Styles.togglet, () -> {
                    tab = id;
                    rebuildBody.run();
                }).group(group).height(36f).minWidth(76f).padRight(2f).get();
                b.setChecked(id.equals(tab));
                tabButtons[i] = b;
            }
        }).size(w, 36).growX().row();

        d.cont.add(body).size(w, h);

        rebuildBody = () -> {
            body.clear();
            switch (tab) {
                case "relics" -> buildEntries(body, relicEntries());
                case "enemies" -> buildEntries(body, enemyEntries());
                case "maps" -> buildEntries(body, mapEntries());
                case "heros" -> buildEntries(body, heroEntries());
                case "stats" -> buildStats(body);
                default -> buildEntries(body, historyEntries());
            }
        };
        rebuildBody.run();

        d.addCloseButton();
        var reset = d.buttons.button("Reset all data", Icon.trash, () -> confirmReset(() -> rebuildBody.run()))
                .size(210f, 64f).get();
        reset.getLabel().setColor(Color.scarlet);
        d.show();
    }

    static void jump(String tabId, String key) {
        tab = tabId;
        pendingKey = key;
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i].equals(tabId) && tabButtons[i] != null) tabButtons[i].setChecked(true);
        }
        rebuildBody.run();
    }

    static void confirmReset(Runnable after) {
        BaseDialog first = new BaseDialog("Reset all data?");
        first.cont.add("This erases every unlock, lifetime stat and run history,\nand abandons the run in progress.")
                .pad(10f).row();
        first.buttons.button("Cancel", Icon.cancel, first::hide).size(160f, 56f);
        first.buttons.button("Continue", Icon.right, () -> {
            first.hide();
            confirmFinal(after);
        }).size(160f, 56f);
        first.show();
    }

    static void confirmFinal(Runnable after) {
        BaseDialog d = new BaseDialog("Are you really sure?");
        d.cont.add("[scarlet]This cannot be undone.[]\nAll logbook progress and the current run will be deleted.")
                .pad(10f).row();
        d.buttons.button("Cancel", Icon.cancel, d::hide).size(160f, 56f);

        float[] left = {RESET_LOCKOUT};
        var yes = d.buttons.button("Yes, delete everything", Icon.trash, () -> {
            if (left[0] > 0f) return;
            d.hide();
            RiskodMaps.exitRun();
            Meta.reset();
            HeroKitPrefs.sanitizeAll();
            after.run();
        }).size(260f, 56f).get();

        yes.setDisabled(true);
        yes.update(() -> {
            if (left[0] <= 0f) return;
            left[0] -= Time.delta / 60f;
            if (left[0] <= 0f) {
                yes.setDisabled(false);
                yes.getLabel().setText("Yes, delete everything");
            } else {
                yes.getLabel().setText("Yes, delete everything (" + (int) Math.ceil(left[0]) + ")");
            }
        });
        d.show();
    }

    static void buildEntries(Table body, Seq<Entry> entries) {
        if (entries.isEmpty()) {
            pendingKey = null;
            body.add("Nothing here yet.").color(Color.gray);
            return;
        }

        Table cardHolder = new Table();
        cardHolder.top().left();
        cardHolder.add("Select an entry").color(Color.gray);
        Table[] rows = new Table[entries.size];

        Intc select = idx -> {
            for (int i = 0; i < rows.length; i++) {
                if (rows[i] != null) rows[i].background(i == idx ? Tex.buttonDown : Tex.button);
            }
            cardHolder.clear();
            cardHolder.top().left();
            Entry e = entries.get(idx);
            if (e.unlocked) {
                e.card.get(cardHolder);
            } else {
                cardHolder.add("[accent]Locked[]").left().padBottom(6f).row();
                cardHolder.add(e.unlockText.isEmpty() ? "???" : e.unlockText)
                        .wrap().width(380f).left();
            }
        };

        ScrollPane listPane = body.pane(list -> {
            list.top();
            for (int i = 0; i < entries.size; i++) {
                Entry e = entries.get(i);
                if (e.header) {
                    list.add("[accent]" + e.name + "[]").left().padTop(6f).padBottom(2f).row();
                    continue;
                }
                int idx = i;
                Table row = new Table(Tex.button);
                row.left();
                if (e.unlocked) {
                    row.image(e.icon).scaling(Scaling.fit).size(60f).padRight(8f);
                } else {
                    row.add(new Element() {
                        @Override
                        public void draw() {
                            float cx = x + width / 2f;
                            float cy = y + height / 2f;

                            Draw.shader(FlatIconShader.instance);
                            Draw.color(Color.valueOf("383838"));
                            Draw.rect(e.icon, cx, cy, e.icon.width, e.icon.height, 0);
                            Draw.shader();
                            Draw.reset();
                        }
                    }).scaling(Scaling.fit).size(60f).padRight(8f);
                }
                row.add(e.unlocked ? e.name : "???")
                        .color(e.unlocked ? Color.white : Color.gray).left().growX();
                row.touchable = Touchable.enabled;
                row.clicked(() -> select.get(idx));
                rows[i] = row;
                list.add(row).growX().minHeight(60f).pad(2f).row();
            }
        }).width(400f).growY().padRight(10f).get();

        body.pane(cardHolder).grow();

        if (pendingKey == null) return;
        String key = pendingKey;
        pendingKey = null;
        for (int i = 0; i < entries.size; i++) {
            Entry e = entries.get(i);
            if (e.header || !e.unlocked || !key.equals(e.key)) continue;
            int idx = i;
            select.get(idx);
            Core.app.post(() -> {
                listPane.act(0f);
                listPane.layout();
                float fromTop = listPane.getWidget().getHeight() - rows[idx].getY(0) - rows[idx].getHeight();
                listPane.setScrollYForce(Math.max(0f, fromTop - 60f));
            });
            break;
        }
    }

    static Seq<RiskodSector> sectors() {
        Seq<RiskodSector> all = new Seq<>();
        all.addAll(RiskodMaps.easy);
        all.addAll(RiskodMaps.hard);
        if (RiskodMaps.launch != null) all.add(RiskodMaps.launch);
        if (RiskodMaps.moon != null) all.add(RiskodMaps.moon);
        return all;
    }

    static Seq<RelicType> relicCatalog() {
        ObjectSet<RelicType> starting = new ObjectSet<>();
        for (PlayerCharUnitType h : PlayerCharUnitType.selectable) {
            if (h.startingSlots == null) continue;
            for (RelicType r : h.startingSlots) {
                if (r == null) continue;
                starting.add(r);
                if (r.ability instanceof KitSwapAbility k) {
                    for (KitSwapAbility.Kits kit : k.kits) {
                        if (kit == null) continue;
                        for (int i = 0; i < KitSwapAbility.KIT_FLEX; i++) {
                            RelicType kr = kit.slot(i);
                            if (kr != null) starting.add(kr);
                        }
                    }
                }
            }
        }
        Seq<RelicType> out = new Seq<>();
        for (RelicType r : RelicType.all) {
            if (starting.contains(r) && !(r instanceof GearType)) continue;
            out.add(r);
        }
        return out;
    }

    static Seq<Entry> relicEntries() {
        Seq<RelicType> passives = new Seq<>(), others = new Seq<>(), gear = new Seq<>();
        for (RelicType r : relicCatalog()) {
            if (r instanceof GearType || r.slotKind == RelicType.SlotKind.gear) gear.add(r);
            else if (r.slotKind == RelicType.SlotKind.passive) passives.add(r);
            else others.add(r);
        }
        Seq<Entry> out = new Seq<>();
        addRelicGroup(out, "Passives", passives);
        addRelicGroup(out, "Abilities", others);
        addRelicGroup(out, "Gear", gear);
        return out;
    }

    static void addRelicGroup(Seq<Entry> out, String title, Seq<RelicType> list) {
        if (list.isEmpty()) return;
        list.sort(Comparator.comparingInt(a -> a.rarity));
        out.add(header(title));
        for (RelicType r : list) {
            boolean open = relicLogbookUnlocked(r);
            String req;
            if (!r.unlocked()) {
                req = r.unlock != null ? r.unlock.describe() : "Locked";
            } else if (!open) {
                req = "Find this relic at least once";
            } else {
                req = "";
            }
            out.add(entry(r.name, r.localizedName, r.icon, open, req, t -> relicCard(t, r)));
        }
    }

    static Seq<Entry> enemyEntries() {
        Seq<UnitType> enemies = new Seq<>(), bosses = new Seq<>();
        for (UnitType t : EnemySpawnDirector.enemyPool) enemies.addUnique(t);
        Seq<RiskodSector> all = sectors();
        for (RiskodSector s : all) {
            for (UnitType t : s.enemyPool) enemies.addUnique(t);
        }
        for (RiskodSector s : all) {
            for (UnitType t : s.bossPool) {
                if (!enemies.contains(t)) bosses.addUnique(t);
            }
        }
        Seq<Entry> out = new Seq<>();
        addEnemyGroup(out, "Enemies", enemies);
        addEnemyGroup(out, "Bosses", bosses);
        return out;
    }

    static void addEnemyGroup(Seq<Entry> out, String title, Seq<UnitType> list) {
        if (list.isEmpty()) return;
        list.sort((a, b) -> Float.compare(a.health, b.health));
        out.add(header(title));
        for (UnitType u : list) {
            out.add(entry(u.name, u.localizedName, u.uiIcon, enemyUnlocked(u.name),
                    "Kill or die to this unit", t -> enemyCard(t, u)));
        }
    }

    static Seq<Entry> mapEntries() {
        Seq<RiskodSector> list = new Seq<>();
        for (RiskodSector s : sectors()) {
            if (!s.unfinished) list.add(s);
        }
        list.sort((a, b) -> Float.compare(a.difficulty, b.difficulty));
        Seq<Entry> out = new Seq<>();
        for (RiskodSector s : list) {
            Meta.MapStat m = Meta.map(s.name);
            boolean open = m != null && m.escapes > 0;
            out.add(entry(s.name, s.localizedName, s.uiIcon, open,
                    "Escape this sector via teleporter", t -> mapCard(t, s)));
        }
        return out;
    }

    static Seq<Entry> heroEntries() {
        Seq<Entry> out = new Seq<>();
        for (PlayerCharUnitType h : PlayerCharUnitType.selectable) {
            boolean open = heroLogbookUnlocked(h);
            String req;
            if (!h.unlocked()) {
                req = h.unlock != null ? h.unlock.describe() : "Locked";
            } else if (!open) {
                req = "Win a run as this hero";
            } else {
                req = "";
            }
            out.add(entry(h.name, h.localizedName, h.uiIcon, open, req, t -> heroCard(t, h)));
        }
        return out;
    }

    static Seq<Entry> historyEntries() {
        Seq<Entry> out = new Seq<>();
        Seq<Meta.RunRecord> hist = Meta.data().history;
        for (int i = hist.size - 1; i >= 0; i--) {
            Meta.RunRecord rec = hist.get(i);
            UnitType hero = Vars.content.unit(rec.hero);
            String label = (rec.win ? "Win" : "Loss") + " - " + heroName(rec.hero) + " - " + formatTime(rec.time);
            out.add(entry(String.valueOf(i), label, hero == null ? null : hero.uiIcon, true, t -> historyCard(t, rec)));
        }
        return out;
    }

    static void relicCard(Table t, RelicType r) {
        Meta.RelicStat s = Meta.relic(r.name);
        title(t, r.localizedName);
        line(t, "Type", kindLabel(r));
        if (typeLabel(r) != null) line(t, "Applys to", typeLabel(r));
        line(t, "Rarity", rarityLabel(r.rarity));
        if (r.requiredHero != null) line(t, "Hero", r.requiredHero.localizedName);
        if (r.onlyHeroes.any()) line(t, "Heroes", r.onlyHeroes.toString(", ", u -> u.localizedName));
        desc(t, relicBody(r));

        pct(t, "Health", r.healthMul);
        pct(t, "Speed", r.speedMul);
        pct(t, "Ability damage", r.abilityDamageMul);
        pct(t, "Ability range", r.abilityRangeMul);
        pct(t, "Ability cooldown", r.abilityCooldownMul);
        pct(t, "Gear cooldown", r.gearCooldownMul);
        pct(t, "Luck", r.luckMul);
        pct(t, "Pulse interval", r.pulseIntervalMul);
        if (r.bonusLuck != 0f) line(t, "Bonus luck", signed(r.bonusLuck));
        if (r.bonusAbilityCharges != 0) line(t, "Ability charges", signed(r.bonusAbilityCharges));
        if (r.bonusGearCharges != 0) line(t, "Gear charges", signed(r.bonusGearCharges));
        if (r.bonusFocus != 0) line(t, "Focus", signed(r.bonusFocus));
        if (r.grantFocus != 0) line(t, "Focus on pickup", signed(r.grantFocus));
        if (r.bonusOrbCapacity != 0) line(t, "Orb capacity", signed(r.bonusOrbCapacity));
        if (r.bonusEnergyCap != 0f) line(t, "Energy cap", signed(r.bonusEnergyCap));
        if (r.plasmaBankPermanent) line(t, "Excess Energy", "no longer expires");
        if (r.mrBones) line(t, "Lucky break", "\nPrevents Death if teleporter has at least [stat]25%[] charge \nthen [scarlet]self destructs[]");
        if (r.obelisk) line(t, "Ominous",
                "\nReduces [stat]All[] slots cooldowns by [stat]" + (PlayerLoadout.OBELISK_BONUS * 100) + "%[] \nper consecutive slot used without using the same slot twice in a row \nCaps out at [stat]" + (int) ((1 + (1 - PlayerLoadout.OBELISK_HARDCAP)) * 100) + "%[]");
        if (r.negativeObelisk) line(t, "Negative Ominous",
                "\nIncreases [stat]All[] slot's Damage by [stat]" + (PlayerLoadout.NEGATIVE_OBELISK_BONUS * 100) + "%[] \nper consecutive use of the same slot without using a different slot \nCaps out at [stat]" + (int) (PlayerLoadout.NEGATIVE_OBELISK_HARDCAP * 100) + "%[]");
        if (r.stuntMan) line(t, "Daring",
                """
                        Reduces max charges of [stat]ALL[] slots by [scarlet]2[].
                        If a slot has insufficient charges, \nit gains [scarlet]+10%[] cooldown per missing charge instead.
                        Grants [stat]+20%[] damage and range to [stat]ALL[] slots.""");
        if (r.smeared) line(t, "Confusion",
                """
                        \nbuffs that effect only the [stat]Primary[] slot: \n  |> now also apply to the [stat]Utility[] slot
                        buffs that effect only the [stat]Secondary[] slot: \n  |> now also apply to the [stat]Special[] slot
                        buffs that effect only the [stat]Utility[] slot: \n  |> now also apply to the [stat]Primary[] slot
                        buffs that effect only the [stat]Special[] slot: \n  |> now also apply to the [stat]Secondary[] slot""");

        if (r instanceof GearType g) {
            line(t, "Charges", String.valueOf(g.maxCharges));
            line(t, "Cooldown", Strings.autoFixed(g.cooldown / 60f, 1) + "s");
            if (g.healAmount > 0f) line(t, "Heals", fmt(g.healAmount));
        } else if (r.ability instanceof ChargedAbility c && !(r.ability instanceof KitSwapAbility)) {
            line(t, "Charges", String.valueOf(c.maxCharges));
            line(t, "Cooldown", Strings.autoFixed(c.cooldown / 60f, 1) + "s");
        }

        String[] keywords = AbilityGlossary.keywordsFor(r);
        if (keywords.length > 0) {
            section(t, "Keywords");
            for (String k : keywords) {
                t.add("[accent]" + k + "[]").left().row();
                t.add(AbilityGlossary.term(k)).color(Color.lightGray).wrap().width(380f).left().padBottom(4f).row();
            }
        }

        section(t, "Record");
        line(t, "Found", s == null ? "0" : String.valueOf(s.found));
        if (r.slotKind == RelicType.SlotKind.passive && !r.unstackable) {
            line(t, "Most stacked at once", s == null ? "0" : String.valueOf(s.maxStack));
        }
    }

    static String relicBody(RelicType r) {
        StringBuilder sb = new StringBuilder();
        if (r.description != null && !r.description.isEmpty()) sb.append(r.description);
        String extra = null;
        if (r.ability instanceof DefectAbility d) {
            extra = AbilityGlossary.defectBody(d, new DefectState());
            if (d.energyCost > 0f) extra += "\nCost: -" + fmt(d.energyCost) + " Energy";
        } else if (r.ability instanceof KitSwapAbility) {
            extra = "Cycle the active kit.";
        } else if (r.ability instanceof ChargedAbility c) {
            extra = AbilityGlossary.chargedBody(c, null, -1);
        }
        if (extra != null) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(extra);
        }
        return sb.toString();
    }

    static void enemyCard(Table t, UnitType u) {
        Meta.EnemyStat s = Meta.enemy(u.name);
        title(t, u.localizedName);
        desc(t, u.description);
        line(t, "Health", fmt(u.health));
        line(t, "Speed", Strings.autoFixed(u.speed, 2));
        line(t, "Size", fmt(u.hitSize));
        line(t, "Range", Strings.autoFixed(u.range / 8f, 1) + " blocks");
        if (u.flying) line(t, "Movement", "flying");
        section(t, "Record");
        line(t, "Killed", s == null ? "0" : String.valueOf(s.kills));
        line(t, "Died to", s == null ? "0" : String.valueOf(s.deaths));
    }

    static void mapCard(Table t, RiskodSector p) {
        Meta.MapStat m = Meta.map(p.name);
        title(t, p.localizedName);
        desc(t, p.description);
        line(t, "Difficulty", fmt(p.difficulty));
        if (p.minChests >= 0) line(t, "Chests", p.minChests + " - " + p.maxChests);
        if (p.minShrines >= 0) line(t, "Shrines", p.minShrines + " - " + p.maxShrines);
        section(t, "Record");
        line(t, "Escaped via teleporter", m == null ? "0" : String.valueOf(m.escapes));
        line(t, "Shrines activated", m == null ? "0" : String.valueOf(m.shrines));
        line(t, "Relic chests opened", m == null ? "0" : String.valueOf(m.relicChests));
        line(t, "Drone chests opened", m == null ? "0" : String.valueOf(m.droneChests));
    }

    static KitSwapAbility heroKitSwap(PlayerCharUnitType h) {
        if (h.startingSlots == null) return null;
        for (RelicType r : h.startingSlots) {
            if (r != null && r.ability instanceof KitSwapAbility k) return k;
        }
        return null;
    }

    static Table square(RelicType r, int slot) {
        Table sq = new Table(Tex.button);
        sq.touchable = Touchable.enabled;
        sq.image(r.icon == null ? Icon.info.getRegion() : r.icon).size(SQUARE - 12f);
        RelicTooltip.attach(sq, () -> r, () -> null, () -> null, slot);
        return sq;
    }

    static void heroCard(Table t, PlayerCharUnitType h) {
        Meta.HeroStat s = Meta.hero(h.name);
        title(t, h.localizedName);
        desc(t, h.description);
        line(t, "Health", fmt(h.health));
        line(t, "Speed", Strings.autoFixed(h.speed, 2));
        line(t, "Size", fmt(h.hitSize));
        if (h.flying) line(t, "Flying", "true");

        KitSwapAbility swap = heroKitSwap(h);
        RelicType swapRelic = null;
        if (swap != null && h.startingSlots != null) {
            for (RelicType r : h.startingSlots) {
                if (r != null && r.ability == swap) {
                    swapRelic = r;
                    break;
                }
            }
        }

        if (swap != null && swap.kits.length > 0) {
            section(t, "Default kits");
            int base = swap.baseKitCount > 0 ? Math.min(swap.baseKitCount, swap.kits.length) : swap.kits.length;
            for (int i = 0; i < base; i++) {
                KitSwapAbility.Kits k = swap.kits[i];
                if (k == null) continue;
                String kitName = k.name == null || k.name.isEmpty() ? "Kit " + (i + 1) : k.name;
                t.add("[accent]" + kitName + "[]").left().padTop(4f).row();
                if (k.description != null && !k.description.isEmpty()) {
                    t.add(k.description).color(Color.lightGray).wrap().width(380f).left().row();
                }
                Table squares = new Table();
                squares.left();
                for (int j = 0; j < KitSwapAbility.KIT_FLEX; j++) {
                    RelicType kr = k.slot(j);
                    if (kr != null) squares.add(square(kr, j)).size(SQUARE).pad(2f);
                }
                t.add(squares).left().row();
            }
        }

        section(t, swap != null ? "Other starting relics" : "Starting relics");
        Table loadout = new Table();
        loadout.left();
        boolean any = false;
        if (h.startingSlots != null) {
            for (int i = 0; i < h.startingSlots.length; i++) {
                RelicType r = h.startingSlots[i];
                if (r == null) continue;
                if (swap != null && i < KitSwapAbility.KIT_FLEX) continue;
                if (swap != null && r == swapRelic) continue;
                any = true;
                loadout.add(square(r, i)).size(SQUARE).pad(2f);
            }
        }
        if (any) t.add(loadout).left().row();
        else t.add("None").color(Color.gray).left().row();

        if (swap != null && swap.kits.length > (swap.baseKitCount > 0 ? swap.baseKitCount : swap.kits.length)) {
            section(t, "Alternate kits");
            int base = swap.baseKitCount > 0 ? Math.min(swap.baseKitCount, swap.kits.length) : swap.kits.length;
            for (int i = base; i < swap.kits.length; i++) {
                KitSwapAbility.Kits k = swap.kits[i];
                if (k == null) continue;
                boolean locked = k.unlock != null && !k.unlock.met();
                String kitName = k.name == null || k.name.isEmpty() ? "Kit " + (i + 1) : k.name;
                if (!locked) t.add("[accent]" + kitName + "[]").left().padTop(4f).row();
                if (locked && k.unlock != null) {
                    t.add(k.unlock.describe()).color(Color.lightGray).wrap().width(380f).left().row();
                } else if (k.description != null && !k.description.isEmpty()) {
                    t.add(k.description).color(Color.lightGray).wrap().width(380f).left().row();
                }
                if (!locked) {
                    Table squares = new Table();
                    squares.left();
                    for (int j = 0; j < KitSwapAbility.KIT_FLEX; j++) {
                        RelicType kr = k.slot(j);
                        if (kr != null) squares.add(square(kr, j)).size(SQUARE).pad(2f);
                    }
                    t.add(squares).left().row();
                }
            }
        } else if (swap != null && swap.kits.length > 0) {
            boolean anyLocked = false;
            for (KitSwapAbility.Kits k : swap.kits) {
                if (k != null && k.unlock != null && !k.unlock.met()) {
                    anyLocked = true;
                    break;
                }
            }
            if (anyLocked) {
                section(t, "Kit unlocks");
                for (int i = 0; i < swap.kits.length; i++) {
                    KitSwapAbility.Kits k = swap.kits[i];
                    if (k == null || k.unlock == null || k.unlock.met()) continue;
                    String kitName = k.name == null || k.name.isEmpty() ? "Kit " + (i + 1) : k.name;
                    t.add("[gray]" + kitName + "[]").left().padTop(2f).row();
                    t.add(k.unlock.describe()).color(Color.lightGray).wrap().width(380f).left().row();
                }
            }
        }

        String[] lab = {"Primary", "Secondary", "Utility", "Special", "Gear"};
        boolean anyAlt = false;
        for (int slot = 0; slot < PlayerLoadout.SLOT_COUNT; slot++) {
            Seq<HeroAlt> alts = h.altsFor(slot);
            if (alts == null || alts.isEmpty()) continue;
            if (!anyAlt) {
                section(t, "Alternate abilities");
                anyAlt = true;
            }
            t.add("[accent]" + lab[slot] + "[]").left().padTop(4f).row();
            Table row = new Table();
            row.left();
            for (HeroAlt a : alts) {
                if (a.relic == null) continue;
                if (!a.unlocked()) {
                    t.add("[gray]" + a.unlock.describe() + "[]").left().padTop(2f).row();
                    continue;
                }
                row.add(square(a.relic, slot)).size(SQUARE).pad(2f);
            }
            t.add(row).left().row();
        }

        desc(t, h.details);

        section(t, "Record");
        line(t, "Runs", s == null ? "0" : String.valueOf(s.runs));
        line(t, "Wins", s == null ? "0" : String.valueOf(s.wins));
        line(t, "Highest level", s == null ? "0" : String.valueOf(s.highestLevel));
        line(t, "Time played", formatTime(s == null ? 0f : s.timePlayed));
        if (!h.unlocked()) {
            section(t, "Play unlock");
            t.add(h.unlock != null ? h.unlock.describe() : "Locked").color(Color.lightGray).wrap().width(380f).left().row();
        }
    }

    static void historyCard(Table t, Meta.RunRecord rec) {
        title(t, heroName(rec.hero) + (rec.win ? " - Win" : " - Loss"));
        line(t, "Date", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(rec.date)));
        if (!rec.win && rec.killedBy != null) {
            UnitType killer = Vars.content.unit(rec.killedBy);
            line(t, "Killed by", killer == null ? rec.killedBy : killer.localizedName);
        }
        line(t, "Time", formatTime(rec.time));
        line(t, "Stage", String.valueOf(rec.stage));
        line(t, "Level", String.valueOf(rec.level));
        line(t, "Kills", String.valueOf(rec.kills));
        line(t, "Bosses Killed", String.valueOf(rec.bossesKilled));
        line(t, "Damage", fmt(rec.damageDealt));
        line(t, "Damage Received", fmt(rec.damageReceived));
        line(t, "Healing Received", fmt(rec.healingReceived));
        line(t, "Items Obtained", String.valueOf(rec.itemsObtained));
        line(t, "Relics Obtained", String.valueOf(rec.relicsObtained));

        section(t, "Relics");
        if (rec.relics.length == 0) {
            t.add("None").color(Color.gray).left().row();
            return;
        }
        Seq<RelicType> catalog = relicCatalog();
        Seq<String> unique = new Seq<>();
        for (String n : rec.relics) unique.addUnique(n);
        for (String n : unique) {
            int count = 0;
            for (String o : rec.relics) {
                if (o.equals(n)) count++;
            }
            RelicType r = PlayerLoadout.find(n);
            String name = r == null ? n : r.localizedName;
            Meta.RelicStat rs = Meta.relic(n);
            boolean link = r != null && catalog.contains(r) && rs != null && rs.found > 0;

            Label l = new Label(count > 1 ? name + " x" + count : name);
            if (link) {
                l.setColor(LINK);
                l.touchable = Touchable.enabled;
                l.clicked(() -> jump("relics", r.name));
            }
            t.add(l).left().row();
        }
    }

    static void buildStats(Table body) {
        Meta.Totals s = Meta.data().totals;
        body.pane(t -> {
            t.top().left();
            section(t, "Lifetime");
            line(t, "Runs won / lost", s.runsWon + " / " + s.runsLost);
            line(t, "Best winning run", s.bestWinTime > 0f ? formatTime(s.bestWinTime) : "none yet");
            line(t, "Time played", formatTime(s.timePlayed));
            line(t, "Enemies killed", String.valueOf(s.enemiesKilled));
            line(t, "Bosses killed", String.valueOf(s.bossesKilled));
            line(t, "Damage dealt", fmt(s.damageDealt));
            line(t, "Damage received", fmt(s.damageReceived));
            line(t, "Healing received", fmt(s.healingReceived));
            line(t, "Items obtained", String.valueOf(s.itemsObtained));
            line(t, "XP gained", fmt(s.xpGained));
            line(t, "Relics obtained", String.valueOf(s.relicsObtained));
            line(t, "Teleporter escapes", String.valueOf(s.escapes));
            line(t, "Shrines activated", String.valueOf(s.shrines));
            line(t, "Relic chests opened", String.valueOf(s.relicChests));
            line(t, "Drone chests opened", String.valueOf(s.droneChests));
            line(t, "Interactables used", String.valueOf(s.interactables));

            section(t, "Heroes");
            Table pieBody = new Table();
            pieBody.left().top();
            Runnable[] rebuildPie = new Runnable[1];

            t.table(Styles.black, box -> {
                box.margin(10f);
                box.top().left();
                box.table(tabs -> {
                    tabs.left();
                    ButtonGroup<TextButton> group = new ButtonGroup<>();
                    for (int i = 0; i < PIE_TABS.length; i++) {
                        int idx = i;
                        TextButton b = tabs.button(PIE_TABS[i], Styles.togglet, () -> {
                            pieTab = idx;
                            rebuildPie[0].run();
                        }).group(group).height(32f).minWidth(96f).padRight(2f).get();
                        b.setChecked(idx == pieTab);
                    }
                }).left().row();
                box.add(pieBody).left().padTop(8f).row();
            }).left().minWidth(560f).padTop(4f).row();

            rebuildPie[0] = () -> {
                pieBody.clear();
                switch (pieTab) {
                    case 0 -> timePie(pieBody);
                    case 1 -> winLossPie(pieBody);
                    case 2 -> runTimePie(pieBody);
                    case 3 -> deathPie(pieBody);
                    default -> damagePie(pieBody);
                }
            };
            rebuildPie[0].run();
        }).grow();
    }

    static Slice slice(String label, String value, float weight, Color color, Runnable click) {
        Slice s = new Slice();
        s.label = label;
        s.value = value;
        s.weight = weight;
        s.color = color;
        s.click = click;
        return s;
    }

    static Color paletteColor(int i) {
        return PALETTE[i % PALETTE.length];
    }

    static Color lossShade(Color c) {
        return c.cpy().lerp(Color.black, LOSS_SHADE);
    }

    static Runnable heroJump(PlayerCharUnitType h) {
        if (!heroLogbookUnlocked(h)) return null;
        return () -> jump("heros", h.name);
    }

    static float bestWinTime(String hero) {
        float best = 0f;
        Seq<Meta.RunRecord> hist = Meta.data().history;
        for (int i = 0; i < hist.size; i++) {
            Meta.RunRecord rec = hist.get(i);
            if (!rec.win || !hero.equals(rec.hero) || rec.time <= 0f) continue;
            if (best <= 0f || rec.time < best) best = rec.time;
        }
        return best;
    }

    static void pieWithLegend(Table out, Seq<Slice> slices, boolean showPct) {
        float total = 0f;
        for (int i = 0; i < slices.size; i++) total += slices.get(i).weight;
        if (total <= 0f) {
            out.add("Nothing recorded yet.").color(Color.gray).left();
            return;
        }

        float sum = total;
        out.add(new Pie(slices, total)).size(220f).padRight(16f).top();
        out.table(legend -> {
            legend.top().left();
            for (int i = 0; i < slices.size; i++) {
                Slice sl = slices.get(i);
                legend.table(row -> {
                    row.left();
                    row.image(Tex.whiteui).size(14f).padRight(8f).color(sl.color);
                    String pct = showPct ? "  " + Strings.autoFixed(sl.weight / sum * 100f, 0) + "%" : "";
                    row.add(sl.label + " [lightgray]" + sl.value + pct + "[]")
                            .color(sl.click != null ? LINK : Color.white).left();
                    if (sl.click != null) {
                        row.touchable = Touchable.enabled;
                        row.clicked(sl.click);
                    }
                }).left().padBottom(3f).row();
            }
        }).top().left();
    }

    static void timePie(Table out) {
        Seq<Slice> list = new Seq<>();
        Seq<PlayerCharUnitType> heroes = PlayerCharUnitType.selectable;
        for (int i = 0; i < heroes.size; i++) {
            PlayerCharUnitType h = heroes.get(i);
            Meta.HeroStat hs = Meta.hero(h.name);
            if (hs == null || hs.timePlayed <= 0f) continue;
            list.add(slice(h.localizedName, formatTime(hs.timePlayed), hs.timePlayed, paletteColor(i), heroJump(h)));
        }
        pieWithLegend(out, list, true);
    }

    static void runTimePie(Table out) {
        out.add("Fastest winning run per hero. Shorter run = bigger slice.").color(Color.gray).left().row();
        Table inner = new Table();
        inner.left().top();
        Seq<Slice> list = new Seq<>();
        Seq<PlayerCharUnitType> heroes = PlayerCharUnitType.selectable;
        for (int i = 0; i < heroes.size; i++) {
            PlayerCharUnitType h = heroes.get(i);
            float best = bestWinTime(h.name);
            if (best <= 0f) continue;
            list.add(slice(h.localizedName, formatTime(best), 1f / best, paletteColor(i), heroJump(h)));
        }
        pieWithLegend(inner, list, false);
        out.add(inner).left();
    }

    static void winLossPie(Table out) {
        Seq<Slice> winSlices = new Seq<>(), lossSlices = new Seq<>();
        Seq<PlayerCharUnitType> heroes = PlayerCharUnitType.selectable;
        int totalWins = 0, totalLosses = 0;

        for (int i = 0; i < heroes.size; i++) {
            PlayerCharUnitType h = heroes.get(i);
            Meta.HeroStat hs = Meta.hero(h.name);
            if (hs == null || hs.runs <= 0) continue;

            int wins = hs.wins;
            int losses = Math.max(0, hs.runs - hs.wins);
            totalWins += wins;
            totalLosses += losses;

            Color c = paletteColor(i);
            if (wins > 0) winSlices.add(slice(h.localizedName + " wins", String.valueOf(wins), wins, c, heroJump(h)));
            if (losses > 0) lossSlices.add(slice(h.localizedName + " losses", String.valueOf(losses), losses, lossShade(c), heroJump(h)));
        }

        int overall = totalWins + totalLosses;
        if (overall <= 0) {
            out.add("Nothing recorded yet.").color(Color.gray).left();
            return;
        }

        Seq<Slice> ring = new Seq<>();
        ring.addAll(winSlices);
        ring.addAll(lossSlices);

        Seq<Slice> center = new Seq<>();
        center.add(slice("Wins", String.valueOf(totalWins), totalWins, WIN, null));
        center.add(slice("Losses", String.valueOf(totalLosses), totalLosses, LOSS, null));

        Table middle = new Table();
        middle.touchable = Touchable.childrenOnly;
        middle.add(new Pie(center, overall, true)).size(110f);

        Stack chart = new Stack();
        chart.add(new Pie(ring, overall));
        chart.add(middle);
        out.add(chart).size(220f).padRight(16f).top();

        int tw = totalWins, tl = totalLosses;
        out.table(legend -> {
            legend.top().left();
            legend.add("[accent]Overall[]  " + tw + "W / " + tl + "L  [lightgray](" + Strings.autoFixed(tw * 100f / overall, 0) + "% win rate)[]")
                    .left().padBottom(8f).row();

            for (int i = 0; i < heroes.size; i++) {
                PlayerCharUnitType h = heroes.get(i);
                Meta.HeroStat hs = Meta.hero(h.name);
                if (hs == null || hs.runs <= 0) continue;

                int wins = hs.wins;
                int losses = Math.max(0, hs.runs - hs.wins);
                Color c = paletteColor(i);
                Runnable click = heroJump(h);

                legend.table(row -> {
                    row.left();
                    row.image(Tex.whiteui).size(14f).padRight(2f).color(c);
                    row.image(Tex.whiteui).size(14f).padRight(8f).color(lossShade(c));
                    row.add(h.localizedName + " [lightgray]" + wins + "W / " + losses + "L  ("
                                    + Strings.autoFixed(wins * 100f / (wins + losses), 0) + "%)[]")
                            .color(click != null ? LINK : Color.white).left();
                    if (click != null) {
                        row.touchable = Touchable.enabled;
                        row.clicked(click);
                    }
                }).left().padBottom(3f).row();
            }
        }).top().left();
    }

    static void damagePie(Table out) {
        ObjectFloatMap<String> sums = new ObjectFloatMap<>();
        Seq<Meta.RunRecord> hist = Meta.data().history;
        for (int i = 0; i < hist.size; i++) {
            Meta.RunRecord rec = hist.get(i);
            sums.put(rec.hero, sums.get(rec.hero, 0f) + rec.damageDealt);
        }

        Seq<Slice> list = new Seq<>();
        Seq<PlayerCharUnitType> heroes = PlayerCharUnitType.selectable;
        for (int i = 0; i < heroes.size; i++) {
            PlayerCharUnitType h = heroes.get(i);
            float dmg = sums.get(h.name, 0f);
            if (dmg <= 0f) continue;
            list.add(slice(h.localizedName, fmt(dmg), dmg, paletteColor(i), heroJump(h)));
        }

        out.add("Total damage dealt per hero across finished runs.").color(Color.gray).left().row();
        Table inner = new Table();
        inner.left().top();
        pieWithLegend(inner, list, true);
        out.add(inner).left();
    }

    static void deathPie(Table out) {
        Seq<Death> deaths = new Seq<>();
        for (ObjectMap.Entry<String, Meta.EnemyStat> e : Meta.data().enemies) {
            if (e.value.deaths <= 0) continue;
            Death d = new Death();
            d.name = e.key;
            d.count = e.value.deaths;
            deaths.add(d);
        }
        deaths.sort((a, b) -> Integer.compare(b.count, a.count));

        Seq<Slice> list = new Seq<>();
        int top = Math.min(6, deaths.size);
        for (int i = 0; i < top; i++) {
            Death d = deaths.get(i);
            UnitType u = Vars.content.unit(d.name);
            String label = u == null ? d.name : u.localizedName;
            if (i == 0) label += " [scarlet](arch enemy)[]";
            Runnable click = u == null ? null : () -> jump("enemies", d.name);
            list.add(slice(label, String.valueOf(d.count), d.count, paletteColor(i), click));
        }

        int rest = 0;
        for (int i = top; i < deaths.size; i++) rest += deaths.get(i).count;
        if (rest > 0) list.add(slice("Other", String.valueOf(rest), rest, OTHER, null));

        out.add("Enemies that ended your runs.").color(Color.gray).left().row();
        Table inner = new Table();
        inner.left().top();
        pieWithLegend(inner, list, true);
        out.add(inner).left();
    }

    static void title(Table t, String text) {
        t.add("[accent]" + text + "[]").style(Styles.outlineLabel).left().padBottom(6f).row();
    }

    static void section(Table t, String text) {
        t.add("[accent]" + text + "[]").left().padTop(8f).padBottom(2f).row();
    }

    static void desc(Table t, String text) {
        if (text == null || text.isEmpty()) return;
        t.add(text).color(Color.lightGray).wrap().width(380f).left().padBottom(6f).row();
    }

    static void line(Table t, String label, String value) {
        t.add("[lightgray]" + label + ":[] " + value).left().row();
    }

    static void pct(Table t, String label, float mul) {
        if (Math.abs(mul - 1f) < 0.001f) return;
        line(t, label, (mul > 1f ? "+" : "") + Strings.autoFixed((mul - 1f) * 100f, 0) + "%");
    }

    static String signed(float v) {
        return (v > 0f ? "+" : "") + Strings.autoFixed(v, 2);
    }

    static String fmt(float v) {
        return Strings.autoFixed(v, 0);
    }

    static String kindLabel(RelicType r) {
        if (r instanceof GearType) return "Gear";
        return switch (r.slotKind) {
            case passive -> "Passive";
            case ability -> "Ability";
            case weapon -> "Weapon";
            case gear -> "Gear";
        };
    }

    static String typeLabel(RelicType r) {
        return switch (r.slotKind) {
            case passive -> switch (r.bonusApplyTo) {
                case primary -> "Primary";
                case secondary -> "Secondary";
                case utility -> "Utility";
                case special -> "Special";
                case gear -> "Gear";
                case allMain -> "All Abilities";
                case ALL -> "All Slots";
            };
            case ability -> switch (r.equipApplyto) {
                case primary -> "Primary";
                case secondary -> "Secondary";
                case utility -> "Utility";
                case special -> "Special";
                default -> null;
            };
            case weapon, gear -> null;
        };
    }

    static String rarityLabel(int rarity) {
        return Core.bundle.get("relic.rarity." + Math.max(1, rarity), "Rarity " + rarity);
    }

    static String heroName(String name) {
        UnitType hero = Vars.content.unit(name);
        return hero == null ? name : hero.localizedName;
    }

    static String formatTime(float ticks) {
        int sec = (int) (ticks / 60f);
        int h = sec / 3600;
        int m = (sec % 3600) / 60;
        int s = sec % 60;
        String ss = (s < 10 ? "0" : "") + s;
        if (h > 0) return h + ":" + (m < 10 ? "0" : "") + m + ":" + ss;
        return m + ":" + ss;
    }
}
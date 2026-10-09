package riskod.world.ui;

import arc.Core;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ui.dialogs.BaseDialog;

/**
 * Scrollable text window for debug output that would otherwise clutter the chat. Lines may use color markup.
 */
public class InfoUi {

    public static BaseDialog show(String title, Seq<String> lines) {
        if (Vars.headless || Vars.ui == null) return null;

        BaseDialog dialog = new BaseDialog(title);
        dialog.addCloseButton();
        float width = 1600;
        dialog.cont.pane(t -> {
            t.top().left();
            for (String line : lines) {
                if (line.isEmpty()) t.add().height(8f).row();
                else t.add(line).wrap().width(width).left().padBottom(2f).row();
            }
        }).grow();
        dialog.show();
        return dialog;
    }
}
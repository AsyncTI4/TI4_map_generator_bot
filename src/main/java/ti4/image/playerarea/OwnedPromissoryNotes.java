package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;
import ti4.model.Source.ComponentSource;

final class OwnedPromissoryNotes {

    private static final int CARD_STEP = 48;
    private static final Comparator<String> BY_NAME = (id1, id2) -> Mapper.getPromissoryNote(id1)
            .getName()
            .compareToIgnoreCase(Mapper.getPromissoryNote(id2).getName());

    private OwnedPromissoryNotes() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        if (player.getPromissoryNotesOwned().isEmpty()) {
            return x;
        }
        ctx.g2().setStroke(Strokes.STROKE_2);
        List<String> ownedPNs = new ArrayList<>(player.getPromissoryNotesOwned());
        ownedPNs.sort(BY_NAME);

        int deltaX = 0;
        boolean addedPNs = false;
        for (String pnID : ownedPNs) {
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnID);
            if (!ctx.game().isShowOwnedPNsInPlayerArea()
                    && promissoryNote.getFaction().isEmpty()
                    && !promissoryNote.getPlayImmediately()) {
                continue;
            }
            drawNote(ctx, pnID, promissoryNote, x + deltaX, y);
            deltaX += CARD_STEP;
            addedPNs = true;
        }
        return x + deltaX + (addedPNs ? 20 : 0);
    }

    private static void drawNote(PlayerAreaContext ctx, String pnID, PromissoryNoteModel promissoryNote, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        String light = promissoryNote.getSource() == ComponentSource.promises_promises
                ? "pa_promissory_light_pp.png"
                : "pa_promissory_light.png";
        PaImage.drawScaled(g2, x + 1, y + 1, light, 38, 28);
        if (game.isFrankenGame() && promissoryNote.getFaction().isPresent()) {
            FactionIcons.drawFaction(g2, promissoryNote.getFaction().get(), x - 1, y + 108, 42, 42);
        }
        Player holder = !game.isFowMode() && promissoryNote.getPlayArea() ? holderOf(game, pnID) : null;
        g2.setColor(holder != null ? Color.GRAY : Color.WHITE);

        if ("dspntnel".equals(pnID)) {
            g2.setFont(Fonts.ti(16));
            VerticalText.drawOneOrTwoLines(g2, "Plots Within Plots", x + 9, y + 144, 150);
        } else if (promissoryNote.getShrinkName()) {
            g2.setFont(Fonts.ti(16));
            VerticalText.drawOneOrTwoLines(g2, promissoryNote.getShortName(), x + 9, y + 144, 120);
        } else {
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, promissoryNote.getShortName(), x + 7, y + 144, 120);
        }
        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, promissoryNote);
        FactionIcons.drawFaded(g2, holder, x - 1, y + 25, 42, 42, 0.5f);
    }

    private static Player holderOf(Game game, String pnID) {
        for (Player player : game.getRealPlayers()) {
            if (player.getPromissoryNotesInPlayArea().contains(pnID)) {
                return player;
            }
        }
        return null;
    }
}

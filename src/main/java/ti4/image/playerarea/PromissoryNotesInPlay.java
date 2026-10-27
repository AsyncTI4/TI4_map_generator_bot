package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.PlanetModel;
import ti4.model.PromissoryNoteModel;

final class PromissoryNotesInPlay {

    private static final int CARD_STEP = 48;
    private static final String HIDDEN_NAME = "Promissory Note";
    private static final String HIDDEN_CARD_TEXT = "Hidden\nNote";
    private static final String HIDDEN_OVERLAY_TEXT = "Held from a player you cannot see.";

    private PromissoryNotesInPlay() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        if (player.getPromissoryNotesInPlayArea().isEmpty()) {
            return x;
        }
        ctx.g2().setStroke(Strokes.STROKE_2);
        int deltaX = 0;
        for (String pn : player.getPromissoryNotesInPlayArea()) {
            if (drawNote(ctx, player, pn, x + deltaX, y)) {
                deltaX += CARD_STEP;
            }
        }
        return x + deltaX + 20;
    }

    private static boolean drawNote(PlayerAreaContext ctx, Player player, String pn, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        g2.setColor(Color.WHITE);

        Player owner = game.getPNOwner(pn);
        if (owner == null) {
            String error = game.getName() + " " + player.getUserName()
                    + "  `GenerateMap.pnInfo` is trying to display a Promissory Note without an owner - possibly an eliminated player: "
                    + pn;
            BotLogger.warning(new LogOrigin(player), error);
            return false;
        }
        PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
        if (promissoryNote == null) return false;

        boolean hidden = ctx.hidesStatsOf(owner);
        boolean revealsFaction =
                pn.endsWith("_an") || promissoryNote.getFaction().isPresent();
        if (hidden && revealsFaction) {
            CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, HIDDEN_NAME, HIDDEN_OVERLAY_TEXT);
        } else if (pn.endsWith("_an")) {
            LeaderModel allianceCommander = allianceCommanderModel(owner);
            if (allianceCommander != null) {
                CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, allianceCommander);
            }
        } else {
            CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, promissoryNote);
        }

        boolean commanderUnlocked = drawOwnerColour(ctx, player, owner, promissoryNote, hidden, x, y);

        g2.setColor(Color.WHITE);
        if (pn.endsWith("_an") && !commanderUnlocked) {
            g2.setColor(Color.GRAY);
        }
        if (hidden && revealsFaction) {
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, HIDDEN_CARD_TEXT, x + 7, y + 4, 120, true);
        } else {
            writeName(g2, promissoryNote, x + 9, y + 4, findAttachedPlanet(game, promissoryNote));
        }
        return true;
    }

    private static LeaderModel allianceCommanderModel(Player owner) {
        return owner.getLeader(Constants.COMMANDER)
                .flatMap(Leader::getLeaderModel)
                .orElse(null);
    }

    private static boolean drawOwnerColour(
            PlayerAreaContext ctx,
            Player player,
            Player owner,
            PromissoryNoteModel promissoryNote,
            boolean hidden,
            int x,
            int y) {
        Graphics2D g2 = ctx.g2();
        for (Player other : player.getOtherRealPlayers()) {
            String playerColor = other.getColor();
            String playerFaction = other.getFaction();
            if (playerColor != null && playerColor.equals(owner.getColor())
                    || playerFaction != null && playerFaction.equals(owner.getFaction())) {
                String colorID = hidden ? Mapper.getColorID("gray") : Mapper.getColorID(playerColor);
                PaImage.draw(g2, x, y, "pa_pn_color_" + colorID + ".png");
                if (!hidden) {
                    if (ctx.game().isFrankenGame()) {
                        FactionIcons.drawFaction(g2, promissoryNote.getFaction().orElse(""), x - 1, y + 86, 42, 42);
                    }
                    FactionIcons.draw(g2, owner, x - 1, y + 108, 42, 42);
                }
                Leader commander = other.unsafeGetLeader(Constants.COMMANDER);
                return commander != null && !commander.isLocked();
            }
        }
        return false;
    }

    private static void writeName(Graphics2D g2, PromissoryNoteModel model, int x, int y, PlanetModel attached) {
        String text = model.getShortName();
        if (attached != null) text += "\n@" + attached.getShortNamePNAttach();

        if (model.getShrinkName()) {
            g2.setFont(Fonts.ti(16));
            VerticalText.drawOneOrTwoLines(g2, text, x, y, 120, true);
        } else {
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, text, x - 2, y, 120, true);
        }
    }

    private static PlanetModel findAttachedPlanet(Game game, PromissoryNoteModel model) {
        if (model.getAttachment().isEmpty() || model.getAttachment().get().isBlank()) return null;

        String tokenID = model.getAttachment().get();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(tokenID))) {
                    return Mapper.getPlanet(unitHolder.getName());
                }
            }
        }
        return null;
    }
}

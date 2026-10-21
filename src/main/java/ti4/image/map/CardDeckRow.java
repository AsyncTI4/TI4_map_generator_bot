package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class CardDeckRow {

    private static final int CARD_WIDTH = 60;
    private static final int CARD_HEIGHT = 90;
    private static final int HORIZONTAL_SPACING = CARD_WIDTH + 15;
    private static final int TYRANT_SPACING = 200;

    private record DeckBadge(String cardBack, String count, String title, String overlayText) {}

    private CardDeckRow() {}

    static void draw(RenderContext ctx, Graphics2D g2, int x, int y) {
        Game game = ctx.game();
        if (game.isFowMode()) return;

        int textY = y + CARD_HEIGHT - 10;
        g2.setFont(Fonts.ti(24));

        for (DeckBadge badge : badges(game)) {
            PaImage.drawScaled(g2, x, y, badge.cardBack(), CARD_WIDTH, CARD_HEIGHT);
            OutlinedText.draw(
                    g2,
                    badge.count(),
                    x + CARD_WIDTH / 2,
                    textY,
                    Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Bottom,
                    Strokes.STROKE_2,
                    Color.BLACK);
            ctx.overlays().add(badge.title(), badge.overlayText(), x, y, CARD_WIDTH, CARD_HEIGHT);
            x += HORIZONTAL_SPACING;
        }

        String tyrantFile = unclaimedTyrantFile(game);
        if (tyrantFile != null) {
            BufferedImage bufferedImage = ImageHelper.read(tyrantFile);
            g2.drawImage(bufferedImage, x + 15, y - 13, null);
        }
    }

    static int width(Game game) {
        if (game.isFowMode()) return 0;
        int width = badges(game).size() * HORIZONTAL_SPACING;
        return unclaimedTyrantFile(game) == null ? width - (HORIZONTAL_SPACING - CARD_WIDTH) : width + TYRANT_SPACING;
    }

    @Nullable
    private static String unclaimedTyrantFile(Game game) {
        if (!game.isTwilightsFallMode() || !game.getTyrantUserID().isEmpty()) {
            return null;
        }
        return ImageResources.token(Mapper.getTokenID(Constants.TYRANT));
    }

    private static List<DeckBadge> badges(Game game) {
        boolean twilightsFall = game.isTwilightsFallMode();
        List<DeckBadge> badges = new ArrayList<>();
        badges.add(new DeckBadge(
                "cardback_secret.jpg",
                Integer.toString(game.getSecretObjectiveDeckSize()),
                "Secret Objective Deck",
                game.getSecretObjectiveDeckSize() + "/" + game.getSecretObjectiveFullDeckSize()
                        + " cards in the deck"));
        badges.add(new DeckBadge(
                twilightsFall ? "cardback_action_tf.jpg" : "cardback_action.jpg",
                Integer.toString(game.getActionCards().size()),
                "Action Card Deck",
                game.getActionCards().size() + "/" + game.getActionCardFullDeckSize() + " cards in the deck"));
        badges.add(new DeckBadge(
                "cardback_cultural.jpg",
                Integer.toString(game.getExploreDeck("cultural").size()),
                "Cultural Explore Deck",
                game.getCulturalExploreDeckSize() + "/" + game.getCulturalExploreFullDeckSize() + " in the deck \n"
                        + game.getCulturalExploreDiscardSize() + " cards in the discard pile"));
        badges.add(new DeckBadge(
                "cardback_industrial.jpg",
                Integer.toString(game.getExploreDeck("industrial").size()),
                "Industrial Explore Deck",
                game.getIndustrialExploreDeckSize() + "/" + game.getIndustrialExploreFullDeckSize()
                        + " in the deck \n" + game.getIndustrialExploreDiscardSize()
                        + " cards in the discard pile"));
        badges.add(new DeckBadge(
                "cardback_hazardous.jpg",
                Integer.toString(game.getExploreDeck("hazardous").size()),
                "Hazardous Explore Deck",
                game.getHazardousExploreDeckSize() + "/" + game.getHazardousExploreFullDeckSize() + " in the deck \n"
                        + game.getHazardousExploreDiscardSize() + " cards in the discard pile"));
        badges.add(new DeckBadge(
                "cardback_frontier.jpg",
                Integer.toString(game.getExploreDeck("frontier").size()),
                "Frontier Explore Deck",
                game.getFrontierExploreDeckSize() + "/" + game.getFrontierExploreFullDeckSize() + " in the deck \n"
                        + game.getFrontierExploreDiscardSize() + " cards in the discard pile"));
        badges.add(new DeckBadge(
                "cardback_relic.jpg",
                Integer.toString(game.getRelicDeckSize()),
                "Relic Deck",
                game.getRelicDeckSize() + "/" + game.getRelicFullDeckSize() + " cards in the deck"));
        badges.add(new DeckBadge(
                twilightsFall ? "cardback_edict.jpg" : "cardback_agenda.png",
                Integer.toString(game.getAgendaDeckSize()),
                "Agenda Deck",
                game.getAgendaDeckSize() + "/" + game.getAgendaFullDeckSize() + " cards in the deck"));
        if (twilightsFall) {
            badges.add(spliceBadge(
                    "cardback_tf_ability.jpg",
                    "Ability Splice Deck",
                    game.getAbilitySpliceDeck(false).size(),
                    Mapper.getDeck(game.getAbilitySpliceDeckID()).getCardCount()));
            badges.add(spliceBadge(
                    "cardback_unit_upgrade.jpg",
                    "Unit Upgrade Splice Deck",
                    game.getUnitSpliceDeck(false).size(),
                    Mapper.getDeck(game.getUnitSpliceDeckID()).getCardCount()));
            badges.add(spliceBadge(
                    "cardback_genome.jpg",
                    "Genome Splice Deck",
                    game.getGenomeSpliceDeck(false).size(),
                    Mapper.getDeck(game.getGenomeSpliceDeckID()).getCardCount()));
            badges.add(spliceBadge(
                    "cardback_paradigm.jpg",
                    "Paradigm Deck",
                    game.getParadigmSpliceDeck(false).size(),
                    Mapper.getDeck(game.getParadigmSpliceDeckID()).getCardCount()));
        }
        return badges;
    }

    private static DeckBadge spliceBadge(String cardBack, String title, int cardCount, int fullDeck) {
        return new DeckBadge(
                cardBack, Integer.toString(cardCount), title, cardCount + "/" + fullDeck + " cards in the deck");
    }
}

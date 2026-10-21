package ti4.image.map;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.Strokes;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.EventModel;
import ti4.model.Mapper;

final class LawsAndEventsSection {

    private static final int LEFT_MARGIN = 5;
    private static final int LAW_ROW_GAP = 8;
    private static final int EVENT_ROW_GAP = 2;
    private static final int BLOCK_BOTTOM_MARGIN = 3;
    private static final int CARDS_PER_ROW = 2;
    private static final int ICON_INSET = 2;

    private record LawCard(
            Map.Entry<String, Integer> entry,
            String optionalText,
            @Nullable AgendaModel agendaModel,
            String title,
            List<String> bodyLines) {

        int height() {
            return CardBox.heightFor(bodyLines);
        }
    }

    private record EventCard(String title, List<String> bodyLines) {

        int height() {
            return CardBox.heightFor(bodyLines);
        }
    }

    private LawsAndEventsSection() {}

    static int heightOfLaws(Game game) {
        List<LawCard> cards = lawCards(game, CardBox.bodyMetrics());
        return blockHeight(rowHeights(cards.stream().map(LawCard::height).toList()), LAW_ROW_GAP);
    }

    static int heightOfEvents(Game game) {
        List<EventCard> cards = eventCards(game, CardBox.bodyMetrics());
        return blockHeight(rowHeights(cards.stream().map(EventCard::height).toList()), EVENT_ROW_GAP);
    }

    static List<Integer> rowHeights(List<Integer> cardHeights) {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < cardHeights.size(); i += CARDS_PER_ROW) {
            int rowHeight = 0;
            for (int j = i; j < Math.min(i + CARDS_PER_ROW, cardHeights.size()); j++) {
                rowHeight = Math.max(rowHeight, cardHeights.get(j));
            }
            rows.add(rowHeight);
        }
        return rows;
    }

    static int blockHeight(List<Integer> rowHeights, int rowGap) {
        int height = BLOCK_BOTTOM_MARGIN;
        for (int rowHeight : rowHeights) {
            height += rowHeight + rowGap;
        }
        return height;
    }

    static int drawLaws(RenderContext ctx, Graphics2D g2, int left, int y) {
        if (ctx.basicType() == DisplayType.map) {
            return y;
        }
        g2.setStroke(Strokes.STROKE_3);

        List<LawCard> cards = lawCards(ctx.game(), CardBox.bodyMetrics(g2));
        List<Integer> rowHeights =
                rowHeights(cards.stream().map(LawCard::height).toList());
        int rowY = y;
        for (int row = 0; row < rowHeights.size(); row++) {
            int rowHeight = rowHeights.get(row);
            for (int column = 0; column < CARDS_PER_ROW; column++) {
                int index = row * CARDS_PER_ROW + column;
                if (index < cards.size()) {
                    drawLaw(ctx, g2, cards.get(index), cardX(left, column), rowY, rowHeight);
                }
            }
            rowY += rowHeight + LAW_ROW_GAP;
        }
        return rowY + BLOCK_BOTTOM_MARGIN;
    }

    static int drawEvents(RenderContext ctx, Graphics2D g2, int left, int y) {
        g2.setStroke(Strokes.STROKE_3);

        List<EventCard> cards = eventCards(ctx.game(), CardBox.bodyMetrics(g2));
        List<Integer> rowHeights =
                rowHeights(cards.stream().map(EventCard::height).toList());
        int rowY = y;
        for (int row = 0; row < rowHeights.size(); row++) {
            int rowHeight = rowHeights.get(row);
            for (int column = 0; column < CARDS_PER_ROW; column++) {
                int index = row * CARDS_PER_ROW + column;
                if (index < cards.size()) {
                    drawEvent(g2, cards.get(index), cardX(left, column), rowY, rowHeight);
                }
            }
            rowY += rowHeight + EVENT_ROW_GAP;
        }
        return rowY + BLOCK_BOTTOM_MARGIN;
    }

    private static int cardX(int left, int column) {
        return LEFT_MARGIN + left + column * CardBox.COLUMN_STEP;
    }

    private static List<LawCard> lawCards(Game game, FontMetrics bodyMetrics) {
        List<LawCard> cards = new ArrayList<>();
        Map<String, String> lawsInfo = game.getLawsInfo();
        for (Map.Entry<String, Integer> lawEntry : game.getLaws().entrySet()) {
            String lawID = lawEntry.getKey();
            String optionalText = lawsInfo.get(lawID);
            String agendaTitle = Mapper.getAgendaTitle(lawID);
            if (agendaTitle == null) {
                agendaTitle = Mapper.getAgendaJustNames().get(lawID);
            }
            if (agendaTitle == null) {
                BotLogger.error("Unable to find law with id '" + lawID + "' during map generation. Skipping.");
                continue;
            }
            if (optionalText != null
                    && !optionalText.isEmpty()
                    && game.getPlayerFromColorOrFaction(optionalText) == null) {
                agendaTitle += "   [" + optionalText + "]";
            }
            String agendaText = Mapper.getAgendaText(lawID);
            if (agendaText == null) {
                agendaText = Mapper.getAgendaForOnly(lawID);
            }
            agendaText = "(" + lawEntry.getValue() + ") " + agendaText;
            cards.add(new LawCard(
                    lawEntry,
                    optionalText,
                    Mapper.getAgenda(lawID),
                    agendaTitle,
                    CardBox.bodyLines(bodyMetrics, agendaText, true)));
        }
        return cards;
    }

    private static List<EventCard> eventCards(Game game, FontMetrics bodyMetrics) {
        List<EventCard> cards = new ArrayList<>();
        for (Map.Entry<String, Integer> event : game.getEventsInEffect().entrySet()) {
            EventModel eventModel = Mapper.getEvent(event.getKey());
            if (eventModel == null) {
                continue;
            }
            String body = "(" + event.getValue() + ") " + eventModel.getMapText();
            cards.add(new EventCard(eventModel.getName(), CardBox.bodyLines(bodyMetrics, body, true)));
        }
        return cards;
    }

    private static void drawLaw(RenderContext ctx, Graphics2D g2, LawCard card, int x, int y, int height) {
        CardBox.drawFrame(g2, x, y, height, ColorUtil.LawColor);
        if (card.agendaModel() != null) {
            ctx.overlays().add(card.agendaModel(), x, y, CardBox.WIDTH, height);
        }
        CardBox.drawText(g2, x, y, ColorUtil.LawColor, 33, card.title(), card.bodyLines());
        try {
            paintLawIcons(ctx, g2, card.entry(), card.optionalText(), x, y);
        } catch (Exception e) {
            BotLogger.error("Could not paint agenda icon", e);
        }
    }

    private static void drawEvent(Graphics2D g2, EventCard card, int x, int y, int height) {
        CardBox.drawFrame(g2, x, y, height, Color.BLUE);
        CardBox.drawText(g2, x, y, Color.WHITE, 30, card.title(), card.bodyLines());
        try {
            paintIcon(g2, "event.png", x, y);
        } catch (Exception e) {
            BotLogger.error("Could not paint event icon", e);
        }
    }

    private static void paintLawIcons(
            RenderContext ctx, Graphics2D g2, Map.Entry<String, Integer> lawEntry, String optionalText, int x, int y) {
        Game game = ctx.game();
        FogView fog = ctx.fog();
        String lawID = lawEntry.getKey();
        String agendaType = Mapper.getAgendaType(lawID);

        if (optionalText == null || optionalText.isEmpty() || game.getPlayerFromColorOrFaction(optionalText) == null) {
            paintIcon(g2, "agenda.png", x, y);
        } else if ("0".equals(agendaType)) {
            Player electedPlayer = null;
            boolean convertToGeneric = false;
            for (Player player : game.getPlayers().values()) {
                if (optionalText.equals(player.getFaction()) || optionalText.equals(player.getColor())) {
                    if (fog.shouldConvertToGeneric(player)) {
                        convertToGeneric = true;
                    }
                    electedPlayer = player;
                    break;
                }
            }
            if (convertToGeneric || electedPlayer == null) {
                paintIcon(g2, "agenda.png", x, y);
            } else {
                FactionIcons.draw(g2, electedPlayer, x + 2, y + 2, 95, 95);
            }
        }
        if (!game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue()).isEmpty()) {
            int tokenDeltaY = 0;
            int count = 0;
            for (String debtToken : game.getStoredValue("controlTokensOnAgenda" + lawEntry.getValue())
                    .split("_")) {

                boolean hideFactionIcon = fog.shouldConvertToGeneric(game.getPlayerFromColorOrFaction(debtToken));
                String controlID = hideFactionIcon ? Mapper.getControlID("gray") : Mapper.getControlID(debtToken);
                if (controlID.contains("null")) {
                    continue;
                }
                float scale = 0.80f;
                BufferedImage controlTokenImage = ImageHelper.readScaled(ImageResources.commandToken(controlID), scale);
                PlayerTokens.drawControlToken(
                        g2,
                        controlTokenImage,
                        game.getPlayerFromColorOrFaction(debtToken),
                        x + (count / 3) * 55,
                        y + tokenDeltaY - (count / 3) * 90,
                        hideFactionIcon,
                        scale);
                tokenDeltaY += 30;
                count += 1;
            }
        }
    }

    private static void paintIcon(Graphics2D g2, String factionFileName, int x, int y) {
        String factionFile = ImageResources.faction(factionFileName);
        if (factionFile != null) {
            BufferedImage bufferedImage = ImageHelper.read(factionFile);
            g2.drawImage(bufferedImage, x + ICON_INSET, y + ICON_INSET, null);
        }
    }
}

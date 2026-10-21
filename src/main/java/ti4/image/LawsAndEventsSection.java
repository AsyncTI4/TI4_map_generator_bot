package ti4.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DisplayType;
import ti4.logging.BotLogger;
import ti4.model.AgendaModel;
import ti4.model.EventModel;

final class LawsAndEventsSection {

    private static final int LEFT_MARGIN = 5;
    private static final int LAW_ROW_STEP = 118;
    private static final int EVENT_ROW_STEP = 112;
    private static final int ICON_INSET = 2;

    private LawsAndEventsSection() {}

    static int drawLaws(RenderContext ctx, Graphics2D g2, int left, int y) {
        if (ctx.basicType() == DisplayType.map) {
            return y;
        }
        Game game = ctx.game();
        int x = LEFT_MARGIN + left;
        g2.setStroke(Strokes.STROKE_3);

        Map<String, Integer> laws = game.getLaws();
        Map<String, String> lawsInfo = game.getLawsInfo();
        boolean secondColumn = false;
        for (Map.Entry<String, Integer> lawEntry : laws.entrySet()) {
            String lawID = lawEntry.getKey();
            String lawNumberID = "(" + lawEntry.getValue() + ") ";
            String optionalText = lawsInfo.get(lawID);
            AgendaModel agendaModel = Mapper.getAgenda(lawID);

            CardBox.drawFrame(g2, x, y, ColorUtil.LawColor);
            if (agendaModel != null) {
                ctx.overlays().add(agendaModel, x, y, CardBox.WIDTH, CardBox.HEIGHT);
            }
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
            agendaText = lawNumberID + agendaText;
            CardBox.drawText(g2, x, y, ColorUtil.LawColor, 33, agendaTitle, agendaText, true);
            try {
                paintLawIcons(ctx, g2, lawEntry, optionalText, x, y);
            } catch (Exception e) {
                BotLogger.error("Could not paint agenda icon", e);
            }

            if (!secondColumn) {
                secondColumn = true;
                x += CardBox.COLUMN_STEP;
            } else {
                secondColumn = false;
                y += LAW_ROW_STEP;
                x -= CardBox.COLUMN_STEP;
            }
        }
        return y + 3 + (secondColumn ? LAW_ROW_STEP : 0);
    }

    static int drawEvents(RenderContext ctx, Graphics2D g2, int left, int y) {
        Game game = ctx.game();
        int x = LEFT_MARGIN + left;
        g2.setStroke(Strokes.STROKE_3);

        Map<String, Integer> events = game.getEventsInEffect();
        boolean secondColumn = false;
        for (Map.Entry<String, Integer> event : events.entrySet()) {
            String eventNumberID = "(" + event.getValue() + ") ";

            CardBox.drawFrame(g2, x, y, Color.BLUE);

            EventModel eventModel = Mapper.getEvent(event.getKey());

            CardBox.drawText(
                    g2, x, y, Color.WHITE, 30, eventModel.getName(), eventNumberID + eventModel.getMapText(), true);
            try {
                paintIcon(g2, "event.png", x, y);
            } catch (Exception e) {
                BotLogger.error("Could not paint event icon", e);
            }

            if (!secondColumn) {
                secondColumn = true;
                x += CardBox.COLUMN_STEP;
            } else {
                secondColumn = false;
                y += EVENT_ROW_STEP;
                x = LEFT_MARGIN + left;
            }
        }
        return secondColumn ? y + 115 : y + 3;
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
                DrawingUtil.drawPlayerFactionIconImage(g2, electedPlayer, x + 2, y + 2, 95, 95);
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
                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
                DrawingUtil.drawControlToken(
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
        String factionFile = ResourceHelper.getInstance().getFactionFile(factionFileName);
        if (factionFile != null) {
            BufferedImage bufferedImage = ImageHelper.read(factionFile);
            g2.drawImage(bufferedImage, x + ICON_INSET, y + ICON_INSET, null);
        }
    }
}

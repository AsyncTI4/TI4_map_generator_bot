package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.Mapper;

public final class ScoredSecrets {

    private static final int TEXT_INDENT = 40;
    private static final int ROW_HEIGHT = 43;
    private static final int MARKER_OFFSET = 515;
    private static final float MARKER_SCALE = 0.55f;

    private ScoredSecrets() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_3);
        Map<String, String> secretObjectiveNames = Mapper.getSecretObjectivesJustNames();
        g2.setFont(Fonts.ti(26));
        g2.setColor(ColorUtil.Stage1RevealedColor);

        int x = slot.left() + TEXT_INDENT;
        if (player.isSearchWarrant()) {
            g2.setColor(Color.LIGHT_GRAY);
            y = drawRows(ctx, player, player.getSecrets(), secretObjectiveNames, Set.of(), x, y);
        }
        Map<String, Integer> secretsScored = scoredNotMadePublic(game, player);
        Set<String> scoredIds = new HashSet<>();
        player.getSecrets().keySet().forEach(id -> scoredIds.add(withoutExtraSuffix(id)));
        secretsScored.keySet().forEach(id -> scoredIds.add(withoutExtraSuffix(id)));
        g2.setColor(Color.RED);
        drawRows(ctx, player, secretsScored, secretObjectiveNames, scoredIds, x, y);
        return rowCount(game, player);
    }

    public static int rowCount(Game game, Player player) {
        int scored = scoredNotMadePublic(game, player).size();
        return player.isSearchWarrant() ? scored + player.getSecrets().size() : scored;
    }

    private static Map<String, Integer> scoredNotMadePublic(Game game, Player player) {
        Map<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            secretsScored.remove(withoutExtraSuffix(id));
        }
        return secretsScored;
    }

    private static String withoutExtraSuffix(String id) {
        return id.replace("extra1", "").replace("extra2", "");
    }

    private static int drawRows(
            PlayerAreaContext ctx,
            Player player,
            Map<String, Integer> secrets,
            Map<String, String> secretObjectiveNames,
            Set<String> scoredIds,
            int x,
            int y) {
        Graphics2D g2 = ctx.g2();
        for (Map.Entry<String, Integer> secret : secrets.entrySet()) {
            String key = secret.getKey();
            if (!secretObjectiveNames.containsKey(key) || secret.getValue() == null) {
                continue;
            }
            g2.drawString("(" + secret.getValue() + ") " + secretObjectiveNames.get(key), x, y + 23);
            if (scoredIds.contains(key)) {
                drawScoreMarker(ctx, player, x + MARKER_OFFSET, y);
            }
            CardRect.draw(ctx, x - 4, y - 5, 600, 38, Mapper.getSecretObjective(key));
            y += ROW_HEIGHT;
        }
        return y;
    }

    private static void drawScoreMarker(PlayerAreaContext ctx, Player scorer, int x, int y) {
        try {
            List<String> scoredBy = List.of(scorer.getUserID());
            for (Player player : ctx.game().getPlayers().values()) {
                if (!scoredBy.contains(player.getUserID())) {
                    continue;
                }
                boolean convertToGeneric = ctx.hidesStatsOf(player);
                String controlID =
                        convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());
                if (controlID.contains("null")) {
                    continue;
                }
                BufferedImage controlTokenImage =
                        ImageHelper.readScaled(ImageResources.commandToken(controlID), MARKER_SCALE);
                PlayerTokens.drawControlToken(
                        ctx.g2(), controlTokenImage, player, x, y, convertToGeneric, MARKER_SCALE);
            }
        } catch (Exception e) {
            BotLogger.error("Error drawing score control token markers", e);
        }
    }
}

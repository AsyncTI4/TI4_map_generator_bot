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
import ti4.helpers.Storage;
import ti4.image.ColorUtil;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.image.Strokes;
import ti4.logging.BotLogger;

final class ScoredSecrets {

    private static final int LEFT_MARGIN = 50;
    private static final int ROW_HEIGHT = 43;
    private static final int MARKER_OFFSET = 515;
    private static final float MARKER_SCALE = 0.55f;

    private ScoredSecrets() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_3);
        Map<String, String> secretObjectiveNames = Mapper.getSecretObjectivesJustNames();
        g2.setFont(Storage.getFont26());
        g2.setColor(ColorUtil.Stage1RevealedColor);

        int x = LEFT_MARGIN + slot.columnOffset();
        if (player.isSearchWarrant()) {
            g2.setColor(Color.LIGHT_GRAY);
            y = drawRows(ctx, player, player.getSecrets(), secretObjectiveNames, Set.of(), x, y);
        }
        Map<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            secretsScored.remove(withoutExtraSuffix(id));
        }
        Set<String> scoredIds = new HashSet<>();
        player.getSecrets().keySet().forEach(id -> scoredIds.add(withoutExtraSuffix(id)));
        secretsScored.keySet().forEach(id -> scoredIds.add(withoutExtraSuffix(id)));
        g2.setColor(Color.RED);
        drawRows(ctx, player, secretsScored, secretObjectiveNames, scoredIds, x, y);
        if (player.isSearchWarrant()) {
            return secretsScored.size() + player.getSecrets().size();
        }
        return secretsScored.size();
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
                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), MARKER_SCALE);
                DrawingUtil.drawControlToken(ctx.g2(), controlTokenImage, player, x, y, convertToGeneric, MARKER_SCALE);
            }
        } catch (Exception e) {
            BotLogger.error("Error drawing score control token markers", e);
        }
    }
}

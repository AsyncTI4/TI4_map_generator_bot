package ti4.image.map;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.game.Game;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;

final class ObjectivesSection {

    static final int SPACING_BETWEEN_OBJECTIVE_TYPES = 10;
    private static final int LEFT_MARGIN = 5;
    private static final int BOTTOM_MARGIN = 15;

    private ObjectivesSection() {}

    static int rowWidth(Game game, int scoreTokenSpacing) {
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        try {
            probe.setFont(Fonts.ti(26));
            List<Objective> stage1 = Objective.retrievePublic1(game);
            List<Objective> stage2 = new ArrayList<>(Objective.retrievePublic2(game));
            if (game.isOmegaPhaseMode()) {
                int splitSize = stage1.size() / 2;
                stage2.addAll(stage1.stream().skip(splitSize).toList());
                stage1 = stage1.stream().limit(splitSize).toList();
            }
            int rowWidth = 5 + columnWidth(game, probe, stage1, scoreTokenSpacing) + SPACING_BETWEEN_OBJECTIVE_TYPES;
            rowWidth += columnWidth(game, probe, stage2, scoreTokenSpacing);
            List<Objective> custom = Objective.retrieveCustom(game);
            if (!custom.isEmpty()) {
                rowWidth += SPACING_BETWEEN_OBJECTIVE_TYPES + columnWidth(game, probe, custom, scoreTokenSpacing);
            }
            return rowWidth + SPACING_BETWEEN_OBJECTIVE_TYPES;
        } finally {
            probe.dispose();
        }
    }

    static int draw(RenderContext ctx, Graphics2D g2, int left, int y) {
        Game game = ctx.game();
        int scoreTokenSpacing = ctx.scoreTokenSpacing();
        g2.setStroke(Strokes.STROKE_3);
        g2.setFont(Fonts.ti(26));

        int top = y;
        int x = LEFT_MARGIN + left;
        int maxY = y;

        List<Objective> objectives = Objective.retrievePublic1(game);
        List<Objective> secondHalfObjectives = null;
        if (game.isOmegaPhaseMode()) {
            int splitSize = objectives.size() / 2;
            secondHalfObjectives = objectives.stream().skip(splitSize).toList();
            objectives = objectives.stream().limit(splitSize).toList();
        }
        int maxTextWidth = ObjectiveBox.getMaxTextWidth(game, g2, objectives);
        int boxWidth = ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);

        y = drawColumn(ctx, g2, objectives, x, y, boxWidth, maxTextWidth);

        x += boxWidth + SPACING_BETWEEN_OBJECTIVE_TYPES;
        maxY = Math.max(y, maxY);
        y = top;

        objectives = Objective.retrievePublic2(game);
        if (game.isOmegaPhaseMode()) {
            objectives.addAll(secondHalfObjectives);
        }
        maxTextWidth = ObjectiveBox.getMaxTextWidth(game, g2, objectives);
        boxWidth = ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);
        y = drawColumn(ctx, g2, objectives, x, y, boxWidth, maxTextWidth);

        x += boxWidth + SPACING_BETWEEN_OBJECTIVE_TYPES;
        maxY = Math.max(y, maxY);
        y = top;

        objectives = Objective.retrieveCustom(game);
        maxTextWidth = ObjectiveBox.getMaxTextWidth(game, g2, objectives);
        boxWidth = ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);
        y = drawColumn(ctx, g2, objectives, x, y, boxWidth, maxTextWidth);

        maxY = Math.max(y, maxY);
        return maxY + BOTTOM_MARGIN;
    }

    private static int drawColumn(
            RenderContext ctx,
            Graphics2D g2,
            List<Objective> objectives,
            int x,
            int y,
            int boxWidth,
            int maxTextWidth) {
        for (Objective objective : objectives) {
            ObjectiveBox box = new ObjectiveBox(x, y, boxWidth, maxTextWidth, ctx.scoreTokenSpacing());
            box.display(ctx.game(), g2, ctx.fog(), ctx.overlays(), objective);
            y += ObjectiveBox.getVerticalSpacing();
        }
        return y;
    }

    private static int columnWidth(Game game, Graphics2D probe, List<Objective> objectives, int scoreTokenSpacing) {
        int maxTextWidth = ObjectiveBox.getMaxTextWidth(game, probe, objectives);
        return ObjectiveBox.getBoxWidth(game, maxTextWidth, scoreTokenSpacing);
    }
}

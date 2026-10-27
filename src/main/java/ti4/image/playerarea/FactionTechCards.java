package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;

final class FactionTechCards {

    private static final int CARD_STEP = 48;

    private FactionTechCards() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        List<String> techs = player.getNotResearchedFactionTechs();
        if (techs.isEmpty()) {
            return x;
        }
        ctx.g2().setStroke(Strokes.STROKE_2);
        int deltaX = 0;
        for (String tech : techs) {
            ctx.g2().setColor(Color.DARK_GRAY);
            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel == null || techModel.isUnitUpgrade()) {
                continue;
            }
            drawTech(ctx, tech, techModel, x + deltaX, y);
            deltaX += CARD_STEP;
        }
        return x + deltaX;
    }

    private static void drawTech(PlayerAreaContext ctx, String tech, TechnologyModel techModel, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        String techIcon = backgroundIcon(techModel);
        if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
            techIcon = game.getStoredValue("colorChange" + tech);
        }
        if (!techIcon.isEmpty()) {
            PaImage.draw(g2, x, y, "pa_tech_techicons_" + techIcon + "_exh.png");
        }
        if (techModel.getFaction().isPresent()) {
            String faction = techModel.getFaction().get();
            String icon = game.isLiberationC4Mode() && "ghost".equals(faction) ? "redcreuss" : faction;
            FactionIcons.drawFactionFaded(g2, icon, x + 1, y + 108, 42, 42, 0.5f);
        }
        TechCards.drawName(g2, techModel, x, y);
        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, techModel);
    }

    private static String backgroundIcon(TechnologyModel techModel) {
        int types = 0;
        String techIcon = "";
        if (techModel.isPropulsionTech()) {
            types++;
            techIcon += "propulsion";
        }
        if (techModel.isCyberneticTech()) {
            types++;
            techIcon += "cybernetic";
        }
        if (techModel.isBioticTech()) {
            if (types < 2) techIcon += "biotic";
            types++;
        }
        if (techModel.isWarfareTech()) {
            if (types < 2) techIcon += "warfare";
        }
        return techIcon;
    }
}

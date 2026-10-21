package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.ResourceHelper;
import ti4.game.Player;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.image.PaImage;
import ti4.model.TechnologyModel;

final class StasisCards {

    private static final int CARD_STEP = 48;

    private StasisCards() {}

    static int drawGenSynthesis(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        int genSynthesisInfantry = player.getStasisInfantry();
        String infantryII = infantryIITech(player.getTechs());
        if (infantryII == null) {
            return xDeltaFromRight;
        }
        TechnologyModel infantryIITech = Mapper.getTech(infantryII);

        xDeltaFromRight += CARD_STEP;
        int x = slot.rightEdge() - xDeltaFromRight;
        PaImage.draw(ctx.g2(), x + 4, y, "pa_tech_techname_stasiscapsule.png");
        drawCount(ctx.g2(), genSynthesisInfantry, x, y);
        CardRect.draw(
                ctx,
                x + 2,
                y - 2,
                CardRect.WIDTH,
                CardRect.HEIGHT,
                "Gen Synthesis - " + infantryIITech.getName(),
                genSynthesisInfantry + " infantry to revive.");
        return xDeltaFromRight;
    }

    static int drawCorsairsCove(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        int stasisFighters = player.getStasisFighters();
        if (stasisFighters < 1) {
            return xDeltaFromRight;
        }

        xDeltaFromRight += CARD_STEP;
        int x = slot.rightEdge() - xDeltaFromRight;
        Graphics2D g2 = ctx.g2();
        BufferedImage fighter = ImageHelper.readScaled(
                ResourceHelper.getInstance().getUnitFile(Mapper.getUnitKey("ff", player.getColor())), 36, 36);
        g2.drawImage(fighter, x + 4, y + 10, null);
        g2.setColor(Color.WHITE);
        g2.setFont(Storage.getFont16());
        DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, "Corsairs' Cove", x + 7, y + 116, 100);
        drawCount(g2, stasisFighters, x, y);
        CardRect.draw(
                ctx,
                x + 2,
                y - 2,
                CardRect.WIDTH,
                CardRect.HEIGHT,
                "Corsairs' Cove",
                stasisFighters + " fighters in stasis.");
        return xDeltaFromRight;
    }

    private static void drawCount(Graphics2D g2, int count, int x, int y) {
        boolean shrinkText = count >= 20;
        DrawingUtil.drawCenteredString(
                g2,
                Integer.toString(count),
                new Rectangle(x + (shrinkText ? 2 : 4), y + (shrinkText ? 123 : 121), 42, 30),
                shrinkText ? Storage.getFont30() : Storage.getFont36());
    }

    static String infantryIITech(List<String> techs) {
        if (techs == null) {
            return null;
        }
        for (String tech : techs) {
            TechnologyModel techInformation = Mapper.getTech(tech);
            if (techInformation == null) {
                continue;
            }
            if ("inf2".equals(techInformation.getBaseUpgrade().orElse("")) || "inf2".equals(tech)) {
                return tech;
            }
        }
        return null;
    }
}

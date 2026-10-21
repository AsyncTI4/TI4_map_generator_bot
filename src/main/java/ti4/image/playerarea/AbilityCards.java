package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.AbilityModel;
import ti4.model.Mapper;

final class AbilityCards {

    private static final int CARD_STEP = 48;
    private static final Map<String, String> CARD_IMAGES = Map.of(
            "grace", "pa_ds_edyn_grace",
            "policy_the_people_connect", "pa_ds_olra_policy_cpos",
            "policy_the_people_control", "pa_ds_olra_policy_cneg",
            "policy_the_environment_preserve", "pa_ds_olra_policy_hpos",
            "policy_the_environment_plunder", "pa_ds_olra_policy_hneg",
            "policy_the_economy_empower", "pa_ds_olra_policy_ipos",
            "policy_the_economy_exploit", "pa_ds_olra_policy_ineg");
    private static final Comparator<String> BY_NAME = (ability1, ability2) -> Mapper.getAbility(ability1)
            .getName()
            .compareToIgnoreCase(Mapper.getAbility(ability2).getName());

    private AbilityCards() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        if (player.getAbilities().isEmpty()) {
            return x;
        }
        ctx.g2().setStroke(Strokes.STROKE_2);
        List<String> allAbilities = new ArrayList<>(player.getAbilities());
        allAbilities.sort(BY_NAME);

        int deltaX = 0;
        boolean addedAbilities = false;
        for (String abilityID : allAbilities) {
            if (drawAbility(ctx, player, abilityID, x + deltaX, y)) {
                deltaX += CARD_STEP;
                addedAbilities = true;
            }
        }
        return x + deltaX + (addedAbilities ? 20 : 0);
    }

    private static boolean drawAbility(PlayerAreaContext ctx, Player player, String abilityID, int x, int y) {
        Graphics2D g2 = ctx.g2();
        boolean isExhaustedLocked = player.getExhaustedAbilities().contains(abilityID);
        g2.setColor(isExhaustedLocked ? Color.GRAY : Color.WHITE);

        AbilityModel abilityModel = Mapper.getAbility(abilityID);
        if (abilityModel == null) {
            BotLogger.error("Ability null: " + abilityID);
            return false;
        }
        String cardImage = CARD_IMAGES.get(abilityID);
        if (cardImage != null) {
            String fileName = cardImage + (isExhaustedLocked ? "_exh" : "_rdy") + ".png";
            BufferedImage image = ImageHelper.read(ImageResources.playerArea(fileName));
            g2.drawImage(image, x, y, null);
        } else {
            String iconFaction = ctx.game().isLiberationC4Mode() && "ghost".equals(abilityModel.getFaction())
                    ? "redcreuss"
                    : abilityModel.getFaction();
            FactionIcons.drawFaction(g2, iconFaction, x - 1, y, 42, 42);
            if (abilityModel.getShrinkName()) {
                g2.setFont(Fonts.ti(16));
                VerticalText.drawOneOrTwoLines(g2, abilityModel.getShortName(), x + 9, y + 144, 130);
            } else {
                g2.setFont(Fonts.ti(18));
                VerticalText.drawOneOrTwoLines(g2, abilityModel.getShortName(), x + 7, y + 144, 130);
            }
        }
        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, abilityModel);
        return true;
    }
}

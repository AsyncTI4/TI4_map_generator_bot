package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.List;
import ti4.game.Player;
import ti4.helpers.ButtonHelperAbilities;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;

final class HomebrewStripCards {

    private static final String BLUEPRINT_PREFIX = "pa_ds_bent_blueprint_";
    private static final int BLUEPRINT_STEP = 48;
    private static final int OMEN_STEP = 56;

    private HomebrewStripCards() {}

    static int drawBentorBlueprints(PlayerAreaContext ctx, Player player, int x, int y) {
        if (!player.hasAbility("ancient_blueprints")) {
            return x;
        }
        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_2);
        g2.setColor(Color.WHITE);
        int deltaX = 0;
        boolean hasFoundAny = false;
        List<Boolean> found = List.of(
                player.isHasFoundCulFrag(),
                player.isHasFoundHazFrag(),
                player.isHasFoundIndFrag(),
                player.isHasFoundUnkFrag());
        List<String> fragments = List.of("crf", "hrf", "irf", "urf");
        for (int i = 0; i < fragments.size(); i++) {
            if (found.get(i)) {
                g2.drawRect(x + deltaX - 2, y - 2, 44, 152);
                PaImage.draw(g2, x + deltaX, y, BLUEPRINT_PREFIX + fragments.get(i) + ".png");
                hasFoundAny = true;
                deltaX += BLUEPRINT_STEP;
            }
        }
        return x + deltaX + (hasFoundAny ? 20 : 0);
    }

    static int drawOmenDice(PlayerAreaContext ctx, Player player, int x, int y) {
        if (!player.hasAbility("divination")
                || ButtonHelperAbilities.getAllOmenDie(ctx.game()).isEmpty()) {
            return x;
        }
        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_2);
        int deltaX = 0;
        for (int i = 0; i < ButtonHelperAbilities.getAllOmenDie(ctx.game()).size(); i++) {
            String omen = "pa_ds_myko_omen_"
                    + ButtonHelperAbilities.getAllOmenDie(ctx.game()).get(i) + ".png";
            omen = omen.replace("10", "0");
            g2.drawRect(x + deltaX - 2, y - 2, 52, 152);
            PaImage.draw(g2, x + deltaX, y, omen);
            deltaX += OMEN_STEP;
        }
        return x + deltaX + 20;
    }
}

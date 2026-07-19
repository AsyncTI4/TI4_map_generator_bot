package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ponthous;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;

@UtilityClass
public class PonthousAbilityHandler {
    private static final String USE_PONTHOUS = "usePonthousLegendaryAbility_";
    private static final String PONTHOUS = "ponthous";
    private static final String PONTHOUS_BOTH = "attachment_ponthousboth.png";
    private static final String PONTHOUS_RES = "attachment_positiveres3.png";
    private static final String PONTHOUS_INF = "attachment_positiveinf3.png";
    private static final String LEGACY_PONTHOUS_RES = "attachment_ponthousres.png";
    private static final String LEGACY_PONTHOUS_INF = "attachment_ponthousinf.png";

    public static List<Button> offerFracturedSouls(Player player) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red(player.factionButtonChecker() + USE_PONTHOUS + "res", "Ponthous +"));
        buttons.add(Buttons.red(player.factionButtonChecker() + USE_PONTHOUS + "inf", "Ponthous -"));

        return buttons;
    }

    @ButtonHandler(USE_PONTHOUS)
    public static void resolvePonthousLegendaryPlanetAbility(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null
                || player == null
                || !player.getPlanets().contains(PONTHOUS)
                || !player.getExhaustedPlanets().contains(PONTHOUS)
                || !player.getExhaustedPlanetsAbilities().contains(PONTHOUS)) {
            return;
        }

        String resOrInf = buttonID.replace(USE_PONTHOUS, "");

        if ("res".equals(resOrInf)) {
            setPonthousAttachment(game, PONTHOUS_INF);
        } else if ("inf".equals(resOrInf)) {
            setPonthousAttachment(game, PONTHOUS_RES);
        } else {
            return;
        }

        player.refreshPlanet(PONTHOUS);
        ButtonHelper.deleteMessage(event);
    }

    public static void resetFracturedSouls(Game game, Player player) {
        if (player.hasPlanet(PONTHOUS)) {
            setPonthousAttachment(game, PONTHOUS_BOTH);
        }
    }

    private static void setPonthousAttachment(Game game, String attachment) {
        Planet ponthous = game.getPlanetsInfo().get(PONTHOUS);
        if (ponthous == null) {
            return;
        }
        ponthous.removeToken(PONTHOUS_BOTH);
        ponthous.removeToken(PONTHOUS_RES);
        ponthous.removeToken(PONTHOUS_INF);
        ponthous.addToken(attachment);
    }
}

package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.thrones;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;

@UtilityClass
public class ThronesPromissoryHandler {
    private static final String CHOOSE_THRONE = "chooseThroneForPromissory_";

    public static void getUnplacedThronePlanetButtonsForPN(
            GenericInteractionCreateEvent event, Game game, Player player) {
        ThronesAbilityHandler.sendUnplacedThronePlanetButtons(
                event,
                game,
                player,
                CHOOSE_THRONE,
                ", please choose the Throne planet to place in your home system.",
                List.of());
    }

    @ButtonHandler(CHOOSE_THRONE)
    public static void placeChosenThronePlanetInHs(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace(CHOOSE_THRONE, "");
        if (!ThronesAbilityHandler.placeThronePlanetInHomeSystem(event, game, player, planet)) {
            return;
        }

        Player owner = game.getPNOwner("thpnthrones");
        game.setPurgedPN("thpnthrones");
        player.removePromissoryNote("thpnthrones");
        if (owner != null) {
            owner.removePromissoryNote("thpnthrones");
            owner.removeOwnedPromissoryNoteByID("thpnthrones");
        }
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        if (owner != null && owner != player) {
            PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);
        }

        ThronesAbilityHandler.announceThronePlanetPlacement(event, player, planet);

        ButtonHelper.deleteMessage(event);
    }
}

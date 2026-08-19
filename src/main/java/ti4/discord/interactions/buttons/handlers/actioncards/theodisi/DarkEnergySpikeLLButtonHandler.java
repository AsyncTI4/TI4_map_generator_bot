package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.explore.ExploreService;

@UtilityClass
public class DarkEnergySpikeLLButtonHandler {
    private static final String RESOLVE_DARK_ENERGY_SPIKE = "resolveDarkEnergySpike";
    private static final String SELECT_DARK_ENERGY_SPIKE_SYSTEM = "selectDarkEnergySpikeSystem_";

    @ButtonHandler(RESOLVE_DARK_ENERGY_SPIKE)
    public static void resolveDarkEnergySpike(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the system whose frontier deck you wish to explore with _Dark Energy Spike_.";
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                message,
                NewStuffHelper.buttonPagination(
                        buttons, player.factionButtonChecker() + SELECT_DARK_ENERGY_SPIKE_SYSTEM, 0));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_DARK_ENERGY_SPIKE_SYSTEM)
    public static void selectDarkEnergySpikeSystem(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose the system whose frontier deck you wish to explore with _Dark Energy Spike_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SELECT_DARK_ENERGY_SPIKE_SYSTEM,
                buttonID)) return;

        Tile tile = game.getTileByPosition(buttonID.substring(SELECT_DARK_ENERGY_SPIKE_SYSTEM.length()));
        if (tile == null) return;

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " used _Dark Energy Spike_ to explore the frontier deck in "
                        + tile.getRepresentationForButtons(game, player) + ".");

        ExploreService.expFront(event, tile, game, player, true);
    }

    private static List<Button> getSystemButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : game.getTileMap().values()) {
            if (tile.getTileModel() != null && tile.getTileModel().isHyperlane()) continue;
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_DARK_ENERGY_SPIKE_SYSTEM + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player)));
        }
        return buttons;
    }
}

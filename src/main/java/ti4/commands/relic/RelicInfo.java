package ti4.commands.relic;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RelicHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class RelicInfo extends GameStateSubcommand {

    public RelicInfo() {
        super(Constants.RELIC_INFO, "Send relic information to your Cards Info channel", false, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        sendRelicInfo(game, player, event);
    }
    @ButtonHandler(Constants.REFRESH_RELIC_INFO)
    public static void sendRelicInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        String headerText = player.getRepresentation() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        RelicHelper.sendRelicInfo(player);
    }
}

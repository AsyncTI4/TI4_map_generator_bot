package ti4.discord.interactions.commands.map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

class ShowMapString extends GameStateSubcommand {

    public ShowMapString() {
        super("show_map_string", "Display the map string for this map", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!FoWHelper.canSeeWholeMap(game, event)) {
            MessageHelper.replyToMessage(
                    event, "In an active Fog of War game only the GM can see the map string, in the GM room.");
            return;
        }
        showMapString(event, game);
    }

    private static void showMapString(GenericInteractionCreateEvent event, Game game) {
        MessageHelper.sendMessageToEventChannel(event, game.getName() + " map string below:");
        MessageHelper.sendMessageToEventChannel(event, game.getMapString());
    }
}

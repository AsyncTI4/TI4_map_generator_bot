package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ShowMapString extends GameStateSubcommand {

    public ShowMapString() {
        super("show_map_string", "Display the map string for this map", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        showMapString(event, getGame());
    }

    private static void showMapString(GenericInteractionCreateEvent event, Game game) {
        MessageHelper.sendMessageToEventChannel(event, game.getName() + " map string below:");
        MessageHelper.sendMessageToEventChannel(event, game.getMapString());
    }
}

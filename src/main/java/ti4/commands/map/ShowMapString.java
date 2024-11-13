package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class ShowMapString extends MapSubcommandData {

    public ShowMapString() {
        super("show_map_string", "Display the map string for this map");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        showMapString(event, game);
    }

    public static void showMapString(GenericInteractionCreateEvent event, Game game) {
        if (game.isFowMode() && !game.isHasEnded()) {
            MessageHelper.sendMessageToEventChannel(event, "You can't use this in a Fog of War game");
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, game.getName() + " map string below:");
        MessageHelper.sendMessageToEventChannel(event, game.getMapString());
    }

}

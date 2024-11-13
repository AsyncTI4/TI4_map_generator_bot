package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.async.JimboHandlers;
import ti4.map.Game;

public class InteractiveBuilder extends MapSubcommandData {
    public InteractiveBuilder() {
        super(Constants.INTERACTIVE_BUILDER, "Use the interactive map builder to add tiles, tokens, and more");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        JimboHandlers.postMainMenu(event, game);
    }
}
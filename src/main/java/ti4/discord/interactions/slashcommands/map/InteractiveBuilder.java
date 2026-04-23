package ti4.discord.interactions.slashcommands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.slashcommands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.async.JimboHandlers;

public class InteractiveBuilder extends GameStateSubcommand {
    public InteractiveBuilder() {
        super(
                Constants.INTERACTIVE_BUILDER,
                "Use the interactive map builder to add tiles, tokens, and more",
                true,
                false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        JimboHandlers.postMainMenu(event, game);
    }
}

package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.map.Game;

public abstract class GameStateCommand implements Command {

    protected Game game;

    void preExecute(SlashCommandInteractionEvent event) {

    }

    void postExecute(SlashCommandInteractionEvent event) {

        Command.super.postExecute(event);
    }
}

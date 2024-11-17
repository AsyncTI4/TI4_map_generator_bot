package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.game.GameEnd;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.RematchHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

class Rematch extends GameStateSubcommand {

    public Rematch() {
        super(Constants.REMATCH, "Create a new game with the same players and channels as the current game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if ("YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            rematch(game, event);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Please type YES.");
        }

    }

    @ButtonHandler("rematch")
    private static void rematch(Game game, GenericInteractionCreateEvent event) {
        GameEnd.gameEndStuff(game, event, true);
        RematchHelper.secondHalfOfRematch(event, game);
    }
}

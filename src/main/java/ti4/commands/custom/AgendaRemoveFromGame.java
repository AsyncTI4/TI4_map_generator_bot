package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class AgendaRemoveFromGame extends GameStateSubcommand {

    public AgendaRemoveFromGame() {
        super(Constants.REMOVE_AGENDA_FROM_GAME, "Agenda remove from game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.AGENDA_ID, "Agenda ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean removed = game.removeAgendaFromGame(event.getOption(Constants.AGENDA_ID).getAsString());
        if (removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda removed from game deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda not found in game deck");
        }
    }
}

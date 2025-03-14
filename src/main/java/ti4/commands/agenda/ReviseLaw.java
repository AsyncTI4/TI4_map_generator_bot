package ti4.commands.agenda;

import com.amazonaws.util.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ReviseLaw extends GameStateSubcommand {

    public ReviseLaw() {
        super(Constants.REVISE_LAW, "Revise a law", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID, which is found between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ELECTED, "Elected non-player game object (e.g. secret objective, planet, etc.)"));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Elected Faction").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping agendaIdOption = event.getOption(Constants.AGENDA_ID);
        if (agendaIdOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No agenda ID defined.");
            return;
        }

        Game game = getGame();
        Player player = CommandHelper.getOtherPlayerFromEvent(game, event);

        String optionText;
        boolean playerWasElected = !StringUtils.isNullOrEmpty(event.getOption(Constants.TARGET_FACTION_OR_COLOR, null, OptionMapping::getAsString));
        String message = "Law revised";
        if (playerWasElected && player != null) {
            optionText = player.getFaction();
            message += " with " + player.getColor() + " as the elected color.";
        } else {
            optionText = event.getOption(Constants.ELECTED, null, OptionMapping::getAsString);
        }

        Player electedPlayer = game.getPlayerFromColorOrFaction(optionText);
        if (electedPlayer != null) {
            optionText = electedPlayer.getFaction();
        }
        boolean success = game.reviseLaw(agendaIdOption.getAsInt(), optionText);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found.");
        }
    }
}

package ti4.commands.agenda;

import com.amazonaws.util.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ReviseLaw extends AgendaSubcommandData {
    public ReviseLaw() {
        super(Constants.REVISE_LAW, "Revise a law");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ELECTED, "Elected PO or anything other than Faction").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Elected Faction").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }

        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);

        String optionText;
        boolean playerWasElected = !StringUtils.isNullOrEmpty(event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString));
        String message = "Law revised";
        if (playerWasElected && player != null) {
            optionText = player.getFaction();
            message = message + " with " + player.getColor() + " as the elected color";
        } else {
            optionText = event.getOption(Constants.ELECTED, null, OptionMapping::getAsString);
        }

        Player electedPlayer = game.getPlayerFromColorOrFaction(optionText);
        if (electedPlayer != null) {
            optionText = electedPlayer.getFaction();
        }
        boolean success = game.reviseLaw(option.getAsInt(), optionText);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found");
        }
    }
}

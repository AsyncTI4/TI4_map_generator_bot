package ti4.discord.interactions.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import software.amazon.awssdk.utils.StringUtils;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class AddLaw extends GameStateSubcommand {

    AddLaw() {
        super(Constants.ADD_LAW, "Add Agenda as Law", true, true);
        markSuspicious();
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID, which is found between the ()")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.STRING,
                Constants.ELECTED,
                "Elected non-player game object (e.g. secret objective, planet, etc.)"));
        addOptions(CommonOptions.factionColor("Elected faction"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option =
                CommandHelper.requireOrReport(event, event.getOption(Constants.AGENDA_ID), "No agenda ID defined.");
        if (option == null) return;

        Game game = getGame();
        Player player = getPlayer();
        String optionText;
        boolean playerWasElected =
                StringUtils.isNotBlank(event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString));
        if (playerWasElected) {
            optionText = player.getFaction();
        } else {
            optionText = event.getOption(Constants.ELECTED, null, OptionMapping::getAsString);
        }

        Player electedPlayer = game.getPlayerFromColorOrFaction(optionText);
        if (electedPlayer != null) {
            optionText = electedPlayer.getFaction();
        }

        boolean success = game.addLaw(option.getAsInt(), optionText);
        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law added.");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Law ID not found.");
        }
    }
}

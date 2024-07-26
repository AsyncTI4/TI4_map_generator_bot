package ti4.commands.cardsso;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class UnscoreSO extends SOCardsSubcommandData {
    public UnscoreSO() {
        super(Constants.UNSCORE_SO, "Unscore a secret objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Scored secret objective ID to unscore").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found.");
            return;
        }
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Please select which secret objectives to unscore.");
            return;
        }

        boolean scored = game.unscoreSecretObjective(getUser().getId(), option.getAsInt());
        if (!scored) {
            List<String> scoredSOs = player.getSecretsScored().entrySet().stream()
                .map(e -> "> (" + e.getValue() + ") " + SOInfo.getSecretObjectiveRepresentationShort(e.getKey()))
                .toList();
            StringBuilder sb = new StringBuilder("You haven't scored that secret objective; please retry.\nYour current scored secret objectives are:\n");
            scoredSOs.forEach(sb::append);
            if (scoredSOs.isEmpty()) sb.append("> None");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
            return;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unscored secret objective " + option.getAsInt());
        SOInfo.sendSecretObjectiveInfo(game, player, event);
    }
}

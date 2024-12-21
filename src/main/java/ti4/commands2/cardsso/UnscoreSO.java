package ti4.commands2.cardsso;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class UnscoreSO extends GameStateSubcommand {

    public UnscoreSO() {
        super(Constants.UNSCORE_SO, "Unscore Secret Objective", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Scored secret objective ID, which is found between ()").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int soId = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        boolean scored = game.unscoreSecretObjective(player.getUserID(), soId);
        if (!scored) {
            List<String> scoredSOs = player.getSecretsScored().entrySet().stream()
                .map(e -> "> (" + e.getValue() + ") " + SecretObjectiveInfoService.getSecretObjectiveRepresentationShort(e.getKey()))
                .toList();
            StringBuilder sb = new StringBuilder("Secret Objective ID found - please retry.\nYour current scored secret objectives are:\n");
            scoredSOs.forEach(sb::append);
            if (scoredSOs.isEmpty()) sb.append("> None");
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
            return;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unscored SO " + soId);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
    }
}

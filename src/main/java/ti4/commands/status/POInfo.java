package ti4.commands.status;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class POInfo extends StatusSubcommandData {
    public POInfo() {
        super("po_info", "Show Public Objectives");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_SCORED, "Also display which players have scored each objective").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeScored = event.getOption(Constants.INCLUDE_SCORED, false, OptionMapping::getAsBoolean);

        Game game = getActiveGame();
        Map<String, Integer> publicObjectiveIDs = game.getRevealedPublicObjectives();
        Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
        List<PublicObjectiveModel> publicObjectives = publicObjectiveIDs.entrySet().stream()
            .filter(id -> Mapper.isValidPublicObjective(id.getKey()))
            .map(id -> Mapper.getPublicObjective(id.getKey()))
            .toList();

        Player currentPlayer = game.getPlayer(getUser().getId());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("__**Current Public Objectives**__\n");
        int publicObjectiveNumber = 1;
        for (PublicObjectiveModel publicObjective : publicObjectives) {
            stringBuilder.append(publicObjectiveNumber)
                .append(". ")
                .append(publicObjective.getRepresentation())
                .append("\n");

            if (includeScored && scoredPublicObjectives.containsKey(publicObjective.getAlias())) {
                List<Player> playersWhoHaveScoredObjective = scoredPublicObjectives.get(publicObjective.getAlias()).stream()
                    .map(player -> game.getPlayer(player))
                    .filter(player -> player != null)
                    .filter(player -> !game.isFowMode() || FoWHelper.canSeeStatsOfPlayer(game, player, currentPlayer))
                    .toList();

                if (!playersWhoHaveScoredObjective.isEmpty()) {
                    stringBuilder.append("> Scored By:");
                }
                for (Player player : playersWhoHaveScoredObjective) {
                    stringBuilder.append(player.getFactionEmoji());
                }
                stringBuilder.append("\n");
            }
            publicObjectiveNumber++;
        }

        MessageHelper.sendMessageToChannel(
            event.getMessageChannel(),
            stringBuilder.toString());
    }
}

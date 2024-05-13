package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;

public class POInfo extends StatusSubcommandData {
    public POInfo() {
        super("po_info", "Show Public Objectives");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_SCORED, "Also display which players have scored each objective").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var includeScored = event.getOption(Constants.INCLUDE_SCORED, false, OptionMapping::getAsBoolean);

        var game = getActiveGame();
        var publicObjectiveIDs = game.getRevealedPublicObjectives();
        var scoredPublicObjectives = game.getScoredPublicObjectives();
        var publicObjectives = publicObjectiveIDs.entrySet().stream()
            .filter(id -> Mapper.isValidPublicObjective(id.getKey()))
            .map(id -> Mapper.getPublicObjective(id.getKey()))
            .toList();

        var currentPlayer = game.getPlayer(getUser().getId());
        var stringBuilder = new StringBuilder();
        stringBuilder.append("__**Current Public Objectives**__\n");
        int publicObjectiveNumber = 1;
        for (var publicObjective : publicObjectives) {
            stringBuilder.append(publicObjectiveNumber)
                .append(". ")
                .append(publicObjective.getRepresentation())
                .append("\n");

            if (includeScored && scoredPublicObjectives.containsKey(publicObjective.getAlias())) {
                var playersWhoHaveScoredObjective = scoredPublicObjectives.get(publicObjective.getAlias()).stream()
                    .map(player -> game.getPlayer(player))
                    .filter(player -> player != null)
                    .filter(player -> !game.isFoWMode() || FoWHelper.canSeeStatsOfPlayer(game, player, currentPlayer))
                    .toList();

                if (!playersWhoHaveScoredObjective.isEmpty()) {
                    stringBuilder.append("> Scored By:");
                }
                for (var player : playersWhoHaveScoredObjective) {
                    stringBuilder.append(player.getFactionEmoji());
                }
                stringBuilder.append("\n");
            }
            publicObjectiveNumber++;
        }

        MessageHelper.sendMessageToChannel(
            event.getMessageChannel(),
            stringBuilder.toString()
        );
    }
}

package ti4.commands2.status;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

class POInfo extends GameStateSubcommand {

    public POInfo() {
        super("po_info", "Show Public Objectives", false, true);
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_SCORED, "Also display which players have scored each objective").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean includeScored = event.getOption(Constants.INCLUDE_SCORED, false, OptionMapping::getAsBoolean);

        Game game = getGame();
        Map<String, Integer> publicObjectiveIDs = game.getRevealedPublicObjectives();
        Map<String, List<String>> scoredPublicObjectives = game.getScoredPublicObjectives();
        List<PublicObjectiveModel> publicObjectives = publicObjectiveIDs.keySet().stream()
            .filter(Mapper::isValidPublicObjective)
            .map(Mapper::getPublicObjective)
            .toList();

        Player currentPlayer = getPlayer();
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
                    .map(game::getPlayer)
                    .filter(Objects::nonNull)
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

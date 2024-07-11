package ti4.commands.custom;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PeakAtObjectiveDeck extends CustomSubcommandData {

    public PeakAtObjectiveDeck() {
        super("peak_objective_decks", "Peak at stage 1 or 2 objective deck");
        addOptions(new OptionData(OptionType.INTEGER, "stage", "Stage 1 or 2").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, "count", "Number of objectives to peak at (default 1)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }
        int count = event.getOption("count", 1, OptionMapping::getAsInt);
        int stage = event.getOption("stage", 1, OptionMapping::getAsInt);
        List<String> peakedObjectives = new ArrayList<>();
        List<String> poDeck = stage == 1 ? game.getPublicObjectives1() : game.getPublicObjectives2();
        for (int i = 0; i < count && i < poDeck.size(); i++) {
            peakedObjectives.add(poDeck.get(i));
        }
        StringBuilder sb = new StringBuilder()
            .append(player.getRepresentation(true, true))
            .append(" **Stage ").append(stage).append(" Public Objectives**").append("\n");
        peakedObjectives.stream()
            .map(peakedObjectiveId -> "(" + peakedObjectiveId + "): " + Mapper.getPublicObjective(peakedObjectiveId).getRepresentation())
            .forEach(sb::append);
        sb.append("\n");
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
    }

}

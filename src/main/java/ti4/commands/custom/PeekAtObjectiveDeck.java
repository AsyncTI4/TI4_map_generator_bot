package ti4.commands.custom;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.generator.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PeekAtObjectiveDeck extends PlayerGameStateSubcommand {

    public PeekAtObjectiveDeck() {
        super("peek_objective_decks", "Peek at stage 1 or 2 objective deck", true, false);
        addOptions(new OptionData(OptionType.INTEGER, "stage", "Stage 1 or 2").setRequired(true));
        addOptions(new OptionData(OptionType.INTEGER, "count", "Number of objectives to peek at (default 1)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int count = event.getOption("count", 1, OptionMapping::getAsInt);
        int stage = event.getOption("stage", 1, OptionMapping::getAsInt);
        List<String> peakedObjectives = new ArrayList<>();
        List<String> poDeck = stage == 1 ? game.getPublicObjectives1() : game.getPublicObjectives2();
        for (int i = 0; i < count && i < poDeck.size(); i++) {
            peakedObjectives.add(poDeck.get(i));
        }
        StringBuilder sb = new StringBuilder()
            .append(player.getRepresentationUnfogged())
            .append(" **Stage ").append(stage).append(" Public Objectives**").append("\n");
        peakedObjectives.stream()
            .map(peakedObjectiveId -> "(" + peakedObjectiveId + "): " + Mapper.getPublicObjective(peakedObjectiveId).getRepresentation())
            .forEach(sb::append);
        sb.append("\n");
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
    }

}

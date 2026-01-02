package ti4.commands.custom;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.PublicObjectiveModel;

class RevealSpecificStage2 extends GameStateSubcommand {

    RevealSpecificStage2() {
        super(Constants.REVEAL_SPECIFIC_STAGE2, "Reveal a specific stage 2 public objective", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, Constants.UNREVEALED_OBJECTIVE_INDEX,
                "Unrevealed objective index (1-based)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String objectiveId = event.getOption(Constants.PO_ID, null, OptionMapping::getAsString);
        Integer objectiveIndex =
                event.getOption(Constants.UNREVEALED_OBJECTIVE_INDEX, null, OptionMapping::getAsInt);
        if (objectiveId == null && objectiveIndex == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Provide a public objective ID or index.");
            return;
        }
        Map.Entry<String, Integer> objective = objectiveIndex != null
                ? game.revealSpecificStage2ByIndex(objectiveIndex)
                : game.revealSpecificStage2(objectiveId);
        if (objective == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective not found.");
            return;
        }
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "### " + game.getPing() + " **Stage 2 Public Objective Revealed**");
        event.getChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin()
                .queue(Consumers.nop(), BotLogger::catchRestError));
    }
}

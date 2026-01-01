package ti4.commands.custom;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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

class RevealSpecificUnrevealedObjective extends GameStateSubcommand {

    RevealSpecificUnrevealedObjective() {
        super(Constants.REVEAL_SPECIFIC_UNREVEALED_OBJECTIVE, "Reveal a specific unrevealed public objective", true,
                false);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String objectiveId = event.getOption(Constants.PO_ID).getAsString();
        Map.Entry<String, Integer> objective = game.revealSpecificUnrevealedObjective(objectiveId);
        if (objective == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Public objective not found among unrevealed objectives.");
            return;
        }
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        String stageLabel = po != null && po.getPoints() == 2 ? "Stage 2" : "Stage 1";
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "### " + game.getPing() + " **" + stageLabel + " Public Objective Revealed**");
        if (po != null) {
            event.getChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin()
                    .queue(Consumers.nop(), BotLogger::catchRestError));
        }
    }
}

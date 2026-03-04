package ti4.commands.custom;

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

class RevealSpecificStage1 extends GameStateSubcommand {

    RevealSpecificStage1() {
        super(Constants.REVEAL_SPECIFIC_STAGE1, "Reveal a specific stage 1 public objective", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String objectiveId = event.getOption(Constants.PO_ID).getAsString();
        PublicObjectiveModel po = Mapper.getPublicObjective(objectiveId);
        if (po == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Public objective with id " + objectiveId + " does not exist.");
            return;
        }

        boolean revealed = game.revealSpecificStage1(objectiveId);
        if (!revealed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective not found.");
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(), "### " + game.getPing() + " **Stage 1 Public Objective Revealed**");
        event.getChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin()
                .queue(Consumers.nop(), BotLogger::catchRestError));
    }
}

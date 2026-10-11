package ti4.discord.interactions.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

class RevealSpecificStage extends GameStateSubcommand {

    private final int stage;

    RevealSpecificStage(int stage) {
        super(
                stage == 1 ? Constants.REVEAL_SPECIFIC_STAGE1 : Constants.REVEAL_SPECIFIC_STAGE2,
                "Reveal a specific stage " + stage + " public objective",
                true,
                false);
        this.stage = stage;
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String objectiveId = event.getOption(Constants.PO_ID).getAsString();
        PublicObjectiveModel po = CommandHelper.requireOrReport(
                event,
                Mapper.getPublicObjective(objectiveId),
                "Public objective with id " + objectiveId + " does not exist.");
        if (po == null) return;

        boolean revealed = stage == 1 ? game.revealSpecificStage1(objectiveId) : game.revealSpecificStage2(objectiveId);
        if (!revealed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective not found.");
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getChannel(), "### " + game.getPing() + " **Stage " + stage + " Public Objective Revealed**");
        event.getChannel()
                .sendMessageEmbeds(po.getRepresentationEmbed())
                .queue(m -> m.pin().queue(Consumers.nop(), BotLogger::catchRestError));
    }
}

package ti4.commands.custom;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealSpecificStage1 extends CustomSubcommandData {
    public RevealSpecificStage1() {
        super(Constants.REVEAL_SPECIFIC_STAGE1, "PO to reveal");
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping poOption = event.getOption(Constants.PO_ID);
        if (poOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify PO");
            return;
        }
        Map.Entry<String, Integer> objective = game.revealSpecificStage1(poOption.getAsString());
        if (objective == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "PO not found");
            return;
        }
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(event.getChannel(), game.getPing() + " **Stage 1 Public Objective Revealed**");
        event.getChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
    }
}

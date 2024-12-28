package ti4.commands2.custom;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

class RevealSpecificStage1 extends GameStateSubcommand {

    public RevealSpecificStage1() {
        super(Constants.REVEAL_SPECIFIC_STAGE1, "Reveal a specific stage 1 public objective", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.PO_ID, "Public objective ID").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Map.Entry<String, Integer> objective = game.revealSpecificStage1(event.getOption(Constants.PO_ID).getAsString());
        if (objective == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Public objective not found.");
            return;
        }
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(event.getChannel(), game.getPing() + " **Stage 1 Public Objective Revealed**");
        event.getChannel().sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
    }
}

package ti4.commands.status;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealStage2 extends StatusSubcommandData {
    public RevealStage2() {
        super(Constants.REVEAL_STAGE2, "Reveal Stage2 Public Objective");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        revealS2(event, event.getChannel());
    }

    public void revealS2(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game activeGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());
        Map.Entry<String, Integer> objective = activeGame.revealState2();

        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(channel, Helper.getGamePing(event, activeGame) + " **Stage 2 Public Objective Revealed**");
        channel.sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
    }
}

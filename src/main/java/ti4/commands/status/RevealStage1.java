package ti4.commands.status;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealStage1 extends StatusSubcommandData {
    public RevealStage1() {
        super(Constants.REVEAL_STATGE1, "Reveal Stage1 Public Objective");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        revealS1(event, event.getChannel());
    }

    public void revealS1(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game activeGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());

        Map.Entry<String, Integer> objective = activeGame.revealState1();


        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
      String sb = Helper.getGamePing(event, activeGame) +
          " **Stage 1 Public Objective Revealed**" + "\n" +
          "(" + objective.getValue() + ") " +
          po.getRepresentation() + "\n";
        MessageHelper.sendMessageToChannelAndPin(channel, sb);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
    }
}

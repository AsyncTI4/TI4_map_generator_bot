package ti4.commands.status;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
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
        MessageHelper.sendMessageToChannel(channel, activeGame.getPing() + " **Stage 2 Public Objective Revealed**");
        channel.sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
        if (activeGame.getCurrentPhase().equalsIgnoreCase("status")) {
            // first do cleanup if necessary
            int playersWithSCs = 0;
            for (Player player : activeGame.getRealPlayers()) {
                if (player.getSCs() != null && player.getSCs().size() > 0 && !player.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }

            if (playersWithSCs > 0) {
                new Cleanup().runStatusCleanup(activeGame);
                if (!activeGame.isFoWMode()) {
                    MessageHelper.sendMessageToChannel(channel,
                            ListPlayerInfoButton.representScoring(activeGame, objective.getKey(), 0));
                }
                MessageHelper.sendMessageToChannel(activeGame.getMainGameChannel(),
                        activeGame.getPing() + "Status Cleanup Run!");
                if (!activeGame.isFoWMode()) {
                    DisplayType displayType = DisplayType.map;
                    MapGenerator.saveImage(activeGame, displayType, event)
                            .thenAccept(fileUpload -> MessageHelper
                                    .sendFileUploadToChannel(activeGame.getActionsChannel(), fileUpload));
                }
            }
        } else {
            if (!activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(channel,
                        ListPlayerInfoButton.representScoring(activeGame, objective.getKey(), 0));
            }
        }
    }

    public void revealTwoStage2(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game activeGame = GameManager.getInstance().getUserActiveGame(event.getUser().getId());

        Map.Entry<String, Integer> objective1 = activeGame.revealState2();
        Map.Entry<String, Integer> objective2 = activeGame.revealState2();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        MessageHelper.sendMessageToChannel(channel, activeGame.getPing() + " **Stage 2 Public Objectives Revealed**");
        channel.sendMessageEmbeds(List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed()))
                .queue(m -> m.pin().queue());

        int maxSCsPerPlayer;
        if (activeGame.getRealPlayers().isEmpty()) {
            maxSCsPerPlayer = activeGame.getSCList().size() / Math.max(1, activeGame.getPlayers().size());
        } else {
            maxSCsPerPlayer = activeGame.getSCList().size() / Math.max(1, activeGame.getRealPlayers().size());
        }

        if (maxSCsPerPlayer == 0)
            maxSCsPerPlayer = 1;

        if (activeGame.getRealPlayers().size() == 1) {
            maxSCsPerPlayer = 1;
        }
        activeGame.setStrategyCardsPerPlayer(maxSCsPerPlayer);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
        GameSaveLoadManager.saveMap(activeGame, event);
    }
}

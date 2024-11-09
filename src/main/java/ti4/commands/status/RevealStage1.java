package ti4.commands.status;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.generator.MapRenderPipeline;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;

public class RevealStage1 extends StatusSubcommandData {

    public RevealStage1() {
        super(Constants.REVEAL_STAGE1, "Reveal Stage1 Public Objective");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        revealS1(event, event.getChannel());
    }

    public void revealS1(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game game = GameManager.getUserActiveGame(event.getUser().getId());

        Map.Entry<String, Integer> objective = game.revealStage1();

        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 1 Public Objective Revealed**");
        channel.sendMessageEmbeds(po.getRepresentationEmbed()).queue(m -> m.pin().queue());
        if ("status".equalsIgnoreCase(game.getPhaseOfGame())) {
            // first do cleanup if necessary
            int playersWithSCs = 0;
            for (Player player : game.getRealPlayers()) {
                if (player.getSCs() != null && !player.getSCs().isEmpty() && !player.getSCs().contains(0)) {
                    playersWithSCs++;
                }
            }

            if (playersWithSCs > 0) {
                new Cleanup().runStatusCleanup(game);
                if (!game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(channel,
                        ListPlayerInfoButton.representScoring(game, objective.getKey(), 0));
                }
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
                    game.getPing() + " **Status Cleanup Run!**");
                if (!game.isFowMode()) {
                    MapRenderPipeline.render(game, event, DisplayType.map,
                                    fileUpload -> MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload));
                }
            }
        } else {
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(channel,
                    ListPlayerInfoButton.representScoring(game, objective.getKey(), 0));
            }
        }
    }

    public static void revealTwoStage1(GenericInteractionCreateEvent event, MessageChannel channel) {
        Game game = GameManager.getUserActiveGame(event.getUser().getId());

        Map.Entry<String, Integer> objective1 = game.revealStage1();
        Map.Entry<String, Integer> objective2 = game.revealStage1();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 1 Public Objectives Revealed**");
        channel.sendMessageEmbeds(List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed()))
            .queue(m -> m.pin().queue());

        int maxSCsPerPlayer;
        if (game.getRealPlayers().isEmpty()) {
            maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getPlayers().size());
        } else {
            maxSCsPerPlayer = game.getSCList().size() / Math.max(1, game.getRealPlayers().size());
        }

        if (maxSCsPerPlayer == 0)
            maxSCsPerPlayer = 1;

        if (game.getRealPlayers().size() == 1) {
            maxSCsPerPlayer = 1;
        }
        game.setStrategyCardsPerPlayer(maxSCsPerPlayer);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = GameManager.getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);
    }
}

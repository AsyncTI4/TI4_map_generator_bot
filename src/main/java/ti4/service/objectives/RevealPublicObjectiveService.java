package ti4.service.objectives;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.DisplayType;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;
import ti4.service.StatusCleanupService;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class RevealPublicObjectiveService {

    public static void revealS2(Game game, GenericInteractionCreateEvent event, MessageChannel channel) {
        Map.Entry<String, Integer> objective = game.revealStage2();

        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 2 Public Objective Revealed**");
        channel.sendMessageEmbeds(po.getRepresentationEmbed())
                .queue(m -> m.pin().queue());
        if (!"status".equalsIgnoreCase(game.getPhaseOfGame())) {
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        channel, ListPlayerInfoService.representScoring(game, objective.getKey(), 0));
            }
            return;
        }
        // first do cleanup if necessary
        int playersWithSCs = 0;
        for (Player player : game.getRealPlayers()) {
            if (player.getSCs() != null
                    && !player.getSCs().isEmpty()
                    && !player.getSCs().contains(0)) {
                playersWithSCs++;
            }
        }

        if (playersWithSCs > 0) {
            StatusCleanupService.runStatusCleanup(game);
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        channel, ListPlayerInfoService.representScoring(game, objective.getKey(), 0));
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + " **Status Cleanup Run!**");
            if (!game.isFowMode()) {
                DisplayType displayType = DisplayType.map;
                MapRenderPipeline.queue(
                        game,
                        event,
                        displayType,
                        fileUpload -> MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload));
            }
        }
    }

    public static void revealTwoStage2(Game game, GenericInteractionCreateEvent event, MessageChannel channel) {
        Map.Entry<String, Integer> objective1 = game.revealStage2();
        Map.Entry<String, Integer> objective2 = game.revealStage2();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 2 Public Objectives Revealed**");
        channel.sendMessageEmbeds(List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed()))
                .queue(m -> m.pin().queue());

        int maxSCsPerPlayer;
        if (game.getRealPlayers().isEmpty()) {
            maxSCsPerPlayer =
                    game.getSCList().size() / Math.max(1, game.getPlayers().size());
        } else {
            maxSCsPerPlayer =
                    game.getSCList().size() / Math.max(1, game.getRealPlayers().size());
        }

        if (maxSCsPerPlayer == 0) maxSCsPerPlayer = 1;

        if (game.getRealPlayers().size() == 1) {
            maxSCsPerPlayer = 1;
        }
        game.setStrategyCardsPerPlayer(maxSCsPerPlayer);
    }

    public void revealS1(Game game, GenericInteractionCreateEvent event, MessageChannel channel) {
        Map.Entry<String, Integer> objective = game.revealStage1();
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 1 Public Objective Revealed**");
        channel.sendMessageEmbeds(po.getRepresentationEmbed())
                .queue(m -> m.pin().queue());
        if (!"status".equalsIgnoreCase(game.getPhaseOfGame())) {
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        channel, ListPlayerInfoService.representScoring(game, objective.getKey(), 0));
            }
            return;
        }
        // first do cleanup if necessary
        int playersWithSCs = 0;
        for (Player player : game.getRealPlayers()) {
            if (player.getSCs() != null
                    && !player.getSCs().isEmpty()
                    && !player.getSCs().contains(0)) {
                playersWithSCs++;
            }
        }

        if (playersWithSCs > 0) {
            StatusCleanupService.runStatusCleanup(game);
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(
                        channel, ListPlayerInfoService.representScoring(game, objective.getKey(), 0));
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), game.getPing() + " **Status Cleanup Run!**");
            if (!game.isFowMode()) {
                MapRenderPipeline.queue(
                        game,
                        event,
                        DisplayType.map,
                        fileUpload -> MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload));
            }
        }
    }

    public static void revealTwoStage1(Game game) {
        Map.Entry<String, Integer> objective1 = game.revealStage1();
        Map.Entry<String, Integer> objective2 = game.revealStage1();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        var channel = game.getMainGameChannel();
        MessageHelper.sendMessageToChannel(channel, game.getPing() + " **Stage 1 Public Objectives Revealed**");
        channel.sendMessageEmbeds(List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed()))
                .queue(m -> m.pin().queue());

        int maxSCsPerPlayer;
        if (game.getRealPlayers().isEmpty()) {
            maxSCsPerPlayer =
                    game.getSCList().size() / Math.max(1, game.getPlayers().size());
        } else {
            maxSCsPerPlayer =
                    game.getSCList().size() / Math.max(1, game.getRealPlayers().size());
        }

        if (maxSCsPerPlayer == 0) maxSCsPerPlayer = 1;

        if (game.getRealPlayers().size() == 1) {
            maxSCsPerPlayer = 1;
        }
        game.setStrategyCardsPerPlayer(maxSCsPerPlayer);
    }
}

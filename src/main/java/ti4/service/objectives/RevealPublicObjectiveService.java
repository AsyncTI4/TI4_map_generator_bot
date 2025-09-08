package ti4.service.objectives;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.RelicHelper;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;
import ti4.service.StatusCleanupService;
import ti4.service.emoji.SourceEmojis;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
public class RevealPublicObjectiveService {

    private static final int WHITE_COLOR = 0xFFFFFF;

    public static void revealS2(Game game, GenericInteractionCreateEvent event) {
        revealS2(game, event, false);
    }

    public static void revealS2(Game game, GenericInteractionCreateEvent event, boolean random) {
        Map.Entry<String, Integer> objective;
        if (random) {
            objective = game.revealStage2Random();
        } else {
            objective = game.revealStage2();
        }

        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        RelicHelper.offerInitialNeuraLoopChoice(game, objective.getKey());
        var channel = game.getActionsChannel();
        if (game.isLiberationC4Mode()) {
            if (game.getRevealedPublicObjectives().get("Control Ordinian") == null
                    || game.getRevealedPublicObjectives().get("Control Ordinian") == 0) {
                game.addCustomPO("Control Ordinian", 2);
                MessageHelper.sendMessageToChannel(
                        channel,
                        "### " + game.getPing() + ", a stage 2 public objective has been revealed." + "\n### "
                                + game.getPing()
                                + " _Liberate Ordinian_ is no longer scorable. _Control Ordinian_ is now available to be scored.");
                EmbedBuilder control = new EmbedBuilder();
                control.setTitle(SourceEmojis.Codex + "__**Control Ordinian**__");
                control.setDescription("Control Ordinian.");
                control.setColor(WHITE_COLOR);
                channel.sendMessageEmbeds(List.of(po.getRepresentationEmbed(), control.build()))
                        .queue(m -> m.pin().queue());
            } else {
                MessageHelper.sendMessageToChannel(
                        channel, "### " + game.getPing() + ", a stage 2 public objective has been revealed.");
                channel.sendMessageEmbeds(po.getRepresentationEmbed())
                        .queue(m -> m.pin().queue());
            }
        } else {
            channel.sendMessageEmbeds(po.getRepresentationEmbed())
                    .queue(m -> m.pin().queue());
        }

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
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "### " + game.getPing() + " **Status Cleanup Run!**");
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

    public static void revealTwoStage2(Game game, MessageChannel channel) {
        Map.Entry<String, Integer> objective1 = game.revealStage2();
        Map.Entry<String, Integer> objective2 = game.revealStage2();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        MessageHelper.sendMessageToChannel(
                channel, game.getPing() + ", two stage 2 public objectives has been revealed.");
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

    public static void revealSO(Game game, MessageChannel channel) {
        Map.Entry<String, Integer> objective = game.revealSecretObjective();
        RelicHelper.offerInitialNeuraLoopChoice(game, objective.getKey());

        SecretObjectiveModel po = Mapper.getSecretObjective(objective.getKey());
        if (po == null) {
            Map<String, String> sos = Mapper.getSecretObjectivesJustNames();
            for (Map.Entry<String, String> entry : sos.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(objective.getKey())) {
                    po = Mapper.getSecretObjective(entry.getKey());
                }
            }
        }
        MessageHelper.sendMessageToChannel(
                channel, game.getPing() + ", a secret objective has been converted to a public objective.");
        if (po != null) {
            channel.sendMessageEmbeds(List.of(po.getRepresentationEmbed()))
                    .queue(m -> m.pin().queue());
        }
    }

    public String revealS1(Game game, GenericInteractionCreateEvent event) {
        return revealS1(game, event, false);
    }

    public String revealS1(Game game, GenericInteractionCreateEvent event, boolean random) {
        Map.Entry<String, Integer> objective;
        if (random) {
            objective = game.revealStage1Random();
        } else {
            objective = game.revealStage1();
        }
        PublicObjectiveModel po = Mapper.getPublicObjective(objective.getKey());
        var channel = game.getActionsChannel();
        MessageHelper.sendMessageToChannel(
                channel, "### " + game.getPing() + ", a stage 1 public objective has been revealed.");
        channel.sendMessageEmbeds(po.getRepresentationEmbed())
                .queue(m -> m.pin().queue());
        RelicHelper.offerInitialNeuraLoopChoice(game, objective.getKey());
        if (!"status".equalsIgnoreCase(game.getPhaseOfGame())) {
            if (!game.isFowMode() && !Objects.equals(objective.getKey(), Constants.IMPERIUM_REX_ID)) {
                MessageHelper.sendMessageToChannel(
                        channel, ListPlayerInfoService.representScoring(game, objective.getKey(), 0));
            }
            return objective.getKey();
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
            if (!game.isFowMode() && !Objects.equals(objective.getKey(), Constants.IMPERIUM_REX_ID)) {
                MessageHelper.sendMessageToChannel(
                        channel, ListPlayerInfoService.representScoring(game, objective.getKey(), 0));
            }
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), "### " + game.getPing() + ", it's time for the Status Cleanup Run!");
            if (!game.isFowMode()) {
                MapRenderPipeline.queue(
                        game,
                        event,
                        DisplayType.map,
                        fileUpload -> MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload));
            }
        }

        return objective.getKey();
    }

    public static void revealTwoStage1(Game game) {
        Map.Entry<String, Integer> objective1 = game.revealStage1();
        Map.Entry<String, Integer> objective2 = game.revealStage1();

        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        var channel = game.getActionsChannel();
        if (game.isLiberationC4Mode()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    game.getPing()
                            + ", two regular stage 1 public objectives, along with the __Liberate Ordinian__ scenario public objective, have been revealed.");
            EmbedBuilder liberate = new EmbedBuilder();
            liberate.setTitle(SourceEmojis.Codex + "__**Liberate Ordinian**__");
            liberate.setDescription("Win a combat against the Nekro Virus.");
            liberate.setColor(WHITE_COLOR);
            channel.sendMessageEmbeds(
                            List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed(), liberate.build()))
                    .queue(m -> m.pin().queue());
        } else {
            MessageHelper.sendMessageToChannel(
                    channel, game.getPing() + ", two stage 1 public objectives have been revealed.");
            channel.sendMessageEmbeds(List.of(po1.getRepresentationEmbed(), po2.getRepresentationEmbed()))
                    .queue(m -> m.pin().queue());
        }

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

    public static void revealAllObjectives(Game game) {
        var channel = game.getMainGameChannel();
        Map.Entry<String, Integer> objective1 = game.revealStage1();
        Map.Entry<String, Integer> objective2 = game.revealStage1();
        Map.Entry<String, Integer> objective3 = game.revealStage1();
        Map.Entry<String, Integer> objective4 = game.revealStage1();
        Map.Entry<String, Integer> objective6 = game.revealStage2();
        Map.Entry<String, Integer> objective7 = game.revealStage2();
        Map.Entry<String, Integer> objective8 = game.revealStage2();
        Map.Entry<String, Integer> objective9 = game.revealStage2();
        PublicObjectiveModel po1 = Mapper.getPublicObjective(objective1.getKey());
        PublicObjectiveModel po2 = Mapper.getPublicObjective(objective2.getKey());
        PublicObjectiveModel po3 = Mapper.getPublicObjective(objective3.getKey());
        PublicObjectiveModel po4 = Mapper.getPublicObjective(objective4.getKey());
        PublicObjectiveModel po6 = Mapper.getPublicObjective(objective6.getKey());
        PublicObjectiveModel po7 = Mapper.getPublicObjective(objective7.getKey());
        PublicObjectiveModel po8 = Mapper.getPublicObjective(objective8.getKey());
        PublicObjectiveModel po9 = Mapper.getPublicObjective(objective9.getKey());
        if (game.getPublicObjectives1Peakable().isEmpty()) {
            MessageHelper.sendMessageToChannel(channel, game.getPing() + ", all objectives have been revealed.");
            channel.sendMessageEmbeds(List.of(
                            po1.getRepresentationEmbed(),
                            po2.getRepresentationEmbed(),
                            po3.getRepresentationEmbed(),
                            po4.getRepresentationEmbed(),
                            po6.getRepresentationEmbed(),
                            po7.getRepresentationEmbed(),
                            po8.getRepresentationEmbed(),
                            po9.getRepresentationEmbed()))
                    .queue(m -> m.pin().queue());

        } else {
            Map.Entry<String, Integer> objective5 = game.revealStage1();
            Map.Entry<String, Integer> objective10 = game.revealStage2();
            PublicObjectiveModel po5 = Mapper.getPublicObjective(objective5.getKey());
            PublicObjectiveModel po10 = Mapper.getPublicObjective(objective10.getKey());
            MessageHelper.sendMessageToChannel(channel, game.getPing() + ", all objectives have been revealed.");
            channel.sendMessageEmbeds(List.of(
                            po1.getRepresentationEmbed(),
                            po2.getRepresentationEmbed(),
                            po3.getRepresentationEmbed(),
                            po4.getRepresentationEmbed(),
                            po5.getRepresentationEmbed(),
                            po6.getRepresentationEmbed(),
                            po7.getRepresentationEmbed(),
                            po8.getRepresentationEmbed(),
                            po9.getRepresentationEmbed(),
                            po10.getRepresentationEmbed()))
                    .queue(m -> m.pin().queue());
        }

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

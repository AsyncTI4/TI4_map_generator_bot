package ti4.service.megagame;

import ti4.model.metadata.AutoPingMetadataManager;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.image.io.ImageUploads;
import ti4.image.map.MegagameGroupsImage;
import ti4.message.MessageHelper;
import ti4.service.game.StartPhaseService;

@UtilityClass
public class MegagameStrategyPhaseService {

    public static void startRound(Game game) {
        MegagameGroupService.rotate(game);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "Strategy card groups have shifted one column.\n" + MegagameGroupService.summary(game));
        sendGroupsImage(game, game.getMainGameChannel());
    }

    public static void sendGroupsImage(Game game, MessageChannel channel) {
        sendGroupsImage(game, channel, MegagameGroupService.currentLayout(game), true, "_sc_groups");
    }

    public static void sendNextRoundImage(Game game, MessageChannel channel) {
        sendGroupsImage(game, channel, MegagameGroupService.nextRoundLayout(game), false, "_sc_groups_next_round");
    }

    private static void sendGroupsImage(
            Game game, MessageChannel channel, MegagameGroupService.Layout layout, boolean showCards, String suffix) {
        if (channel == null) {
            return;
        }
        BufferedImage image = MegagameGroupsImage.render(game, layout, showCards);
        MessageHelper.sendFileUploadToChannel(
                channel, ImageUploads.createFileUpload(image, game.getName() + suffix, "png"));
    }

    public static void pingFirstPickers(Game game, String pickMessage) {
        List<Player> pickers = new ArrayList<>();
        for (String group : MegagameGroupService.groups(game)) {
            Player picker = nextPicker(game, group);
            MegagameGroupService.setCurrentPicker(game, group, picker);
            if (picker != null) {
                pickers.add(picker);
                sendPickPrompt(game, group, picker, pickMessage);
            }
        }
        if (!pickers.isEmpty()) {
            game.updateActivePlayer(pickers.getFirst());
        }
    }

    public static void afterPick(GenericInteractionCreateEvent event, Game game, Player player) {
        String group = MegagameGroupService.groupOf(game, player);
        if (group != null) {
            Player next = nextPicker(game, group);
            MegagameGroupService.setCurrentPicker(game, group, next);
            if (next != null) {
                sendPickPrompt(game, group, next, " Please use the buttons to pick a strategy card.");
            } else {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), MegagameGroupService.label(group) + " has finished picking.");
            }
        }
        List<Player> stillPicking = currentPickers(game);
        if (stillPicking.isEmpty()) {
            StartPhaseService.startActionPhase(event, game);
            return;
        }
        game.setPhaseOfGame("strategy");
        AutoPingMetadataManager.setupAutoPing(game.getName());
        if (!stillPicking.contains(game.getActivePlayer())) {
            game.updateActivePlayer(stillPicking.getFirst());
        }
    }

    public static List<Player> currentPickers(Game game) {
        List<Player> pickers = new ArrayList<>();
        for (String group : MegagameGroupService.groups(game)) {
            Player picker = MegagameGroupService.currentPicker(game, group);
            if (picker != null) {
                pickers.add(picker);
            }
        }
        return pickers;
    }

    static Player nextPicker(Game game, String group) {
        if (unpickedGroupCards(game, group) == 0) {
            return null;
        }
        int maxCards = Math.max(1, game.getStrategyCardsPerPlayer());
        Player next = null;
        for (Player member : MegagameGroupService.orderFromSpeaker(game, group)) {
            int held = member.getSCs().size();
            if (held < maxCards && (next == null || held < next.getSCs().size())) {
                next = member;
            }
        }
        return next;
    }

    private static long unpickedGroupCards(Game game, String group) {
        return MegagameGroupService.groupCards(game, group).stream()
                .filter(sc -> game.getPlayerFromSC(sc) == null)
                .count();
    }

    private static void sendPickPrompt(Game game, String group, Player picker, String pickMessage) {
        MessageHelper.sendMessageToChannelWithButtons(
                picker.getCorrectChannel(),
                picker.getRepresentationUnfogged() + " is up to pick a strategy card for "
                        + MegagameGroupService.label(group) + "." + pickMessage,
                Helper.getRemainingSCButtons(game, picker));
    }
}

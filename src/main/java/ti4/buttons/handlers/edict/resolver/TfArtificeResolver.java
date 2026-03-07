package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.service.VeiledHeartService;

public class TfArtificeResolver implements EdictResolver {

    @Getter
    public String edict = "tf-artifice";

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        int vpDifference = game.getHighestScore() - player.getTotalVictoryPoints();

        MessageChannel channel = player.getCorrectChannel();
        if (vpDifference < 1) {
            RelicHelper.drawRelicAndNotify(player, event, game);
            ButtonHelperTwilightsFall.drawParadigm(game, player, event, false);
            game.removeStoredValue("artificeParadigms");
            MessageHelper.sendMessageToChannel(
                    channel,
                    "No player has more victory points than " + player.getRepresentationNoPing()
                            + ", so they were not able to draw any additional relics or paradigms.");
        } else {
            ButtonHelperTwilightsFall.drawParadigm(game, player, event, false);
            String relic = game.getAllRelics().getFirst();
            RelicModel mod = Mapper.getRelic(relic);
            MessageHelper.sendMessageToChannelWithEmbed(
                    channel,
                    player.getRepresentationNoPing() + " will draw the following relic:",
                    mod.getRepresentationEmbed());

            String plural = (vpDifference == 1 ? "" : "s");
            String msg = player.getRepresentation() + ", you are " + vpDifference + " point" + plural
                    + " behind the player with the most victory points, so they may draw "
                    + vpDifference + " additional card" + plural + " from either deck.";
            List<Button> buttons = new ArrayList<>();
            for (int x = 0; x <= vpDifference; x++) {
                int relics = x;
                int paradigms = vpDifference - x;

                String id = player.finChecker() + "artificeStep2_" + relics + "_" + paradigms;
                String label = relics + " Relic" + (relics == 1 ? "s" : "") + " & ";
                label += paradigms + " Paradigm" + (paradigms == 1 ? "s" : "");
                buttons.add(Buttons.green(id, label));
            }
            MessageHelper.sendMessageToChannelWithButtons(channel, msg, buttons);
        }
    }

    @ButtonHandler("artificeStep2")
    public static void artificeStep2(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        int relics = Integer.parseInt(buttonID.split("_")[1]);
        int paradigms = Integer.parseInt(buttonID.split("_")[2]);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " has decided to draw " + relics + " extra relic"
                        + (relics == 1 ? "" : "s") + " and " + paradigms + " extra paradigm"
                        + (paradigms == 1 ? "" : "s") + ".");
        if (relics > 0) {
            RelicHelper.drawWithAdvantage(player, game, 1 + relics);
        } else {
            RelicHelper.drawRelicAndNotify(player, event, game);
        }
        if (paradigms > 0) {
            List<Button> buttons = new ArrayList<>();
            for (int x = 0; x < paradigms; x++) {
                ButtonHelperTwilightsFall.drawParadigm(game, player, event, false);
            }
            for (String paradigm : game.getStoredValue("artificeParadigms").split("_")) {
                buttons.add(Buttons.green(
                        "keepArtificeParadigm_" + paradigm,
                        "Keep " + Mapper.getLeader(paradigm).getName()));
            }
            MessageHelper.sendMessageToChannel(
                    game.isVeiledHeartMode() ? player.getCardsInfoThread() : player.getCorrectChannel(),
                    player.getRepresentation() + ", please choose the paradigm you wish to keep.",
                    buttons);
            if (game.isVeiledHeartMode()) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + " is choosing which paradigm to keep.");
            }
        } else {
            game.removeStoredValue("artificeParadigms");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("keepArtificeParadigm")
    public static void keepArtificeParadigm(ButtonInteractionEvent event, Game game, String buttonID, Player player) {
        String paradigm = buttonID.split("_")[1];
        List<String> drawn =
                new ArrayList<>(List.of(game.getStoredValue("savedParadigms").split("_")));
        for (String paradigmToLose : game.getStoredValue("artificeParadigms").split("_")) {
            if (!paradigmToLose.equalsIgnoreCase(paradigm)) {
                if (game.isVeiledHeartMode()) {
                    VeiledHeartService.doAction(
                            VeiledHeartService.VeiledCardAction.DISCARD,
                            VeiledHeartService.VeiledCardType.PARADIGM,
                            player,
                            paradigmToLose);
                }
                player.removeLeader(paradigmToLose);
                drawn.remove(paradigmToLose);
            }
        }
        game.setStoredValue("savedParadigms", String.join("_", drawn));

        MessageHelper.sendMessageToChannel(
                game.isVeiledHeartMode() ? player.getCardsInfoThread() : player.getCorrectChannel(),
                player.getRepresentation() + " kept the _"
                        + Mapper.getLeader(paradigm).getName() + "_ paradigm.");
        if (game.isVeiledHeartMode()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has chosen a paradigm to keep and discarded the others.");
        }
        game.removeStoredValue("artificeParadigms");

        ButtonHelper.deleteMessage(event);
    }
}

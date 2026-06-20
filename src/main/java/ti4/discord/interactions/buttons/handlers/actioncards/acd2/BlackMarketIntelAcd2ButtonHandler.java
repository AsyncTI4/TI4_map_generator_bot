package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.service.info.SecretObjectiveInfoService;

@UtilityClass
class BlackMarketIntelAcd2ButtonHandler {

    @ButtonHandler("resolveBlackMarketIntel")
    public static void resolveBlackMarketIntel(Player player, Game game, ButtonInteractionEvent event) {
        String so1 = game.drawSecretObjective(player.getUserID());
        String so2 = game.drawSecretObjective(player.getUserID());

        String publicMsg = player.getRepresentationNoPing() + " drew 2 secret objectives using _Black Market Intel_.";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicMsg);

        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);

        List<Player> eligiblePlayers = game.getRealPlayers().stream()
                .filter(p -> p != player && p.getSoScored() < 3)
                .toList();

        if (eligiblePlayers.isEmpty() || (so1 == null && so2 == null)) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged()
                            + ", no eligible player found to receive a secret objective (all players have 3 or more scored secret objectives). You keep both.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String soID : List.of(so1 != null ? so1 : "", so2 != null ? so2 : "")) {
            if (soID.isEmpty()) continue;
            SecretObjectiveModel soModel = Mapper.getSecretObjective(soID);
            String soName = soModel != null ? soModel.getName() : soID;
            for (Player target : eligiblePlayers) {
                String label = "Give '" + soName + "' to " + target.getColorIfCanSeeStats(player);
                String buttonId =
                        player.factionButtonChecker() + "blackMarketIntelGive_" + soID + "_" + target.getFaction();
                buttons.add(Buttons.blue(buttonId, label));
            }
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", choose which secret objective to give to another player with fewer than 3 scored secret objectives.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("blackMarketIntelGive_")
    public static void blackMarketIntelGive(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String remainder = buttonID.replace("blackMarketIntelGive_", "");
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore < 0) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String soID = remainder.substring(0, lastUnderscore);
        String targetFaction = remainder.substring(lastUnderscore + 1);
        Player target = game.getPlayerFromColorOrFaction(targetFaction);

        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "Could not find target player; secret objective was not transferred.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Integer soIdentifier = player.getSecretsUnscored().get(soID);
        if (soIdentifier == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(), "Could not find that secret objective in your hand.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.removeSecret(soIdentifier);
        target.setSecret(soID);

        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, target);

        String publicGiveMsg = player.getRepresentationNoPing()
                + " gave a secret objective to "
                + (game.isFowMode() ? "another player" : target.getRepresentationNoPing())
                + " using _Black Market Intel_.";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), publicGiveMsg);

        if (!Objects.equals(player.getCardsInfoThread(), target.getCardsInfoThread())) {
            MessageHelper.sendMessageToChannel(
                    target.getCardsInfoThread(),
                    target.getRepresentationUnfogged()
                            + ", you received a secret objective from "
                            + player.getColorIfCanSeeStats(target)
                            + " via _Black Market Intel_.");
        }

        ButtonHelper.deleteMessage(event);
    }
}

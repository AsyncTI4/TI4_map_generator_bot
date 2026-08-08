package ti4.service.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant.RevenantLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant.RevenantTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Revenant.RevenantUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Verydith.VerydithLeadersHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.franken.FrankenAlternateTextService;
import ti4.service.info.CardsInfoService;

@UtilityClass
public class UnlockLeaderService {

    public static void unlockLeader(String leaderID, Game game, Player player) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);
        String message;
        if (leaderModel != null) {
            message = player.getRepresentation() + " has unlocked their " + leaderModel.getType() + ".";
        } else {
            message =
                    player.getRepresentation() + " unlocked " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
        }
        unlockLeader(leaderID, game, player, message);
    }

    public static void unlockLeader(String leaderID, Game game, Player player, String message) {
        Leader playerLeader = player.unsafeGetLeader(leaderID);
        MessageChannel channel = game.getMainGameChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }

        if (playerLeader == null) {
            MessageHelper.sendMessageToChannel(channel, "Leader " + leaderID + " not found");
            return;
        }
        playerLeader.setLocked(false);

        if (Constants.COMMANDER.equals(playerLeader.getType())) {
            CommanderUnlockCheckService.checkAllPlayersInGame(game, "revenant");
        }

        LeaderModel leaderModel = playerLeader.getLeaderModel().orElse(null);
        boolean showFlavourText = Constants.VERBOSITY_VERBOSE.equals(game.getOutputVerbosity());

        if (leaderModel != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    channel,
                    message,
                    FrankenAlternateTextService.getLeaderEmbed(
                            game, leaderModel, false, true, true, showFlavourText, game.isTwilightsFallMode()));
        } else {
            MessageHelper.sendMessageToChannel(
                    channel, LeaderEmojis.getLeaderEmoji(playerLeader).toString());
            MessageHelper.sendMessageToChannel(channel, message);
        }
        if (player.hasAbility("commanding_presence")) {
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", **Commanding Presence** allows you to gain 1 command token after unlocking "
                            + Helper.getLeaderFullRepresentation(playerLeader) + ".",
                    ButtonHelper.getGainCCButtons(player));
        }

        if (leaderID.contains("bentorcommander")) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getFactionEmoji() + ", your commodity value has been set to " + player.getCommoditiesTotal()
                            + ".");
        }

        if (leaderID.contains("naalucommander")) {
            CardsInfoService.sendVariousAdditionalButtons(game, player);
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentationUnfogged()
                            + ", you may use M'aban, the Naalu Commander, via button in your `#cards-info` thread.");
        }

        if ("xxchahero".equals(leaderID)) {
            if (game.getPhaseOfGame().contains("status")) {
                MessageHelper.sendMessageToChannel(
                        channel,
                        "Reminder, " + player.getRepresentationUnfogged()
                                + ", that officially Xxekir Grom remains locked until after both objectives have been scored;"
                                + " you cannot use the ability to pay for any requirements of the unlocking objectives (if they're spendies).");
            } else {
                MessageHelper.sendMessageToChannel(
                        channel,
                        "Reminder, " + player.getRepresentationUnfogged()
                                + ", that officially Xxekir Grom remains locked until after the objective has been scored;"
                                + " you cannot use the ability to pay for any requirements of the unlocking objective (if it's a spendie).");
            }
        }

        if ("veylorhero".equals(leaderID)) {
            game.setStoredValue("veylorHeroActive_" + player.getFaction(), "yes");
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    game.getPing() + ", there will now be a third agenda each agenda phase. Congratulations "
                            + player.getRepresentationNoPing() + ", you are now the senate!");
        }

        if ("revenanthero".equals(leaderID)) {
            RevenantLeadersHandler.offerRevenantHeroChoices(game, player);
        }
        if (player.hasUnit("revenant_mech")) {
            RevenantUnitsHandler.doRevenantMechCheck(game, player);
        }
        if (Constants.COMMANDER.equals(playerLeader.getType()) || Constants.HERO.equals(playerLeader.getType())) {
            RevenantTechHandler.doLazarusPodsLeaderCheck(game);
        }
        if ("verydithcommander".equals(leaderID)) {
            VerydithLeadersHandler.checkVerydithCommander(game);
        }

        if (playerLeader.isExhausted()) {
            MessageHelper.sendMessageToChannel(channel, "Leader is also exhausted");
        }
    }
}

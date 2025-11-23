package ti4.service.agenda;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperTwilightsFallActionCards;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.async.DrumrollService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.relic.HeartOfIxthService;
import ti4.service.unit.DestroyUnitService;
import ti4.spring.jda.JdaService;

@UtilityClass
public class IxthianArtifactService {

    @Nullable
    public static String watchPartyPing(Game game) {
        List<Role> roles = JdaService.guildPrimary.getRolesByName("Ixthian Watch Party", true);
        if (!game.isFowMode() && !roles.isEmpty()) {
            return roles.getFirst().getAsMention();
        }
        return null;
    }

    @Nullable
    public static TextChannel watchPartyChannel(Game game) {
        List<TextChannel> channels = JdaService.guildPrimary.getTextChannelsByName("ixthian-watch-party", true);
        if (!game.isFowMode() && !channels.isEmpty()) {
            return channels.getFirst();
        }
        return null;
    }

    @ButtonHandler("rollIxthian")
    public static void rollIxthian(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        if (game.getSpeakerUserID().equals(player.getUserID()) || "rollIxthianIgnoreSpeaker".equals(buttonID)) {
            rollIxthian(event, player, game, !game.isFowMode());
        } else {
            Button ixthianButton =
                    Buttons.green("rollIxthianIgnoreSpeaker", "Roll Ixthian Artifact", PlanetEmojis.Mecatol);
            String msg = "The speaker should roll for _Ixthian Artifact_. Click this button to roll anyway!";
            MessageHelper.sendMessageToChannelWithButton(event.getChannel(), msg, ixthianButton);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static int drumrollSeconds() {
        int rand = 6 + ThreadLocalRandom.current().nextInt(6);
        if (ThreadLocalRandom.current().nextInt(5) == 0) { // random chance for an extra long wait
            rand += 8 + ThreadLocalRandom.current().nextInt(14);
        }
        return rand;
    }

    public static void rollIxthian(GenericInteractionCreateEvent event, Player player, Game game, boolean publish) {
        String gameMsg = game.getPing();
        String gameName = game.getName();
        MessageChannel chan = game.getMainGameChannel();
        MessageChannel partyChan = watchPartyChannel(game);
        String watchMsg = watchPartyPing(game);

        Predicate<Game> resolve = futureGame -> {
            resolveIxthianRoll(event, player, futureGame, publish && partyChan != null);
            return false;
        };

        int sec = drumrollSeconds();
        if (publish && !game.isFowMode()) {
            DrumrollService.doDrumrollMirrored(chan, gameMsg, sec, gameName, resolve, partyChan, watchMsg);
        } else {
            DrumrollService.doDrumroll(chan, gameMsg, sec, gameName, resolve);
        }
    }

    private String resultMessage(Die result, boolean heartUsed) {
        String msg = "## Rolled a " + result.getGreenDieIfSuccessOrRedDieIfFailure() + " for Ixthian Artifact";

        if (!heartUsed) {
            msg += "!!";
            if (result.isSuccess()) {
                msg += TechEmojis.Propulsion3.toString();
                msg += TechEmojis.Biotic3.toString();
                msg += TechEmojis.Cybernetic3.toString();
                msg += TechEmojis.Warfare3.toString();
            } else {
                msg += "💥💥💥💥";
            }
        } else if (heartUsed) {
            msg += "........\n# BUT THE HEART OF IXTH PLAYER CHANGED THE OUTCOME!!!";
            if (result.isSuccess()) {
                msg += "💥💥💥💥";
            } else {
                msg += TechEmojis.Propulsion3.toString();
                msg += TechEmojis.Biotic3.toString();
                msg += TechEmojis.Cybernetic3.toString();
                msg += TechEmojis.Warfare3.toString();
            }
        }
        return msg;
    }

    private String watchPartyMessage(Game game, Die result, boolean heartUsed) {
        String message = game.getName() + " has finished rolling:\n";
        message += resultMessage(result, heartUsed);
        return message;
    }

    private int getIxthianThreshold(Game game) {
        if (game.getAgendaDeckID().toLowerCase().contains("absol")) return 7;
        return 6;
    }

    private static void resolveIxthianRoll(
            GenericInteractionCreateEvent event, Player player, Game game, boolean publish) {
        TextChannel partyChan = watchPartyChannel(game);

        Die result = new Die(getIxthianThreshold(game));
        if (!result.eligibleForHeart() || !HeartOfIxthService.isHeartAvailable(game)) {
            if (result.isSuccess()) {
                resolveIxthianTech(game, result, false, publish);
            } else {
                resolveIxthianDestroy(event, game, result, false, publish);
            }
        } else {
            Player heartPlayer = HeartOfIxthService.getHeartOfIxthPlayer(game, true);
            String buttonID = heartPlayer.finChecker() + "resolveIxthian_" + result.getResult();
            buttonID += publish ? "_publish" : "";
            Button good = Buttons.green(buttonID, "Get Tech", TechEmojis.PropulsionTech);
            Button bad = Buttons.red(buttonID, "Explode", MiscEmojis.DoubleBoom);

            List<Button> buttons = HeartOfIxthService.makeHeartOfIxthButtons(game, heartPlayer, good, bad, result);
            String msg = resultMessage(result, false).replaceFirst("## ", "");
            msg += "\nHOWEVER, " + heartPlayer.getRepresentation() + " you can use the Heart of Ixth";
            msg += " to modify the outcome of Ixthian Artifact.";
            MessageHelper.sendMessageToChannelWithButtons(heartPlayer.getCorrectChannel(), msg, buttons);

            if (game.isFowMode()) {
                String suspenseMsg = "Someone is deciding whether or not to modify the roll...";
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), suspenseMsg);
            } else if (publish && partyChan != null) {
                String suspenseMsg = "In " + game.getName();
                suspenseMsg += ", someone is deciding whether or not to modify the roll...";
                MessageHelper.sendMessageToChannel(partyChan, suspenseMsg);
            }
        }
    }

    @ButtonHandler("resolveIxthian")
    private void resolveIxthian(ButtonInteractionEvent event, Game game, String buttonID) {
        boolean publish = buttonID.contains("_publish");
        boolean heart = buttonID.endsWith("_heart");
        int result = Integer.parseInt(buttonID.split("_")[1]);
        int threshold = getIxthianThreshold(game);
        Die res = DiceHelper.spoof(threshold, result);

        if (res.isSuccess() ^ heart) {
            resolveIxthianTech(game, res, heart, publish);
        } else {
            resolveIxthianDestroy(event, game, res, heart, publish);
        }
    }

    private void resolveIxthianTech(Game game, Die result, boolean usedHeart, boolean publish) {
        informGameAndWatchPartyAndExhaustHeart(game, result, usedHeart, publish);

        if (game.isFowMode()) {
            String fowMsg = "You may use the button to get your two technologies.";
            for (Player p : game.getRealPlayers()) {
                if (p.hasAbility("propagation")) {
                    List<Button> buttons = ButtonHelper.getGainCCButtons(p);
                    String propMsg = p.getRepresentation();
                    propMsg += ", you would research a technology, but because of **Propagation**";
                    propMsg += ", you instead gain 6 command tokens. Your current command tokens are ";
                    propMsg += p.getCCRepresentation() + ". Use buttons to gain command tokens.";
                    MessageHelper.sendMessageToChannelWithButtons(p.getCorrectChannel(), propMsg, buttons);
                    game.setStoredValue("originalCCsFor" + p.getFaction(), p.getCCRepresentation());
                } else {
                    MessageHelper.sendMessageToChannelWithButton(p.getCorrectChannel(), fowMsg, Buttons.GET_A_TECH);
                }
            }
            return;
        }

        if (Helper.getPlayerFromAbility(game, "propagation") != null) {
            Player player = Helper.getPlayerFromAbility(game, "propagation");
            List<Button> buttons = ButtonHelper.getGainCCButtons(player);
            String propMsg = player.getRepresentation();
            propMsg += ", you would research a technology, but because of **Propagation**";
            propMsg += ", you instead gain 6 command tokens. Your current command tokens are ";
            propMsg += player.getCCRepresentation() + ". Use buttons to gain command tokens.";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), propMsg, buttons);
            game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());
        }
        MessageHelper.sendMessageToChannelWithButton(
                game.getMainGameChannel(), "You may use the button to get your two technologies.", Buttons.GET_A_TECH);
    }

    private static void resolveIxthianDestroy(
            GenericInteractionCreateEvent event, Game game, Die result, boolean usedHeart, boolean publish) {
        informGameAndWatchPartyAndExhaustHeart(game, result, usedHeart, publish);

        Tile tile = game.getMecatolTile();
        {
            Player neutral = game.getPlayerFromColorOrFaction("neutral");
            if (neutral != null) {
                DestroyUnitService.destroyAllPlayerUnitsInSystem(event, game, neutral, tile, false);
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), "Destroyed all neutral units in the Mecatol Rex system.");
            }
        }
        ButtonHelperTwilightsFallActionCards.sendDestroyButtonsForSpecificTileAndSurrounding(game, tile);
        String msg = "Please destroy units in or adjacent to the Mecatol Rex system.";
        if (game.isFowMode()) {
            for (Player p : game.getRealPlayers()) {
                MessageHelper.sendMessageToChannel(p.getCorrectChannel(), msg);
            }
            return;
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
    }

    private void informGameAndWatchPartyAndExhaustHeart(Game game, Die result, boolean usedHeart, boolean publish) {
        if (usedHeart) {
            Player heartPlayer = HeartOfIxthService.getHeartOfIxthPlayer(game, true);
            HeartOfIxthService.exhaustHeartOfIxth(game, heartPlayer, !result.isSuccess());
        }

        String resultMsg = resultMessage(result, usedHeart);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), resultMsg);
        TextChannel watchParty = watchPartyChannel(game);
        if (watchParty != null && !game.isFowMode() && publish) {
            String watchMsg = watchPartyMessage(game, result, usedHeart);
            MessageHelper.sendMessageToChannel(watchParty, watchMsg);
        }
    }
}

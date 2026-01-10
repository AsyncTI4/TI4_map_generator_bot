package ti4.service.objectives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.function.Consumers;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.commands.special.SetupNeutralPlayer;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.StringHelper;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.model.ColorModel;
import ti4.model.SecretObjectiveModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.image.FileUploadService;
import ti4.service.info.SecretObjectiveInfoService;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class DrawSecretService {

    public static void drawSO(GenericInteractionCreateEvent event, Game game, Player player) {
        drawSO(event, game, player, 1, true);
    }

    public static void drawSO(
            GenericInteractionCreateEvent event, Game game, Player player, int count, boolean useTnelis) {
        String output = " drew " + count + " secret objective" + (count > 1 ? "s" : "") + ".";
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            output += "Drew a " + (count == 1 ? "second" : StringHelper.ordinal(count + 1))
                    + " secret objective due to **Plausible Deniability**.";
            count++;
        }
        List<String> idsDrawn = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            idsDrawn.add(game.drawSecretObjective(player.getUserID()));
        }
        MessageHelper.sendMessageToEventChannel(event, player.getRepresentation() + output);
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event);
        if (useTnelis && player.hasAbility("plausible_deniability")) {
            SecretObjectiveHelper.sendSODiscardButtons(player);
        }
        if (event instanceof ButtonInteractionEvent bevent
                && bevent.getUser().getId().equals(player.getUserID())) {
            List<MessageEmbed> soEmbeds = idsDrawn.stream()
                    .map(Mapper::getSecretObjective)
                    .filter(Objects::nonNull)
                    .map(SecretObjectiveModel::getRepresentationEmbed)
                    .toList();
            bevent.getHook()
                    .setEphemeral(true)
                    .sendMessage("Drew the following secret objective(s):")
                    .addEmbeds(soEmbeds)
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    public static void dealSOToAll(GenericInteractionCreateEvent event, int count, Game game) {
        if (count > 0) {
            for (Player player : game.getRealPlayers()) {
                for (int i = 0; i < count; i++) {
                    game.drawSecretObjective(player.getUserID());
                }
                if (player.hasAbility("plausible_deniability")) {
                    game.drawSecretObjective(player.getUserID());
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + " due to **Plausible Deniability**, you were dealt an extra secret objective. Thus, you must also discard an extra secret objective.");
                }
                SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player, event, game.getRound() == 1, false);
            }
        }
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                count + " " + CardEmojis.SecretObjective + " dealt to all players. Check your `#cards-info` threads.");
        if (game.getRound() == 1) {
            if (!game.isFowMode()) {
                StringBuilder message =
                        new StringBuilder("Here are the quick reference cards for the factions in this game.");
                List<FileUpload> files = new ArrayList<>();
                for (Player player : game.getRealPlayers()) {
                    String path = ResourceHelper.getResourceFromFolder(
                            "quick_reference/" + (game.isThundersEdge() ? "TE/" : "PoK/"),
                            player.getFaction().toLowerCase() + ".png");
                    if (path == null) {
                        message.append("\n- Could not get quick reference for ")
                                .append(player.getFaction())
                                .append(".");
                    } else {
                        files.add(FileUploadService.createFileUpload(
                                ImageHelper.read(path), player.getFaction() + "_ref"));
                    }
                }
                if (!files.isEmpty() && files.size() <= 10) {
                    message.append(
                            "\n-# A reminder that these reference cards are general overviews, and not specific mechanical text.");
                    MessageHelper.sendMessageWithFiles(
                            game.getActionsChannel(), files, message.toString(), true, false);
                }
            }
            if ((game.getStoredValue("useOldPok").isEmpty())
                    && !game.isTwilightsFallMode()
                    && !game.isBaseGameMode()
                    && !game.isHomebrewSCMode()) {
                game.setStrategyCardSet("te");
            }

            if ((!game.getStoredValue("useOldPok").isEmpty()) && !game.isTwilightsFallMode()) {
                game.validateAndSetRelicDeck(Mapper.getDeck("relics_pok"));
                game.resetRelics();
                game.setStrategyCardSet("pok");
            } else if (!game.isThundersEdge() && !game.isTwilightsFallMode()) {
                game.removeRelicFromGame("quantumcore");
                game.removeRelicFromGame("thesilverflame");
            }
            if (game.isThundersEdge() && !game.isTwilightsFallMode()) {
                Player neutral = game.getPlayerFromColorOrFaction("neutral");
                if (neutral == null) {
                    List<String> unusedColors = game.getUnusedColors().stream()
                            .map(ColorModel::getName)
                            .toList();
                    String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
                    game.setupNeutralPlayer(color);
                }
                game.validateAndSetRelicDeck(Mapper.getDeck("relics_pok_te"));
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_te"));
                game.setStrategyCardSet("te");
            }
            if (game.isTwilightsFallMode()) {
                ButtonHelperTwilightsFall.fixMahactColors(game, event);
                game.setupTwilightsFallMode(event);
            }
            if (game.isThundersEdge() || game.getStoredValue("useOldPok").isEmpty() || game.isTwilightsFallMode()) {
                Tile mr = game.getMecatolTile();
                if (mr != null) {
                    String pos = mr.getPosition();
                    boolean ingress = mr.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS);
                    game.removeTile(pos);
                    Tile tile = new Tile("112", pos);
                    Planet rex = tile.getUnitHolderFromPlanet("mrte");
                    rex.addToken(Constants.CUSTODIAN_TOKEN_PNG);
                    game.setTile(tile);
                    if (ingress) {
                        tile.getSpaceUnitHolder().addToken(Constants.TOKEN_INGRESS);
                    }
                }
            }
            if (game.isCulturalExchangeProgramMode()) {
                List<String> factions = new ArrayList<>(game.getRealFactions());
                Collections.shuffle(factions);
                for (Player player : game.getRealPlayers()) {
                    player.setLeaders(new ArrayList<>());
                    String faction = factions.removeFirst();
                    player.initLeadersForFaction(faction);
                    for (Leader leader : player.getLeaders()) {
                        if (leader.getId().contains("commander")) {
                            UnlockLeaderService.unlockLeader(leader.getId(), game, player);
                        }
                    }
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + ", due to _Cultural Exchange Program_, you have received the leaders of "
                                    + Mapper.getFaction(faction).getFactionName() + " this game.");
                }
            }
            List<Button> buttons = new ArrayList<>();
            String message = "Reveal Objectives and Start Strategy Phase";
            if (!game.getStoredValue("needsInauguralSplice").isEmpty()) {
                message = "Reveal Objectives and Start Inaugural Splice";
            }
            buttons.add(Buttons.green("startOfGameObjReveal", message));

            MessageHelper.sendMessageToChannelWithButtons(
                    game.getMainGameChannel(), "Press this button after everyone has discarded.", buttons);
            Player speaker = null;
            if (game.getPlayer(game.getSpeakerUserID()) != null) {
                speaker = game.getPlayers().get(game.getSpeakerUserID());
            }
            if (speaker == null) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "Speaker is not yet assigned. Secret objectives have been dealt, but please assign speaker soon (command is `/player speaker`).");
            }
        }
    }
}

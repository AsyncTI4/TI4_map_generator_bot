package ti4.service.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.buttons.Buttons;
import ti4.commands.special.SetupNeutralPlayer;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.service.emoji.MiscEmojis;
import ti4.service.image.FileUploadService;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class RoundOneService {

    public static void RoundOne(GenericInteractionCreateEvent event, Game game) {
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
                    files.add(FileUploadService.createFileUpload(ImageHelper.read(path), player.getFaction() + "_ref"));
                }
            }
            if (!files.isEmpty() && files.size() <= 10) {
                message.append(
                        "\n-# A reminder that these reference cards are general overviews, and not specific mechanical text.");
                MessageHelper.sendMessageWithFiles(game.getActionsChannel(), files, message.toString(), true, false);
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
                List<String> unusedColors =
                        game.getUnusedColors().stream().map(ColorModel::getName).toList();
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

        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("fine_print")
                    || player.hasAbility("collateralized_loans")
                    || player.hasAbility("binding_debts")) {
                game.setDebtPoolIcon(Constants.VADEN_DEBT_POOL, MiscEmojis.SharkLoan.toString());
            }
        }
    }
}

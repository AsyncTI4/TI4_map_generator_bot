package ti4.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.service.async.DrumrollService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.TileEmojis;

@UtilityClass
public class SilverFlameService {

    public String rep(boolean includeCardText) {
        if (includeCardText) {
            return Mapper.getRelic("thesilverflame").getSimpleRepresentation();
        } else {
            return Mapper.getRelic("thesilverflame").getName();
        }
    }

    public void rollSilverFlame(ButtonInteractionEvent event, Game game, Player player) {
        silverFlameDrumroll(player, 10);
    }

    private List<Button> silverFlameResolveButtons(Game game, Player player, Die resultDie) {
        List<Button> resolveButtons = new ArrayList<>();
        if (resultDie.getResult() > 8) {
            resolveButtons.add(Buttons.green(
                    player.finChecker() + "resolveSilverFlamePoint", "Gain 1 Victory Point", CardEmojis.Public1alt));
        }
        resolveButtons.add(Buttons.red(
                player.finChecker() + "resolveSilverFlamePurge", "Purge your Home System", TileEmojis.TileRedBack));

        // TODO: other mykomentori related buttons
        // if (player.getPromissoryNotesInPlayArea().contains("dspnmyko") &&
        // game.getStoredValue("usedGiftInsight").isBlank())
        //     resolveButtons.add(Buttons.blue("rerollSilverFlame", "Reroll with Gift of Insight", CardEmojis.PN));

        return resolveButtons;
    }

    private void silverFlameDrumroll(Player prePlayer, int target) {
        Game gameS = prePlayer.getGame();
        String gameName = gameS.getName();
        String watchPartyMsg = prePlayer.getRepresentation() + " is rolling for the silver flame in " + gameS.getName()
                + "! They are at " + prePlayer.getTotalVictoryPoints() + "/" + gameS.getVp() + " VP!";
        if (prePlayer.hasRelicReady("heartofixth")) {
            watchPartyMsg += " They have the heart of ixth, so only need an 8!";
        }
        DisasterWatchHelper.postTileInFlameWatch(gameS, null, prePlayer.getHomeSystemTile(), 0, watchPartyMsg);
        String drumrollMessage = prePlayer.getRepresentation() + " is rolling for " + rep(false) + "!";
        DrumrollService.doDrumroll(prePlayer.getCorrectChannel(), drumrollMessage, 5, gameName, game -> {
            Die result = new Die(target);
            Player player = game.getPlayer(prePlayer.getUserID());

            String resultMsg = "## " + player.getRepresentation() + " rolled a " + result.getResult() + " for "
                    + rep(false) + "! " + result.getGreenDieIfSuccessOrRedDieIfFailure();
            DisasterWatchHelper.sendMessageInFlameWatch(game, resultMsg);
            resultMsg += "\nUse the button%s to resolve:";
            List<Button> buttons = silverFlameResolveButtons(game, player, result);
            resultMsg = String.format(resultMsg, buttons.size() > 1 ? "s" : "");

            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), resultMsg, buttons);
            return false;
        });
    }

    @ButtonHandler("resolveSilverFlamePoint")
    private void resolveSilverFlamePoint(ButtonInteractionEvent event, Game game, Player player, String buttonID) {

        String flame = "The Silver Flame";
        Integer id = game.getRevealedPublicObjectives().getOrDefault(flame, null);

        String message = null;
        if (id != null) {
            game.scorePublicObjective(player.getUserID(), id);
            message = player.getRepresentation() + " scored '" + flame + "'";
        } else {
            id = game.addCustomPO(flame, 1);
            game.scorePublicObjective(player.getUserID(), id);
            message = "Custom PO '" + flame + "' has been added.\n" + player.getRepresentation() + " scored '" + flame
                    + "'";
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveSilverFlamePurge")
    private void resolveSilverFlamePurge(ButtonInteractionEvent event, Game game, Player player, String buttonID) {

        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Where the heck is your home system???");
            BotLogger.error(Constants.jazzPing() + " Missing Home System");
            return;
        }

        // count units
        Map<UnitKey, Integer> allUnitsCount = new HashMap<>();
        List<UnitType> singles = List.of(UnitType.Infantry, UnitType.Fighter);
        for (UnitHolder uh : homeSystem.getUnitHolders().values()) {
            for (UnitKey key : uh.getUnitsByState().keySet()) {
                int uhAmt = singles.contains(key.getUnitType()) ? 1 : uh.getUnitCount(key);
                allUnitsCount.put(key, allUnitsCount.getOrDefault(key, 0) + uhAmt);
            }
        }

        // purge units
        StringBuilder purgedUnitList = new StringBuilder("Also purged the following units from the game:");
        for (Entry<UnitKey, Integer> entry : allUnitsCount.entrySet()) {
            UnitKey key = entry.getKey();
            Player owner = game.getPlayerFromColorOrFaction(key.getColorID());
            if (owner == null) continue;

            int quantity = entry.getValue();
            owner.setUnitCap(key.asyncID(), player.getUnitCap(key.asyncID()) - quantity);
            purgedUnitList
                    .append("\n> ")
                    .append(owner.fogSafeEmoji())
                    .append(key.unitEmoji())
                    .append(" (x")
                    .append(quantity)
                    .append(")");
        }

        // Post messages
        String message = "## " + rep(false) + " was used to purge the " + player.fogSafeEmoji() + " home system in "
                + game.getName();

        message += "\n" + purgedUnitList;
        DisasterWatchHelper.postTileInDisasterWatch(game, event, homeSystem, 0, message);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

        // remove all planets
        for (Planet planet : homeSystem.getPlanetUnitHolders()) {
            for (Player p : game.getRealPlayers()) {
                if (p.hasPlanet(planet.getName())) p.removePlanet(buttonID);
            }
        }

        game.removeTile(homeSystem.getPosition());
        ButtonHelper.deleteMessage(event);
    }
}

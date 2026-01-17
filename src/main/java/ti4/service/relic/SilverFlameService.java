package ti4.service.relic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.helpers.SecretObjectiveHelper;
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
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.TileEmojis;
import ti4.service.map.AddTileService;
import ti4.service.map.FractureService;

@UtilityClass
public class SilverFlameService {

    public String rep() {
        return Mapper.getRelic("thesilverflame").getSimpleRepresentation(false);
    }

    public void rollSilverFlame(ButtonInteractionEvent event, Game game, Player player) {
        silverFlameDrumroll(game, player, 10);
    }

    private List<Button> silverFlameResolveButtons(Game game, Player player, Die resultDie) {
        List<Button> resolveButtons = new ArrayList<>();
        String ffcc = player.finChecker();
        Button good = Buttons.green(ffcc + "resolveSilverFlamePoint", "Gain 1 Victory Point", CardEmojis.Public1alt);
        Button bad = Buttons.red(ffcc + "resolveSilverFlamePurge", "Purge your Home System", TileEmojis.TileRedBack);
        resolveButtons.addAll(HeartOfIxthService.makeHeartOfIxthButtons(game, player, good, bad, resultDie));

        // TODO: other mykomentori related buttons
        // if (player.getPromissoryNotesInPlayArea().contains("dspnmyko") &&
        // game.getStoredValue("usedGiftInsight").isBlank())
        //     resolveButtons.add(Buttons.blue("rerollSilverFlame", "Reroll with Gift of Insight", CardEmojis.PN));

        return resolveButtons;
    }

    private void silverFlameDrumroll(Game game, Player flamePlayer, int target) {
        String gameName = game.getName();
        String watchPartyMsg =
                flamePlayer.getRepresentation() + " is rolling for _The Silver Flame_ in " + gameName + "!";
        watchPartyMsg += " They are currently at " + flamePlayer.getTotalVictoryPoints() + "/" + game.getVp()
                + " victory points";
        watchPartyMsg +=
                (flamePlayer.getTotalVictoryPoints() + 1 == game.getVp()) ? "; this is for all the marbles." : ".";
        if (flamePlayer.hasRelicReady("heartofixth")) {
            watchPartyMsg += " They have the _Heart of Ixth_, so only need to roll a 9!";
        } else if (HeartOfIxthService.isHeartAvailable(game)) {
            watchPartyMsg += " Somebody else has the _Heart of Ixth_ though! 😱";
        }
        DisasterWatchHelper.postTileInFlameWatch(game, null, flamePlayer.getHomeSystemTile(), 0, watchPartyMsg);

        Predicate<Game> resolve = g2 -> {
            Player player = g2.getPlayer(flamePlayer.getUserID());
            resolveSilverFlameRoll(g2, player, target);
            return false;
        };
        String drumrollMessage = flamePlayer.getRepresentation() + " is rolling for " + rep() + "!";
        DrumrollService.doDrumroll(flamePlayer.getCorrectChannel(), drumrollMessage, 5, gameName, resolve);
    }

    private void resolveSilverFlameRoll(Game game, Player flamePlayer, int target) {
        Die result = new Die(target);

        String dice;
        boolean definitePoint = false;
        if (flamePlayer.hasRelicReady("heartofixth") && result.getResult() == 9) {
            dice = DiceEmojis.d10blue_9.toString();
            definitePoint = true;
        } else if (HeartOfIxthService.isHeartAvailable(game)
                && !flamePlayer.hasRelicReady("heartofixth")
                && result.getResult() >= 9) {
            dice = DiceEmojis.getGrayDieEmoji(result.getResult());
        } else {
            dice = result.getGreenDieIfSuccessOrRedDieIfFailure();
            definitePoint = result.getResult() == 10;
        }

        String resultMsg = "## " + flamePlayer.getRepresentation() + " rolled " + dice + " for _The Silver Flame_! ";
        if (definitePoint && (flamePlayer.getTotalVictoryPoints() + 1 == game.getVp())) {
            resultMsg += "\nEnjoy all the marbles.";
        }
        DisasterWatchHelper.sendMessageInFlameWatch(game, resultMsg);
        resultMsg += "\nPlease resolve with %s.";
        List<Button> buttons = silverFlameResolveButtons(game, flamePlayer, result);
        resultMsg = String.format(resultMsg, buttons.size() == 1 ? "this button" : "these buttons");

        MessageHelper.sendMessageToChannelWithButtons(flamePlayer.getCorrectChannel(), resultMsg, buttons);
    }

    @ButtonHandler("resolveSilverFlame")
    private void resolveSilverFlame(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (HeartOfIxthService.waitForHeartToResolve(event, game, player)) return;

        if (buttonID.contains("Point")) resolveSilverFlamePoint(event, game, player, buttonID);
        if (buttonID.contains("Purge")) resolveSilverFlamePurge(event, game, player, buttonID);
    }

    private void resolveSilverFlamePoint(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String flame = "The Silver Flame";
        Integer id = game.getRevealedPublicObjectives().getOrDefault(flame, null);

        String message = null;
        if (id != null) {
            game.scorePublicObjective(player.getUserID(), id);
            message = player.getRepresentation() + " scored \"" + flame + "\".";
        } else {
            id = game.addCustomPO(flame, 1);
            game.scorePublicObjective(player.getUserID(), id);
            message = "Custom PO \"" + flame + "\" has been added.";
            message += "\n" + player.getRepresentation() + " scored \"" + flame + "\".";
        }
        Helper.checkEndGame(game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteAllButtons(event);
        if (!FractureService.isFractureInPlay(game)) {
            FractureService.spawnFracture(null, game);
            FractureService.spawnIngressTokens(null, game, player, null);
        }
    }

    private void resolveSilverFlamePurge(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile homeSystem = player.getHomeSystemTile();
        if (homeSystem == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Where the heck is your home system???");
            BotLogger.error(Constants.jazzPing() + " Missing Home System");
            return;
        }

        game.setStoredValue("silverFlamed", player.getFaction());

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
        String message = "## " + rep() + " was used to purge the " + player.fogSafeEmoji() + " home system in "
                + game.getName() + ".";

        if (!allUnitsCount.isEmpty()) {
            message += "\n" + purgedUnitList;
        }
        DisasterWatchHelper.sendMessageInFlameWatch(game, message);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

        // remove all planets
        boolean controlsAPlanet = false;
        for (Planet planet : homeSystem.getPlanetUnitHolders()) {
            for (Player p : game.getRealPlayers()) {
                if (p.hasPlanet(planet.getName())) p.removePlanet(buttonID);
            }
            if (!planet.isSpaceStation() && player.getPlanetsForScoring(false).contains(planet)) {
                controlsAPlanet = true;
            }
        }

        game.removeTile(homeSystem.getPosition());
        String planetTileName = AliasHandler.resolveTile("silver_flame");
        Tile tile = new Tile(planetTileName, homeSystem.getPosition());
        AddTileService.addTile(game, tile);
        if (!FractureService.isFractureInPlay(game)) {
            FractureService.spawnFracture(null, game);
            FractureService.spawnIngressTokens(null, game, player, null);
        }
        ButtonHelper.deleteMessage(event);

        if (controlsAPlanet && player.getSecretsUnscored().containsKey("bam")) {
            List<Button> scoreButtons = new ArrayList<>();
            String ffcc = player.finChecker();
            scoreButtons.add(Buttons.green(
                    ffcc + "scoreSilverFlameBAM", "Score Become A Martyr", CardEmojis.SecretObjectiveAlt));
            scoreButtons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    "You have _Become A Martyr_, and have just catastrophically lost control of a planet in your home system. "
                            + "Do you wish to score this secret objective?",
                    scoreButtons);
        }
    }

    @ButtonHandler("scoreSilverFlameBAM")
    private void scoreSilverFlameBAM(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Map<String, Integer> secretObjectives = player.getSecrets();
        Integer idValue = null;
        if (secretObjectives != null && !secretObjectives.isEmpty()) {
            for (Map.Entry<String, Integer> so : secretObjectives.entrySet()) {
                if ("bam".equals(so.getKey())) {
                    idValue = so.getValue();
                    break;
                }
            }
        }
        if (idValue == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Could not find _Become A Martyr_; please score manually.");
            return;
        }
        MessageChannel channel = game.isFowMode() ? player.getCardsInfoThread() : game.getMainGameChannel();
        SecretObjectiveHelper.scoreSO(event, game, player, idValue, channel);
        if (!game.isFowMode() && (player.getTotalVictoryPoints() == game.getVp())) {
            DisasterWatchHelper.sendMessageInFlameWatch(
                    game,
                    "### Just According to _Keikaku_ - " + player.getRepresentation() + " has won " + game.getName()
                            + " by scoring _Become A Martyr_.");
        } else if (!game.isFowMode()) {
            DisasterWatchHelper.sendMessageInFlameWatch(
                    game,
                    "### Every _Silver Flame_ has a silver lining; " + player.getRepresentation()
                            + " has scored _Become A Martyr_ in " + game.getName() + ".");
        }
    }
}

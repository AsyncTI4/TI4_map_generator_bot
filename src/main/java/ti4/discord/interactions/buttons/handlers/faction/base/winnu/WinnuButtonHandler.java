package ti4.discord.interactions.buttons.handlers.faction.base.winnu;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.unit.AddUnitService;
import ti4.spring.service.statistics.overrule.OverruleStatsService;

@UtilityClass
class WinnuButtonHandler {

    @ButtonHandler("winnuStructure_")
    public static void winnuStructure(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String replaced = buttonID.replace("winnuStructure_", "");
        String[] split = replaced.split("_");
        String unit = split[0];
        String planet = split[1];
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        AddUnitService.addUnits(event, tile, game, player.getColor(), unit + " " + planet);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " placed a " + unit + " on " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
        CommanderUnlockCheckService.checkPlayer(player, "titans", "saar", "rohdhna", "cheiran", "celdauri");
    }

    @ButtonHandler("winnuHero_")
    public static void resolveWinnuHeroSC(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        int sc = Integer.parseInt(buttonID.split("_")[1]);
        boolean isOverrule = buttonID.contains("overrule");
        boolean isBlueReverieOverrule = buttonID.contains("overrule_br");
        if (isBlueReverieOverrule
                && ((game.getScPlayed().get(sc) != null && game.getScPlayed().get(sc))
                        || player.getStrategicCC() < 1)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + ", that strategy card is no longer eligible for _Overrule_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (isBlueReverieOverrule) {
            player.setStrategicCC(player.getStrategicCC() - 1);
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event, "resolved _Overrule_");
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " spent 1 command token from their strategy pool to resolve _Overrule_.");
        }
        PlayStrategyCardService.playSC(event, sc, game, game.getMainGameChannel(), player, true, isOverrule);
        if (isOverrule) {
            OverruleStatsService.get().recordChoice(game.getName(), Helper.getSCName(sc, game));
        }
        if (isOverrule && sc == 5 && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    player.getRepresentationUnfogged() + ", you __cannot__ replenish other players' commodities.");
        }
        if (!isOverrule) {
            offerFollowPermissionButtons(game, player, sc);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void offerFollowPermissionButtons(Game game, Player player, int sc) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            String buttonID = player.factionButtonChecker() + "winnuHeroAllowFollow_" + sc + "_" + p2.getFaction();
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(buttonID, p2.getColor()));
            } else {
                buttons.add(Buttons.gray(buttonID, p2.getFactionModel().getShortName(), p2.getFactionEmoji()));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Done Choosing"));
        String message = player.getRepresentation()
                + ", choose which players may perform the secondary ability of **" + Helper.getSCName(sc, game)
                + "**. Each player you choose will be pinged with buttons to follow it.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("winnuHeroAllowFollow_")
    public static void allowWinnuHeroFollow(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String[] split = buttonID.replace("winnuHeroAllowFollow_", "").split("_");
        int sc = Integer.parseInt(split[0]);
        Player follower = game.getPlayerFromColorOrFaction(split[1]);
        if (follower == null) {
            return;
        }
        follower.removeFollowedSC(sc);
        String scName = Helper.getSCName(sc, game);
        String message = follower.getRepresentationUnfogged() + ", " + player.getFactionEmojiOrColor()
                + " has allowed you to perform the secondary ability of **" + scName + "**.";
        if (!"leadership".equalsIgnoreCase(scName)) {
            message += " If you do, you must spend a command token from your strategy pool like normal.";
        }
        MessageHelper.sendMessageToChannelWithButtons(
                follower.getCorrectChannel(),
                message,
                PlayStrategyCardService.getStrategyCardButtons(sc, game, player));
        ButtonHelper.deleteTheOneButton(event);
    }
}

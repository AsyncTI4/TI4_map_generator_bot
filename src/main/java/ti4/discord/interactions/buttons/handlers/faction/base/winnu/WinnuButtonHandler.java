package ti4.discord.interactions.buttons.handlers.faction.base.winnu;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.strategycard.PlayStrategyCardService;
import ti4.service.unit.AddUnitService;

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
        PlayStrategyCardService.playSC(event, sc, game, game.getMainGameChannel(), player, true, isOverrule);
        if (isOverrule) {
            game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, Helper.getSCName(sc, game));
        }
        if (isOverrule && sc == 5 && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(),
                    player.getRepresentationUnfogged() + ", you __cannot__ replenish other players' commodities.");
        }
        if (!isOverrule) {
            if ("leadership".equalsIgnoreCase(Helper.getSCName(sc, game))) {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        game.getPing()
                                + " reminder that the you must get the Winnu player's permission before you follow this.");
            } else {
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        game.getPing()
                                + " reminder that the you must get the Winnu player's permission before you follow this,"
                                + " and that if you do follow, you must spend command tokens from your strategy pool like normal.");
            }
        }
        ButtonHelper.deleteMessage(event);
    }
}

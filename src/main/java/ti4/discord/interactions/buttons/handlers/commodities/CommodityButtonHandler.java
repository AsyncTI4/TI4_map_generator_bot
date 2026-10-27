package ti4.discord.interactions.buttons.handlers.commodities;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperStats;

@UtilityClass
class CommodityButtonHandler {

    @ButtonHandler("gain_1_comms")
    public static void gain1Comm(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 1, true);
    }

    @ButtonHandler("gain_2_comms")
    public static void gain2Comms(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 2, true);
    }

    @ButtonHandler("gain_3_comms")
    public static void gain3Comms(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 3, true);
    }

    @ButtonHandler("gain_4_comms")
    public static void gain4Comms(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 4, true);
    }

    @ButtonHandler("gain_1_comms_stay")
    public static void gain1CommKeepMessage(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 1, false);
    }

    @ButtonHandler("gain_2_comms_stay")
    public static void gain2CommsKeepMessage(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 2, false);
    }

    @ButtonHandler("gain_3_comms_stay")
    public static void gain3CommsKeepMessage(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 3, false);
    }

    @ButtonHandler("gain_4_comms_stay")
    public static void gain4CommsKeepMessage(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 4, false);
    }

    @ButtonHandler("convert_1_comms")
    public static void convert1Comm(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.convertComms(event, game, player, 1);
    }

    @ButtonHandler("convert_2_comms")
    public static void convert2Comms(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.convertComms(event, game, player, 2, true);
    }

    @ButtonHandler("convert_3_comms")
    public static void convert3Comms(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.convertComms(event, game, player, 3);
    }

    @ButtonHandler("convert_4_comms")
    public static void convert4Comms(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.convertComms(event, game, player, 4);
    }

    @ButtonHandler("convert_2_comms_stay")
    public static void convert2CommsKeepMessage(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.convertComms(event, game, player, 2, false);
    }

    @ButtonHandler("resolveHarness")
    public static void replenishFromHarness(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.replenishComms(event, game, player, false);
    }
}

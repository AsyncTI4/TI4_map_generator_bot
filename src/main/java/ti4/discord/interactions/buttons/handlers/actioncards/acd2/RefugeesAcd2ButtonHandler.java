package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class RefugeesAcd2ButtonHandler {

    @ButtonHandler("resolveRefugees")
    public static void resolveRefugees(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player || getRefugeesPlanets(game, p2).isEmpty()) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("resolveRefugeesStep2_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button = Buttons.gray(
                        "resolveRefugeesStep2_" + p2.getFaction(),
                        p2.getFactionModel().getShortName());
                String factionEmojiString = p2.getFactionEmoji();
                buttons.add(button.withEmoji(Emoji.fromFormatted(factionEmojiString)));
            }
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.toString() + " has no valid _Refugees_ targets.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        buttons.add(Buttons.red("deleteButtons", "Done"));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose which player's planets to place infantry on.",
                buttons);
    }

    @ButtonHandler("resolveRefugeesStep2_")
    public static void resolveRefugeesStep2(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace("resolveRefugeesStep2_", ""));
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find the selected player for _Refugees_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        sendRefugeesPlanetButtons(player, game, target, 2);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveRefugeesStep3_")
    public static void resolveRefugeesStep3(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String[] payload = buttonID.replace("resolveRefugeesStep3_", "").split("_", 3);
        if (payload.length < 3) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Refugees_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player target = game.getPlayerFromColorOrFaction(payload[0]);
        int infantryRemaining = Integer.parseInt(payload[1]);
        String planet = payload[2];
        Tile tile = game.getTileContainingPlanet(planet);
        if (target == null || tile == null) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Refugees_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 infantry " + planet);
        game.removeStoredValue("coexistFlag");
        ButtonHelperAbilities.oceanBoundCheck(game);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " placed 1 infantry into coexistence on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Refugees_.");

        ButtonHelper.deleteMessage(event);
        if (infantryRemaining > 1) {
            sendRefugeesPlanetButtons(player, game, target, infantryRemaining - 1);
        }
    }

    private static void sendRefugeesPlanetButtons(Player player, Game game, Player target, int infantryRemaining) {
        List<String> eligiblePlanets = getRefugeesPlanets(game, target);
        if (eligiblePlanets.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not find any eligible planets for _Refugees_ outside " + target.getRepresentationUnfogged()
                            + "'s home system.");
            return;
        }

        List<Button> buttons = new ArrayList<>(eligiblePlanets.stream()
                .map(planet -> Buttons.green(
                        "resolveRefugeesStep3_" + target.getFaction() + "_" + infantryRemaining + "_" + planet,
                        Helper.getPlanetRepresentation(planet, game)))
                .toList());
        buttons.add(Buttons.red("deleteButtons", "Done"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose where to place "
                        + (infantryRemaining == 1 ? "your last infantry" : "an infantry")
                        + " for _Refugees_ on " + target.getRepresentationUnfogged() + "'s worlds.",
                buttons);
    }

    private static List<String> getRefugeesPlanets(Game game, Player target) {
        Set<String> planets = new HashSet<>();
        Tile homeSystem = target.getHomeSystemTile();
        for (Tile tile : game.getTiles()) {
            if (tile == null || tile == homeSystem) {
                continue;
            }
            for (UnitHolder unitHolder : tile.getPlanetUnitHolders()) {
                if (ButtonHelper.getPlayersWithUnitsOnAPlanet(game, unitHolder).contains(target)) {
                    planets.add(unitHolder.getName());
                }
            }
        }
        List<String> eligiblePlanets = new ArrayList<>(planets);
        Collections.sort(eligiblePlanets);
        return eligiblePlanets;
    }
}

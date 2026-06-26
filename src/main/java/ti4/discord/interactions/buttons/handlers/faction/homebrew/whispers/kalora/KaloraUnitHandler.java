package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class KaloraUnitHandler {

    public static void flagshipBombardmentReroll(
            Player player, MessageChannel channel, String tilePos, List<String> planets) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : planets) {
            buttons.add(Buttons.blue(
                    player.factionButtonChecker() + "kaloraFlagshipReroll_" + tilePos + "_" + planet,
                    "Reroll vs. " + planet));
        }
        buttons.add(Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Delete These Buttons"));
        MessageHelper.sendMessageToChannelWithButtons(
                channel,
                player.getFactionEmoji()
                        + " **Razor's Edge**: you may reroll all bombardment dice against each planet.",
                buttons);
    }

    @ButtonHandler("kaloraFlagshipReroll_")
    public static void kaloraFlagshipReroll(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("kaloraFlagshipReroll_", "").split("_", 2);
        String tilePos = parts[0];
        String planet = parts[1];
        game.setStoredValue("bombardmentTarget" + player.getFaction(), planet);
        CombatRollService.secondHalfOfCombatRoll(
                player, game, event, game.getTileByPosition(tilePos), "space", CombatRollType.bombardment, false);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("kaloraExploreFront_")
    public static void kaloraExploreFront(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("kaloraExploreFront_", "");
        ExploreService.expFront(event, game.getTileByPosition(pos), game, player, true, null);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    public static void offerMechButtons(Player player, Game game, Tile tile) {
        boolean hasMechOnTakenPlanet = tile.getPlanetUnitHolders().stream()
                .anyMatch(uH -> uH.getUnitCount(UnitType.Mech, player.getColor()) > 0
                        && game.getStoredValue("planetsTakenThisRound").contains(uH.getName()));
        if (!hasMechOnTakenPlanet) return;

        List<Button> buttons = new ArrayList<>();
        for (Tile t : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            if (t.getPlanetUnitHolders().isEmpty()) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "kaloraExploreFront_" + t.getPosition(),
                        t.getRepresentationForButtons(game, player),
                        ExploreEmojis.Frontier));
            }
        }
        if (!buttons.isEmpty()) {
            buttons.add(Buttons.red(player.factionButtonChecker() + "deleteButtons", "Delete these buttons"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you may explore the frontier deck in a planetless system containing your ships via **Broodwatcher**.",
                    buttons);
        }
    }
}

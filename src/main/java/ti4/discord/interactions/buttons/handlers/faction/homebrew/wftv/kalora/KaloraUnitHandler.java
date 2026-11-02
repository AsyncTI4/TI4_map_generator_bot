package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.kalora;

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
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
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

    public static void offerDreadnoughtTradeGoods(
            Player player, UnitModel unitModel, CombatRollType rollType, List<Die> resultRolls, int numOfUnit) {
        if (rollType != CombatRollType.bombardment) return;
        if (!"kalora_dreadnought".equals(unitModel.getId()) && !"kalora_dreadnought2".equals(unitModel.getId())) return;
        if (numOfUnit < 1 || resultRolls.isEmpty()) return;
        String trigger = "kalora_dreadnought".equals(unitModel.getId()) ? "rolled a 9 or 10" : "produced a hit";
        StringBuilder msg = new StringBuilder(player.getRepresentation())
                .append(", you may gain 1 trade good for each _")
                .append(unitModel.getName())
                .append("_ that ")
                .append(trigger)
                .append(" for **BOMBARDMENT**.");
        int lastUnitStart = Math.min(numOfUnit - 1, resultRolls.size());
        for (int unit = 0; unit < lastUnitStart; unit++) {
            msg.append("\n- ")
                    .append(unitModel.getName())
                    .append(" #")
                    .append(unit + 1)
                    .append(": ")
                    .append(resultRolls.get(unit).getResult());
        }
        msg.append("\n- ")
                .append(unitModel.getName())
                .append(" #")
                .append(lastUnitStart + 1)
                .append(": ");
        for (int die = lastUnitStart; die < resultRolls.size(); die++) {
            if (die > lastUnitStart) msg.append(", ");
            msg.append(resultRolls.get(die).getResult());
            if (die > lastUnitStart) msg.append(" (_Plasma Scoring_/extra die)");
        }
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + "kaloraDreadnoughtTG", "Gain 1 Trade Good", MiscEmojis.tg),
                Buttons.gray(player.factionButtonChecker() + "deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg.toString(), buttons);
    }

    @ButtonHandler("kaloraDreadnoughtTG")
    public static void kaloraDreadnoughtTG(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainTGs(event, game, player, 1, false);
    }

    public static UnitModel preferDreadnoughtForBombardment(
            UnitModel best, List<UnitModel> units, CombatRollType rollType, Player player) {
        if (rollType != CombatRollType.bombardment || !"kalora_flagship".equals(best.getId())) return best;
        int bestHitsOn = best.getCombatDieHitsOnForAbility(rollType, player);
        return units.stream()
                .filter(unit -> unit.getId().startsWith("kalora_dreadnought"))
                .filter(unit -> unit.getCombatDieHitsOnForAbility(rollType, player) == bestHitsOn)
                .findFirst()
                .orElse(best);
    }
}

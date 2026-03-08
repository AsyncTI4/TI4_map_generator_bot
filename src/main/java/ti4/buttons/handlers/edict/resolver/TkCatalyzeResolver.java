package ti4.buttons.handlers.edict.resolver;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.RandomHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.map.FractureService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.DestroyUnitService;

public class TkCatalyzeResolver implements EdictResolver {

    @Getter
    public String edict = "tk-catalyze";

    private static List<Button> buttons(Player player) {
        List<Button> buttons = new ArrayList<>();
        String draw = player.finChecker() + "drawSingularNewSpliceCard_";
        buttons.add(Buttons.green(draw + "ability", "Draw 1 Ability", MiscEmojis.tf_ability));
        buttons.add(Buttons.green(draw + "units", "Draw 1 Unit Upgrade", TechEmojis.UnitUpgradeTech));
        buttons.add(Buttons.green(draw + "genome", "Draw 1 Genome", MiscEmojis.tf_genome));

        buttons.add(Buttons.red(player.finChecker() + "rollCatalyze", "Roll for Catalyze"));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    public void handle(ButtonInteractionEvent event, Game game, Player player) {
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), playerPing(player), buttons(player));
    }

    @ButtonHandler("rollCatalyze")
    private static void rollCatalyze(ButtonInteractionEvent event, Game game, Player player) {
        int result = new Die(0).getResult();
        if (result == 1 || result == 10) { // success
            if (FractureService.isFractureInPlay(game)) {
                // Destroy Styx
                Tile styx = game.getTileFromPlanet("styx");
                DestroyUnitService.destroyAllUnitsInSystem(event, styx, game, false);
                AddUnitService.addUnits(event, styx, game, game.getNeutralColor(), "2 dn, 1 dd, 3 inf s");
            } else {
                String msg = player.getRepresentation(false, false) + " rolled a " + DiceEmojis.getGreenDieEmoji(result)
                        + "! The Fracture is now in play! Ingress tokens will automatically have been placed in their position on the map, if there were no choices to be made.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                FractureService.spawnFracture(event, game);
                FractureService.spawnIngressTokens(event, game, player, null);
            }
        } else if (result == 6 && RandomHelper.isOneInX(10)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "> \"Thunder rolled...\n> It rolled a " + DiceEmojis.getGrayDieEmoji(6)
                            + ".\"\n> \\- Terry Pratchett, _Guards! Guards!_");
        } else { // fail
            String msg = player.getRepresentation(true, false) + " rolled a " + DiceEmojis.getGrayDieEmoji(result)
                    + ", better luck next time.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        }
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}

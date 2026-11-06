package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.unit.CheckUnitContainmentService;

@UtilityClass
public class AgentPrompts {

    private static final String ORIGINAL_COMMAND_TOKENS_KEY_PREFIX = "originalCCsFor";

    public static Message gainCommandTokens(Game game, Player target) {
        game.setStoredValue(ORIGINAL_COMMAND_TOKENS_KEY_PREFIX + target.getFaction(), target.getCCRepresentation());
        return Message.addressedTo(
                target,
                ", your current command tokens are " + target.getCCRepresentation()
                        + ". Use buttons to gain command tokens.",
                ButtonHelper.getGainCCButtons(target));
    }

    public static Message loseCommandTokens(Game game, Player target) {
        game.setStoredValue(ORIGINAL_COMMAND_TOKENS_KEY_PREFIX + target.getFaction(), target.getCCRepresentation());
        return Message.addressedTo(
                target,
                ", your current command tokens are " + target.getCCRepresentation() + ". Use buttons to lose tokens.",
                ButtonHelper.getLoseCCButtons(target));
    }

    public static Message removeInfantry(Game game, Player target) {
        return Message.addressedTo(
                target, ", you may use the buttons to remove infantry.", infantryRemovalButtons(game, target));
    }

    private static List<Button> infantryRemovalButtons(Game game, Player target) {
        List<Button> buttons = new ArrayList<>();
        for (Tile tile : CheckUnitContainmentService.getTilesContainingPlayersUnits(game, target, UnitType.Infantry)) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getUnitCount(UnitType.Infantry, target.getColor()) > 0) {
                    buttons.add(Buttons.green(
                            target.factionButtonChecker() + "jolNarAgentRemoval_" + tile.getPosition() + "_"
                                    + unitHolder.getName(),
                            "Remove infantry from " + ButtonHelper.getUnitHolderRep(unitHolder, tile, game)));
                }
            }
        }
        buttons.add(Buttons.red(target.factionButtonChecker() + "deleteButtons", "Done"));
        return buttons;
    }
}

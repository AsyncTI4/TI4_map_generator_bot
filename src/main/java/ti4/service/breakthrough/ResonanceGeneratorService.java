package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class ResonanceGeneratorService {

    private String resonanceRep() {
        return Mapper.getBreakthrough("crimsonbt").getNameRepresentation();
    }

    public void checkCrimsonCommanderUnlock(Game game, Player player, Tile tile) {
        if (player.hasLeader("crimsoncommander")) {
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) continue;
                if (tile.containsPlayersUnits(p2)) {
                    CommanderUnlockCheckService.checkPlayer(player, "crimson");
                    break;
                }
            }
        }
    }

    public void postInitialButtons(GenericInteractionCreateEvent event, Game game, Player player) {
        // not having a breach is not *technically* required, but like, why would you place an active breach if you can
        // instead flip the one there ??
        Predicate<Tile> unitsAndNoBreach = Tile.tileHasPlayerUnits(player)
                .and(Tile.tileHasBreach().negate())
                .and(tile -> !tile.isHomeSystem(game));
        List<Button> place =
                ButtonHelper.getTilesWithPredicateForAction(player, game, "placeBreach", unitsAndNoBreach, false);
        List<Button> flip =
                ButtonHelper.getTilesWithPredicateForAction(player, game, "flipBreach", Tile.tileHasBreach(), false);

        List<Button> buttons = new ArrayList<>();
        place.stream()
                .map(button -> button.withLabel("Place in " + button.getLabel()))
                .forEach(buttons::add);
        flip.stream()
                .map(button -> button.withLabel("Flip in " + button.getLabel()))
                .map(button -> button.withStyle(ButtonStyle.PRIMARY))
                .forEach(buttons::add);
        buttons.add(Buttons.red(player.finChecker() + "deleteButtons", "Delete these buttons"));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Choose a tile to place or flip a breach.", buttons);
    }

    @ButtonHandler("flipBreach_")
    private static void resolveFlipBreach(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String active = Constants.TOKEN_BREACH_ACTIVE, inactive = Constants.TOKEN_BREACH_INACTIVE;
        String pos = buttonID.replace("flipBreach_", "");
        Tile tile = game.getTileByPosition(pos);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);

        // TODO: JAZZ - idk if you can have multiple, but just prefer flipping inactive for now
        String oldState, newState;
        if (space.getTokenList().contains(inactive)) {
            space.removeToken(inactive);
            space.addToken(active);
            oldState = "inactive";
            newState = "active";
        } else {
            space.removeToken(active);
            space.addToken(inactive);
            oldState = "active";
            newState = "inactive";
        }

        String msg = player.getRepresentationNoPing() + " flipped " + oldState + " breach to " + newState + " in the "
                + tile.getRepresentationForButtons(game, player) + " system.";
        // msg += " using " + resonanceRep() + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeBreach_")
    private static void resolvePlaceBreach(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("placeBreach_", "");
        String source = resonanceRep();
        if (pos.contains("_")) {
            source = "an Exile destroyer";
            pos = pos.split("_")[0];
        }
        Tile tile = game.getTileByPosition(pos);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);

        // TODO: JAZZ - idk if you can have multiple, so don't double add for now
        if (!space.getTokenList().contains(Constants.TOKEN_BREACH_ACTIVE)) {
            space.addToken(Constants.TOKEN_BREACH_ACTIVE);
        }

        String msg = "Placed active breach in the " + tile.getRepresentation();
        msg += " system using " + source + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        checkCrimsonCommanderUnlock(game, player, tile);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeInactiveBreach_")
    private static void resolvePlaceInactiveBreach(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("placeInactiveBreach_", "");
        Tile tile = game.getTileByPosition(pos);
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);

        // TODO: JAZZ - idk if you can have multiple, so don't double add for now
        if (!space.getTokenList().contains(Constants.TOKEN_BREACH_INACTIVE)) {
            space.addToken(Constants.TOKEN_BREACH_INACTIVE);
        }

        String msg = "Placed inactive breach in the " + tile.getRepresentation();
        msg += " system using an Exile destroyer.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        checkCrimsonCommanderUnlock(game, player, tile);
        ButtonHelper.deleteMessage(event);
    }
}

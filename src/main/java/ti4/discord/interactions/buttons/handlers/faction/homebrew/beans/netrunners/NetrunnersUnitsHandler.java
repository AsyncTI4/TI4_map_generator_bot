package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.UnitQueryService;

@UtilityClass
public class NetrunnersUnitsHandler {

    public static final String FLAGSHIP_ID = "netrunners_flagship";
    public static final String MECH_ID = "netrunners_mech";

    public static void offerDeployMechWithStructure(
            GenericInteractionCreateEvent event,
            Game game,
            Tile tile,
            UnitKey unitKey,
            String unitHolderName,
            int count) {
        if (game == null || tile == null || unitKey == null || unitHolderName == null || count < 1) {
            return;
        }

        Player player = game.getPlayerFromColorOrFaction(unitKey.getColor());
        UnitHolder unitHolder = tile.getUnitHolder(unitHolderName);
        UnitModel placedUnit = player == null ? null : player.getUnitFromUnitKey(unitKey);
        if (player == null
                || unitHolder == null
                || placedUnit == null
                || !placedUnit.getIsStructure()
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || UnitQueryService.countUnits(game, player, "mech", true) >= 4
                || !(unitHolder instanceof Planet)) {
            return;
        }

        String message = player.getRepresentationUnfogged()
                + ", you placed a structure on " + Helper.getPlanetRepresentation(unitHolderName, game)
                + ". You may spend 1 resource to deploy 1 mech with that structure.";
        List<Button> buttons = List.of(
                Buttons.green(
                        player.factionButtonChecker() + "netrunnersDeployMech_" + tile.getPosition() + "_"
                                + unitHolderName,
                        "Pay 1r to Deploy Mech",
                        FactionEmojis.netrunners),
                Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("netrunnersDeployMech_")
    public static void resolveDeployMech(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.replace("netrunnersDeployMech_", "").split("_", 2);
        if (parts.length < 2) {
            return;
        }

        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder unitHolder = tile == null ? null : tile.getUnitHolder(parts[1]);
        if (tile == null
                || unitHolder == null
                || !player.hasUnit(MECH_ID)
                || ButtonHelper.isLawInPlay(game, "articles_war")
                || UnitQueryService.countUnits(game, player, "mech", true) >= 4
                || !(unitHolder instanceof Planet)) {
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), "1 mech " + unitHolder.getName());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " deployed 1 mech on "
                        + Helper.getPlanetRepresentation(unitHolder.getName(), game) + " with **Legion**.");

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        buttons.add(Buttons.red("deleteButtons", "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", please pay 1 resource for **Legion**.",
                buttons);
    }

    public static boolean empBlocksGroundForceProduction(Game game, Player player, Tile tile) {
        if (game == null || player == null || tile == null || player.hasUnit(FLAGSHIP_ID)) {
            return false;
        }
        return tile.getUnitHolderValues().stream()
                .anyMatch(unitHolder -> isSpaceDockBlockadedByEmp(game, player, tile, unitHolder));
    }

    public static boolean isGroundForceProductionButton(Button button) {
        String buttonId = button.getCustomId();
        return buttonId != null
                && (buttonId.contains("_infantry_") || buttonId.contains("_2gf_") || buttonId.contains("_mech_"));
    }

    public static boolean empBlocksSpaceCannonAgainst(Player target, Tile tile, CombatRollType rollType) {
        return (rollType == CombatRollType.SpaceCannonOffence || rollType == CombatRollType.SpaceCannonDefence)
                && target != null
                && ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP_ID, target, tile);
    }

    public static boolean empBlocksSpaceCannonAgainstOpponent(
            Game game, Player rollingPlayer, Tile tile, CombatRollType rollType) {
        return !getEmpProtectedOpponents(game, rollingPlayer, tile, rollType).isEmpty();
    }

    public static boolean resolveEmpSpaceCannonBlock(
            GenericInteractionCreateEvent event, Game game, Player rollingPlayer, Tile tile, CombatRollType rollType) {
        String message = getEmpSpaceCannonBlockMessage(game, rollingPlayer, tile, rollType);
        if (message.isEmpty()) {
            return false;
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        return true;
    }

    public static String getEmpSpaceCannonBlockMessage(
            Game game, Player rollingPlayer, Tile tile, CombatRollType rollType) {
        List<Player> protectedPlayers = getEmpProtectedOpponents(game, rollingPlayer, tile, rollType);
        if (protectedPlayers.isEmpty()) {
            return "";
        }
        return "SPACE CANNON cannot be used against "
                + protectedPlayers.stream()
                        .map(Player::getFactionEmojiOrColor)
                        .reduce((first, second) -> first + ", " + second)
                        .orElse("the protected player")
                + "'s units in this system due to **E.M.P**.";
    }

    public static String getEmpSpaceCannonBlockMessage(Player target, Tile tile, CombatRollType rollType) {
        if (!empBlocksSpaceCannonAgainst(target, tile, rollType)) {
            return "";
        }
        return "SPACE CANNON cannot be used against " + target.getFactionEmojiOrColor()
                + "'s units in this system due to **E.M.P**.";
    }

    private static List<Player> getEmpProtectedOpponents(
            Game game, Player rollingPlayer, Tile tile, CombatRollType rollType) {
        if (game == null || rollingPlayer == null || tile == null) {
            return List.of();
        }
        return game.getRealPlayersExcludingThis(rollingPlayer).stream()
                .filter(player -> empBlocksSpaceCannonAgainst(player, tile, rollType))
                .toList();
    }

    private static boolean isSpaceDockBlockadedByEmp(Game game, Player player, Tile tile, UnitHolder unitHolder) {
        if (game == null
                || player == null
                || tile == null
                || unitHolder == null
                || !unitHolder.hasUnit(UnitType.Spacedock, player.getColor())) {
            return false;
        }

        for (Player otherPlayer : game.getRealPlayersExcludingThis(player)) {
            if (player.hasAllianceMember(otherPlayer.getFaction())) {
                continue;
            }
            if (ButtonHelper.doesPlayerHaveFSHere(FLAGSHIP_ID, otherPlayer, tile)) {
                return true;
            }
        }
        return false;
    }
}

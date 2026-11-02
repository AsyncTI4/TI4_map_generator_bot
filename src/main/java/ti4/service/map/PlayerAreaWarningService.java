package ti4.service.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.fow.GMService;

public final class PlayerAreaWarningService {

    public static final String SHOW_SYSTEMS_PREFIX = "showWarningSystems_";

    private static final String PLASTIC = "plastic";
    private static final String BREACH = "breach";
    private static final String SEVER = "sever";
    private static final String GALVANIZE = "galvanize";

    private PlayerAreaWarningService() {}

    public record AffectedSystems(String description, List<String> positions) {}

    private record Warning(
            String message, String showSystemsId, @Nullable Supplier<List<Button>> extraButtons, boolean logToGm) {}

    public static void warnPlayer(Game game, Player player) {
        send(game, player, currentWarnings(game, player, unitsOnBoardIfPlasticIsLimited(game)));
    }

    public static void warnAllPlayers(Game game) {
        warnAllPlayersExcept(game, null);
    }

    public static void warnAllPlayersExcept(Game game, @Nullable Player skipped) {
        Map<UnitKey, Integer> unitsOnBoard = unitsOnBoardIfPlasticIsLimited(game);
        for (Player player : game.getRealPlayers()) {
            if (player != skipped) {
                send(game, player, currentWarnings(game, player, unitsOnBoard));
            }
        }
    }

    @Nullable
    public static AffectedSystems affectedSystems(Game game, String showSystemsId) {
        String[] parts = showSystemsId.substring(SHOW_SYSTEMS_PREFIX.length()).split("_");
        Player player = parts.length < 2 ? null : game.getPlayerFromColorOrFaction(parts[0]);
        if (player == null) {
            return null;
        }
        return switch (parts[1]) {
            case PLASTIC -> parts.length < 3 ? null : plasticSystems(game, player, parts[2]);
            case BREACH -> new AffectedSystems("Breach tokens", PlayerAreaSupplies.breachPositions(game));
            case SEVER -> new AffectedSystems("Sever tokens", PlayerAreaSupplies.severPositions(game));
            case GALVANIZE -> new AffectedSystems("Galvanized units", PlayerAreaSupplies.galvanizedPositions(game));
            default -> null;
        };
    }

    private static AffectedSystems plasticSystems(Game game, Player player, String unitID) {
        UnitKey unitKey = Mapper.getUnitKey(unitID, player.getColor());
        return new AffectedSystems(
                unitKey.unitType().humanReadableName() + " units", PlayerAreaSupplies.positionsWithUnit(game, unitKey));
    }

    private static Map<UnitKey, Integer> unitsOnBoardIfPlasticIsLimited(Game game) {
        if (game.isHasEnded() || !game.isCcNPlasticLimit()) {
            return Map.of();
        }
        return PlayerAreaSupplies.unitsOnBoard(game);
    }

    private static List<Warning> currentWarnings(Game game, Player player, Map<UnitKey, Integer> unitsOnBoard) {
        List<Warning> warnings = new ArrayList<>();
        if (player.getFaction() == null || player.getColor() == null || "null".equals(player.getColor())) {
            return warnings;
        }
        addPlasticWarnings(warnings, game, player, unitsOnBoard);
        addCrimsonRebellionWarnings(warnings, game, player);
        addGalvanizeWarning(warnings, game, player);
        return warnings;
    }

    private static void addPlasticWarnings(
            List<Warning> warnings, Game game, Player player, Map<UnitKey, Integer> unitsOnBoard) {
        if (unitsOnBoard.isEmpty()) {
            return;
        }
        for (String unitID : Mapper.getUnitIDList()) {
            ReinforcementStock stock = ReinforcementStock.of(game, player, unitID, unitsOnBoard);
            if (stock != null && stock.overLimit(game)) {
                warnings.add(plasticWarning(game, player, stock));
            }
        }
    }

    private static Warning plasticWarning(Game game, Player player, ReinforcementStock stock) {
        String asyncID = stock.unitKey().asyncID();
        String message = player.getRepresentationUnfogged()
                + " is exceeding unit plastic or cardboard limits for "
                + stock.unitKey().unitType().humanReadableName()
                + ". Use buttons to remove (note that you cannot remove plastic pieces from systems with your command token in them).";
        return new Warning(
                message,
                showSystemsId(player, PLASTIC + "_" + asyncID),
                () -> ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, asyncID),
                true);
    }

    private static void addCrimsonRebellionWarnings(List<Warning> warnings, Game game, Player player) {
        if (player.hasAbility("incursion") && PlayerAreaSupplies.breachCount(game) > PlayerAreaSupplies.BREACH_TOKENS) {
            warnings.add(tooManyTokens(player, BREACH, "Breach tokens"));
        }
        if (player.ownsPromissoryNote("sever")
                && PlayerAreaSupplies.severCount(game) > PlayerAreaSupplies.SEVER_TOKENS) {
            warnings.add(tooManyTokens(player, SEVER, "Sever tokens"));
        }
    }

    private static Warning tooManyTokens(Player player, String kind, String tokens) {
        String message = player.getRepresentationUnfogged() + ", there are too many " + tokens
                + " on the board. Please review and resolve manually.";
        return new Warning(message, showSystemsId(player, kind), null, false);
    }

    private static void addGalvanizeWarning(List<Warning> warnings, Game game, Player player) {
        if (player.hasAbility("galvanize")
                && PlayerAreaSupplies.galvanizeOverLimit(game, PlayerAreaSupplies.galvanizedCount(game))) {
            String message = player.getRepresentationUnfogged()
                    + ", there are too many Galvanized units on the board. Please review and resolve manually.";
            warnings.add(new Warning(message, showSystemsId(player, GALVANIZE), null, false));
        }
    }

    private static String showSystemsId(Player player, String kind) {
        return SHOW_SYSTEMS_PREFIX + player.getColor() + "_" + kind;
    }

    private static void send(Game game, Player player, List<Warning> warnings) {
        for (Warning warning : warnings) {
            List<Button> buttons = buttonsOf(game, warning);
            if (buttons.isEmpty()) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), warning.message());
            } else {
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), warning.message(), buttons);
            }
            if (warning.logToGm()) {
                GMService.logPlayerActivity(game, player, warning.message());
            }
        }
    }

    private static List<Button> buttonsOf(Game game, Warning warning) {
        List<Button> buttons = new ArrayList<>();
        if (!game.isFowMode()) {
            buttons.add(Buttons.gray(warning.showSystemsId(), "Show Systems"));
        }
        if (warning.extraButtons() != null) {
            buttons.addAll(warning.extraButtons().get());
        }
        return buttons;
    }
}

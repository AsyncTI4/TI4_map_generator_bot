package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners;

import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;

@UtilityClass
public class NetrunnersFactionTechsHandler {

    public static final String DATA_MINING_TECH = "benetrunnersdm";
    private static final String SIPHON_I_UNIT = "netrunners_spacedock";
    private static final String SIPHON_II_UNIT = "netrunners_spacedock2";
    private static final String DATA_MINING_RESOLVED = "dataMiningResolved";
    private static final String SIPHON_DISCOUNT_APPLIED = "siphonDiscountApplied";

    public static void resolveDataMining(Game game) {
        for (Player netrunner : game.getRealPlayers()) {
            if (netrunner.hasTech(DATA_MINING_TECH)) {
                resolveDataMiningForPlayer(game, netrunner);
            }
        }
    }

    private static void resolveDataMiningForPlayer(Game game, Player netrunner) {
        String key = DATA_MINING_RESOLVED + game.getRound() + netrunner.getFaction();
        if (!game.getStoredValue(key).isEmpty()) {
            return;
        }
        game.setStoredValue(key, "true");

        int tokenCount = getHackermanTokenCount(game, netrunner);
        int tgGain = tokenCount / 2;
        if (tgGain < 1) {
            return;
        }

        MessageHelper.sendMessageToChannel(
                netrunner.getCorrectChannel(),
                netrunner.getRepresentation() + " gained " + StringHelper.pluralize(tgGain, "trade good")
                        + " from **Data Mining** with " + StringHelper.pluralize(tokenCount, "control token")
                        + " in their **"
                        + NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL + "** pool. "
                        + netrunner.gainTG(tgGain, true));
    }

    public static int applySiphonDiscount(Game game, Player player, int cost) {
        int discount = Math.min(cost, getSiphonDiscount(game, player));
        if (discount < 1) {
            game.removeStoredValue(SIPHON_DISCOUNT_APPLIED + player.getFaction());
            return cost;
        }
        game.setStoredValue(SIPHON_DISCOUNT_APPLIED + player.getFaction(), Integer.toString(discount));
        return Math.max(0, cost - discount);
    }

    public static String getSiphonDiscountMessage(Game game, Player player) {
        String appliedDiscount = game.getStoredValue(SIPHON_DISCOUNT_APPLIED + player.getFaction());
        int discount = appliedDiscount.isEmpty() ? 0 : Integer.parseInt(appliedDiscount);
        if (discount < 1) {
            return "";
        }
        String siphonName = player.ownsUnit(SIPHON_II_UNIT) ? "Siphon II" : "Siphon I";
        return "\n**" + siphonName + "** discounted this build by " + StringHelper.pluralize(discount, "resource")
                + ".";
    }

    private static int getSiphonDiscount(Game game, Player player) {
        int divisor = getSiphonDiscountDivisor(game, player);
        if (divisor < 1) {
            return 0;
        }
        return getHackermanTokenCount(game, player) / divisor;
    }

    private static int getSiphonDiscountDivisor(Game game, Player player) {
        if (!isProducingWithSiphon(game, player)) {
            return 0;
        }
        if (player.ownsUnit(SIPHON_II_UNIT)) {
            return 2;
        }
        if (player.ownsUnit(SIPHON_I_UNIT)) {
            return 4;
        }
        return 0;
    }

    private static boolean isProducingWithSiphon(Game game, Player player) {
        for (String producedUnit : player.getCurrentProducedUnits().keySet()) {
            String[] parts = producedUnit.split("_");
            if (parts.length < 2) {
                continue;
            }
            Tile tile = game.getTileByPosition(parts[1]);
            if (tileHasSiphonII(player, tile)) {
                return true;
            }
        }
        return false;
    }

    private static boolean tileHasSiphonII(Player player, Tile tile) {
        if (tile == null) {
            return false;
        }
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int getHackermanTokenCount(Game game, Player netrunner) {
        int tokenCount = 0;
        for (Player otherPlayer : game.getRealPlayersExcludingThis(netrunner)) {
            tokenCount +=
                    netrunner.getDebtTokenCount(otherPlayer.getColor(), NetrunnersAbilitiesHandler.SYSTEM_BREACH_POOL);
        }
        return tokenCount;
    }
}

package ti4.discord.interactions.commands.developer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.SanityLimits;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.game.persistence.GameManager;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RunAgainstSpecificGame extends Subcommand {

    RunAgainstSpecificGame() {
        super("run_against_specific_game", "Runs this custom code against a specific game.");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "The game to run the command against.")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String gameName = event.getOption(Constants.GAME_NAME).getAsString();
        if (!GameManager.isValid(gameName)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can't find map for game name " + gameName);
            return;
        }

        ExecutionLockManager.wrapWithLockAndRelease(gameName, ExecutionLockType.WRITE, () -> {
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(), "Running custom command against " + gameName + ".");

                    Game game = GameManager.getManagedGame(gameName).getGame();
                    boolean changed = makeChanges(game);
                    if (changed) {
                        BotLogger.info("Changes made to " + game.getName() + ".");
                        GameManager.save(
                                game, "Developer ran custom command against this game, probably migration related.");
                        MessageHelper.sendMessageToChannel(
                                event.getChannel(), "Finished custom command against " + game.getName() + ".");
                    } else {
                        MessageHelper.sendMessageToChannel(
                                event.getChannel(), "No changes required for " + game.getName() + ".");
                    }
                })
                .run();
    }

    private static boolean makeChanges(Game game) {
        boolean changed = false;
        for (Tile tile : game.getTileMap().values()) {
            changed |= capUnitsInTile(game, tile);
        }
        for (Player player : game.getPlayers().values()) {
            changed |= capUnitsInTile(game, player.getNomboxTile());
            changed |= capPlayerCounts(player);
            changed |= capDebtTokens(player);
            for (Leader leader : player.getLeaders()) {
                if (leader.getTgCount() > SanityLimits.TRADE_GOODS_ON_CARD) {
                    leader.setTgCount(SanityLimits.TRADE_GOODS_ON_CARD);
                    changed = true;
                }
            }
        }
        changed |= capValues(game.getScTradeGoods(), SanityLimits.TRADE_GOODS_ON_CARD);
        return changed;
    }

    private static boolean capUnitsInTile(Game game, Tile tile) {
        if (tile == null) return false;
        boolean changed = false;
        Map<UnitKey, Integer> roomInTile = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            for (Map.Entry<UnitKey, List<Integer>> entry :
                    unitHolder.getUnitsByState().entrySet()) {
                UnitKey unitKey = entry.getKey();
                int room = SanityLimits.isTokenBacked(unitKey.unitType())
                        ? roomInTile.getOrDefault(unitKey, SanityLimits.INFANTRY_AND_FIGHTERS_PER_TILE)
                        : SanityLimits.reinforcementLimit(game, unitKey);
                List<Integer> capped = UnitHolder.capStates(entry.getValue(), room);
                roomInTile.put(
                        unitKey,
                        room - capped.stream().mapToInt(Integer::intValue).sum());
                if (!capped.equals(entry.getValue())) {
                    entry.setValue(capped);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean capPlayerCounts(Player player) {
        boolean changed = false;
        changed |= capCount(player.getTacticalCC(), SanityLimits.COMMAND_TOKENS_PER_POOL, player::setTacticalCC);
        changed |= capCount(player.getFleetCC(), SanityLimits.COMMAND_TOKENS_PER_POOL, player::setFleetCC);
        changed |= capCount(player.getStrategicCC(), SanityLimits.COMMAND_TOKENS_PER_POOL, player::setStrategicCC);
        changed |= capCount(player.getTg(), SanityLimits.TRADE_GOODS, player::setTg);
        changed |= capCount(player.getCommodities(), SanityLimits.COMMODITIES, player::setCommodities);
        changed |= capCount(player.getStasisInfantry(), SanityLimits.STASIS_UNITS, player::setStasisInfantry);
        changed |= capCount(player.getStasisFighters(), SanityLimits.STASIS_UNITS, player::setStasisFighters);
        return changed;
    }

    private static boolean capCount(int current, int max, IntConsumer setter) {
        if (current <= max) return false;
        setter.accept(max);
        return true;
    }

    private static boolean capDebtTokens(Player player) {
        boolean changed = false;
        for (Map<String, Integer> pool : player.getAllDebtTokens().values()) {
            changed |= capValues(pool, SanityLimits.DEBT_TOKENS_PER_COLOR);
        }
        return changed;
    }

    private static <K> boolean capValues(Map<K, Integer> counts, int max) {
        boolean changed = counts.values().stream().anyMatch(count -> count != null && count > max);
        counts.replaceAll((key, count) -> count == null ? null : Math.min(count, max));
        return changed;
    }
}

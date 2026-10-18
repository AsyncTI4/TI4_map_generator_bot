package ti4.discord.interactions.commands.special;

import java.util.ArrayList;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.service.RemoveCommandCounterService;

class MoveAllUnits extends GameStateSubcommand {

    public MoveAllUnits() {
        super(
                Constants.MOVE_ALL_UNITS,
                "Move All Units From One System To Another (tile_name ALL gathers the whole board)",
                true,
                true);
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.TILE_NAME,
                        "System to move from, or ALL for every system on the board")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to move to")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.CC_USE,
                        "\"t\"/\"tactic\" to add a token from tactic pool, \"r\"/\"retreat\" to add a token from reinforcements")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        boolean fromEveryTile = movesFromEveryTile(event);

        Tile tileFrom = null;
        if (!fromEveryTile) {
            tileFrom = CommandHelper.getTile(event, game);
            if (tileFrom == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find the system you're moving from.");
                return;
            }
        }

        Tile tileTo = CommandHelper.getTile(
                event, game, event.getOption(Constants.TILE_NAME_TO).getAsString());
        if (tileTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find the system you're moving to.");
            return;
        }

        Player player = getPlayer();
        UnitHolder destination = tileTo.getUnitHolders().get(Constants.SPACE);
        if (fromEveryTile) {
            reportGathering(event, player, tileTo, gatherFromEveryTile(game, player, tileTo, destination));
        } else {
            moveUnitsOfPlayer(player, tileFrom, destination);
        }

        applyCommandCounter(event, game, player, tileTo);
    }

    private static boolean movesFromEveryTile(SlashCommandInteractionEvent event) {
        return Constants.ALL.equals(
                event.getOption(Constants.TILE_NAME).getAsString().trim());
    }

    static GatheredUnits gatherFromEveryTile(Game game, Player player, Tile tileTo, UnitHolder destination) {
        int units = 0;
        int systems = 0;
        for (Tile tileFrom : new ArrayList<>(game.getTileMap().values())) {
            if (tileFrom == tileTo) continue;
            int moved = moveUnitsOfPlayer(player, tileFrom, destination);
            if (moved > 0) {
                units += moved;
                systems++;
            }
        }
        return new GatheredUnits(units, systems);
    }

    static int moveUnitsOfPlayer(Player player, Tile tileFrom, UnitHolder destination) {
        int movedUnits = 0;
        for (UnitHolder source : tileFrom.getUnitHolders().values()) {
            if (source == destination) continue;
            for (UnitKey key : source.getUnitKeysForPlayer(player)) {
                destination.addUnitsWithStates(key, source.getUnitsByState().get(key));
                movedUnits += source.getUnitCount(key);
            }
            source.getUnitsByState().keySet().removeIf(player::unitBelongsToPlayer);
        }
        return movedUnits;
    }

    private static void reportGathering(
            SlashCommandInteractionEvent event, Player player, Tile tileTo, GatheredUnits gathered) {
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.fogSafeEmoji() + " gathered " + StringHelper.pluralize(gathered.units(), "unit") + " from "
                        + StringHelper.pluralize(gathered.systems(), "system") + " into " + tileTo.getPosition() + ".");
    }

    private static void applyCommandCounter(SlashCommandInteractionEvent event, Game game, Player player, Tile tileTo) {
        OptionMapping optionCC = event.getOption(Constants.CC_USE);
        if (optionCC != null) {
            String value = optionCC.getAsString().toLowerCase();
            if ("t".equals(value) || "tactic".equals(value) || "t/tactic".equals(value)) {
                RemoveCommandCounterService.fromTacticsPool(event, player.getColor(), tileTo, game);
            }
            if (!"no".equals(value)) {
                CommandCounterHelper.addCC(event, player, tileTo, false);
            }
            Helper.isCCCountCorrect(player);
        }
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }

    record GatheredUnits(int units, int systems) {}
}

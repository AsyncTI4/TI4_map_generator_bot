package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.commands2.commandcounter.RemoveCommandCounterService;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

class MoveAllUnits extends GameStateSubcommand {

    public MoveAllUnits() {
        super(Constants.MOVE_ALL_UNITS, "Move All Units From One System To Another", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name to move from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME_TO, "System/Tile name to move to").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CC_USE, "Type t or tactics to add a CC from tactics, r or retreat to add a CC without taking it from tactics").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tileFrom = CommandHelper.getTile(event, game);
        if (tileFrom == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find the tile you're moving from.");
            return;
        }

        Tile tileTo = CommandHelper.getTile(event, game, event.getOption(Constants.TILE_NAME_TO).getAsString());
        if (tileTo == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not find the tile you're moving to.");
            return;
        }

        Player player = getPlayer();
        UnitHolder space = tileTo.getUnitHolders().get("space");
        for (UnitHolder uH : tileFrom.getUnitHolders().values()) {
            for (UnitKey key : uH.getUnits().keySet()) {
                if (!player.unitBelongsToPlayer(key)) continue;
                space.addUnit(key, uH.getUnits().get(key));
            }
            for (UnitKey key : uH.getUnitDamage().keySet()) {
                if (!player.unitBelongsToPlayer(key)) continue;
                space.addDamagedUnit(key, uH.getUnitDamage().get(key));
            }

            uH.removeAllUnits(player.getColor());

        }

        OptionMapping optionCC = event.getOption(Constants.CC_USE);
        if (optionCC != null) {
          String value = optionCC.getAsString().toLowerCase();
            if ("t".equals(value) || "tactics".equals(value) || "t/tactics".equals(value)) {
                RemoveCommandCounterService.fromTacticsPool(event, player.getColor(), tileTo, game);
            }
            if (!"no".equals(value)) {
                CommandCounterHelper.addCC(event, player.getColor(), tileTo, false);
            }
            Helper.isCCCountCorrect(event, game, player.getColor());
        }
    }
}

package ti4.commands.tokens;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.MoveUnits;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Tile;

public class AddCC extends AddRemoveToken {

    @Override
    void doAction(SlashCommandInteractionEvent event, List<String> colors, Tile tile, Game game) {
        boolean usedTactics = false;
        for (String color : colors) {
            OptionMapping option = event.getOption(Constants.CC_USE);
            if (option != null && !usedTactics) {
                usedTactics = true;
                String value = option.getAsString().toLowerCase();
                switch (value) {
                    case "t/tactics", "t", "tactics", "tac", "tact" -> MoveUnits.removeTacticsCC(event, color, tile, game);
                }
            }
            CommandCounterHelper.addCC(event, color, tile);
            Helper.isCCCountCorrect(event, game, color);
        }
    }

    @Override
    public String getDescription() {
        return "Add CC to tile/system";
    }

    @Override
    public String getName() {
        return Constants.ADD_CC;
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                        .setRequired(true)
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.CC_USE, "Type tactics or t, retreat, reinforcements or r")
                        .setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color")
                        .setAutoComplete(true));
    }
}

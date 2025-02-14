package ti4.commands.combat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;

class CombatRoll extends GameStateSubcommand {

    public CombatRoll() {
        super(
                Constants.COMBAT_ROLL,
                "*V2* *BETA* Combat rolls for units on tile. *Auto includes modifiers*",
                true,
                true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.PLANET,
                        "(optional) Space or planet to have combat at (default is space)")
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.STRING,
                Constants.COMBAT_ROLL_TYPE,
                "Switch to afb/bombardment/spacecannonoffence/spacecannondefence"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "roll for player (default you)")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping planetOption = event.getOption(Constants.PLANET);
        OptionMapping rollTypeOption = event.getOption(Constants.COMBAT_ROLL_TYPE);

        Player player = getPlayer();

        String unitHolderName = Constants.SPACE;
        if (planetOption != null) {
            unitHolderName = planetOption.getAsString();
        }

        // Get tile info
        String tileOption = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        String tileID = AliasHandler.resolveTile(tileOption);
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Tile " + tileOption + " not found");
            return;
        }

        CombatRollType rollType = CombatRollType.combatround;
        if (rollTypeOption != null) {
            if ("afb".equalsIgnoreCase(rollTypeOption.getAsString())) {
                rollType = CombatRollType.AFB;
            }
            if ("bombardment".equalsIgnoreCase(rollTypeOption.getAsString())) {
                rollType = CombatRollType.bombardment;
            }
            if ("spacecannonoffence".equalsIgnoreCase(rollTypeOption.getAsString())) {
                rollType = CombatRollType.SpaceCannonOffence;
            }
            if ("spacecannondefence".equalsIgnoreCase(rollTypeOption.getAsString())) {
                rollType = CombatRollType.SpaceCannonDefence;
            }
        }

        CombatRollService.secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType);
    }
}

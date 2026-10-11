package ti4.discord.interactions.commands.combat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;

class CombatRoll extends GameStateSubcommand {

    public CombatRoll() {
        super(
                Constants.COMBAT_ROLL,
                "*V2* *BETA* Combat rolls for units on tile. *Auto includes modifiers*",
                true,
                true);
        addOptions(CommonOptions.tileName());
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.PLANET,
                        "(optional) Space or planet to have combat at (default is space)")
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.STRING,
                Constants.COMBAT_ROLL_TYPE,
                "Switch to afb/bombardment/spacecannonoffence/spacecannondefence"));
        addOptions(CommonOptions.factionColor("roll for player (default you)"));
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
        Tile tile = CommandHelper.requireOrReport(
                event, TileHelper.getTile(event, tileOption, game), "Tile " + tileOption + " not found");
        if (tile == null) return;

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

package ti4.commands.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import org.apache.commons.lang3.StringUtils;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.CombatHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatRollType;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;

public class CombatRoll extends CombatSubcommandData {

    public CombatRoll() {
        super(Constants.COMBAT_ROLL,
                "*V2* *BETA* Combat rolls for units on tile. *Auto includes modifiers*");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET,
                "(optional) Planet to have combat on. Default is space combat.").setAutoComplete(true)
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_ROLL_TYPE,
                "switch to afb/bombardment/spacecannonoffence")
                .setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "roll for player (default you)")
                .setAutoComplete(true).setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        OptionMapping rollTypeOption = event.getOption(Constants.COMBAT_ROLL_TYPE);

        String userID = getUser().getId();
        Player player = activeGame.getPlayer(userID);
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);

        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        if (tileOption == null) {
            return;
        }

        String unitHolderName = Constants.SPACE;
        if (planetOption != null) {
            unitHolderName = planetOption.getAsString();
        }

        // Get tile info
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, activeGame);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                    "Tile " + tileOption.getAsString() + " not found");
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

        secondHalfOfCombatRoll(player, activeGame, event, tile, unitHolderName, rollType);
    }

    public void secondHalfOfCombatRoll(Player player, Game activeGame, GenericInteractionCreateEvent event, Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        String sb = "";
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        if (combatOnHolder == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Cannot find the planet " + unitHolderName + " on tile " + tile.getPosition());
            return;
        }

        if (rollType == CombatRollType.SpaceCannonDefence && !(combatOnHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Planet needs to be specified to fire space cannon defence on tile " + tile.getPosition());
        }

        Map<UnitModel, Integer> playerUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, player, event,
                rollType, activeGame);
        if (activeGame.getLaws().containsKey("articles_war")) {
            if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "naaz_mech_space".equals(unit.getAlias()))) {
                playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                        .filter(e -> !"naaz_mech_space".equals(e.getKey().getAlias()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Skipping " + Emojis.Naaz + " Z-Grav Eidolon due to Articles of War agenda.");
            }
        }
        if (playerUnitsByQuantity.isEmpty()) {
            String fightingOnUnitHolderName = unitHolderName;
            if (!unitHolderName.equalsIgnoreCase(Constants.SPACE)) {
                fightingOnUnitHolderName = Helper.getPlanetRepresentation(unitHolderName, activeGame);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "There are no units in " + fightingOnUnitHolderName + " on tile " + tile.getPosition()
                            + " for player " + player.getColor() + " "
                            + player.getFactionEmoji() + " for the combat roll type "+rollType.toString()+"\n"
                            + "Ping bothelper if this seems to be in error.");

            return;
        }

        List<UnitHolder> combatHoldersForOpponent = new ArrayList<>(List.of(combatOnHolder));
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            // Including space for finding opponents for pds - since people will fire before
            // landing sometimes
            // and fire after landing other times.
            combatHoldersForOpponent.add(tile.getUnitHolders().get(Constants.SPACE));
        }
        Player opponent = CombatHelper.GetOpponent(player, combatHoldersForOpponent, activeGame);
        if(opponent == null){
            opponent = player;
        }
        Map<UnitModel, Integer> opponentUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, opponent, event,
                rollType, activeGame);

        TileModel tileModel = TileHelper.getAllTiles().get(tile.getTileID());
        List<NamedCombatModifierModel> modifiers = CombatModHelper.GetModifiers(player, opponent,
                playerUnitsByQuantity, tileModel, activeGame, rollType, Constants.COMBAT_MODIFIERS);

        List<NamedCombatModifierModel> extraRolls = CombatModHelper.GetModifiers(player, opponent,
                playerUnitsByQuantity, tileModel, activeGame, rollType, Constants.COMBAT_EXTRA_ROLLS);

        // Check for temp mods
        CombatTempModHelper.EnsureValidTempMods(player, tileModel, combatOnHolder);
        CombatTempModHelper.InitializeNewTempMods(player, tileModel, combatOnHolder);
        List<NamedCombatModifierModel> tempMods = new ArrayList<>(CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(player, tileModel,
                combatOnHolder, false, rollType));
        List<NamedCombatModifierModel> tempOpponentMods = new ArrayList<>();
        if(opponent != null){
            tempOpponentMods = CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(opponent, tileModel,
                combatOnHolder, true, rollType);
        }
        tempMods.addAll(tempOpponentMods);

        String message = CombatMessageHelper.displayCombatSummary(player, tile, combatOnHolder, rollType);
        message += CombatHelper.RollForUnits(playerUnitsByQuantity, opponentUnitsByQuantity, extraRolls, modifiers, tempMods, player,
                opponent,
                activeGame, rollType);
        String hits = StringUtils.substringAfter(message, "Total hits ");
        hits = hits.split(" ")[0].replace("*","");
        int h = Integer.parseInt(hits);

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        if(!activeGame.isFoWMode() && rollType == CombatRollType.combatround && combatOnHolder instanceof Planet && h > 0 && opponent != null && opponent != player){
            String msg = opponent.getRepresentation(true, true) + " you can autoassign "+h+" hit(s)";
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Button.success(finChecker+"autoAssignGroundHits_"+combatOnHolder.getName()+"_"+h,"Auto-assign Hits"));
            buttons.add(Button.success("deleteButtons","Decline"));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
        }

    }
}
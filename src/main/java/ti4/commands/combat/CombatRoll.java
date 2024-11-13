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
import org.jetbrains.annotations.NotNull;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CombatHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatRollType;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
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

class CombatRoll extends GameStateSubcommand {

    public CombatRoll() {
        super(Constants.COMBAT_ROLL, "*V2* *BETA* Combat rolls for units on tile. *Auto includes modifiers*", true, false);
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
        OptionMapping planetOption = event.getOption(Constants.PLANET);
        String unitHolderName = Constants.SPACE;
        if (planetOption != null) {
            unitHolderName = planetOption.getAsString();
        }

        Game game = getGame();
        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        // Get tile info
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "Tile " + tileOption.getAsString() + " not found");
            return;
        }

        OptionMapping rollTypeOption = event.getOption(Constants.COMBAT_ROLL_TYPE);
        Player player = getPlayer();
        CombatRollType rollType = getCombatRollType(rollTypeOption);
        secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType);
    }

    @NotNull
    private static CombatRollType getCombatRollType(OptionMapping rollTypeOption) {
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
        return rollType;
    }

    public boolean checkIfUnitsOfType(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName, CombatRollType rollType) {
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        Map<UnitModel, Integer> playerUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, player, event,
            rollType, game);
        return !playerUnitsByQuantity.isEmpty();
    }

    public static int secondHalfOfCombatRoll(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName, CombatRollType rollType) {
        return secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType, false);
    }

    public static int secondHalfOfCombatRoll(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName, CombatRollType rollType, boolean automated) {
        String sb = "";
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        if (combatOnHolder == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Cannot find the planet " + unitHolderName + " on tile " + tile.getPosition());
            return 0;
        }

        if (rollType == CombatRollType.SpaceCannonDefence && !(combatOnHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Planet needs to be specified to fire space cannon defence on tile " + tile.getPosition());
        }

        Map<UnitModel, Integer> playerUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, player, event, rollType, game);
        if (ButtonHelper.isLawInPlay(game, "articles_war")) {
            if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "naaz_mech_space".equals(unit.getAlias()))) {
                playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                    .filter(e -> !"naaz_mech_space".equals(e.getKey().getAlias()))
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Skipping " + Emojis.Naaz + " Z-Grav Eidolon due to Articles of War agenda.");
            }
            if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
                if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "xxcha_mech".equals(unit.getAlias()))) {
                    playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                        .filter(e -> !"xxcha_mech".equals(e.getKey().getAlias()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Skipping " + Emojis.Xxcha + " mechs due to Articles of War agenda.");
                }
            }
            if (rollType == CombatRollType.bombardment) {
                if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "l1z1x_mech".equals(unit.getAlias()))) {
                    playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                        .filter(e -> !"l1z1x_mech".equals(e.getKey().getAlias()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        "Skipping " + Emojis.L1Z1X + " mechs due to Articles of War agenda.");
                }
            }

        }

        if (playerUnitsByQuantity.isEmpty()) {
            String fightingOnUnitHolderName = unitHolderName;
            if (!unitHolderName.equalsIgnoreCase(Constants.SPACE)) {
                fightingOnUnitHolderName = Helper.getPlanetRepresentation(unitHolderName, game);
            }
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "There are no units in " + fightingOnUnitHolderName + " on tile " + tile.getPosition()
                    + " for player " + player.getColor() + " "
                    + player.getFactionEmoji() + " for the combat roll type " + rollType.toString() + "\n"
                    + "Ping bothelper if this seems to be in error.");
            return 0;
        }

        List<UnitHolder> combatHoldersForOpponent = new ArrayList<>(List.of(combatOnHolder));
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            // Including space for finding opponents for pds - since people will fire before
            // landing sometimes
            // and fire after landing other times.
            if (rollType == CombatRollType.SpaceCannonOffence) {
                combatHoldersForOpponent = new ArrayList<>();
            }
            combatHoldersForOpponent.add(tile.getUnitHolders().get(Constants.SPACE));
        }
        Player opponent = CombatHelper.GetOpponent(player, combatHoldersForOpponent, game);
        if (opponent == null) {
            opponent = player;
        }
        Map<UnitModel, Integer> opponentUnitsByQuantity = CombatHelper.GetUnitsInCombat(tile, combatOnHolder, opponent, event, rollType, game);

        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        List<NamedCombatModifierModel> modifiers = CombatModHelper.GetModifiers(player, opponent,
            playerUnitsByQuantity, tileModel, game, rollType, Constants.COMBAT_MODIFIERS);

        List<NamedCombatModifierModel> extraRolls = CombatModHelper.GetModifiers(player, opponent,
            playerUnitsByQuantity, tileModel, game, rollType, Constants.COMBAT_EXTRA_ROLLS);

        // Check for temp mods
        CombatTempModHelper.EnsureValidTempMods(player, tileModel, combatOnHolder);
        CombatTempModHelper.InitializeNewTempMods(player, tileModel, combatOnHolder);
        List<NamedCombatModifierModel> tempMods = new ArrayList<>(CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(player, tileModel,
            combatOnHolder, false, rollType));
        List<NamedCombatModifierModel> tempOpponentMods = CombatTempModHelper.BuildCurrentRoundTempNamedModifiers(opponent, tileModel,
                combatOnHolder, true, rollType);
        tempMods.addAll(tempOpponentMods);

        String message = CombatMessageHelper.displayCombatSummary(player, tile, combatOnHolder, rollType);
        message += CombatHelper.RollForUnits(playerUnitsByQuantity, opponentUnitsByQuantity, extraRolls, modifiers, tempMods, player, opponent, game, rollType, event, tile);
        String hits = StringUtils.substringAfter(message, "Total hits ");
        hits = hits.split(" ")[0].replace("*", "");
        int h = Integer.parseInt(hits);
        int round;
        String combatName = "combatRoundTracker" + (opponent == null ? "nullfaction" : opponent.getFaction()) + tile.getPosition() + combatOnHolder.getName();
        if (game.getStoredValue(combatName).isEmpty()) {
            round = 0;
        } else {
            round = Integer.parseInt(game.getStoredValue(combatName));
        }
        int round2;
        String combatName2 = "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
        if (game.getStoredValue(combatName2).isEmpty()) {
            round2 = 1;
        } else {
            round2 = Integer.parseInt(game.getStoredValue(combatName2));
        }

        if (round2 > round && rollType == CombatRollType.combatround) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "## __Start of Combat Round #" + round2 + "__");
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);
        message = StringUtils.removeEnd(message, ";\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        if (message.contains("adding +1, at the risk of your")) {
            Button thalnosButton = Buttons.green("startThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Thalnos", Emojis.Relic);
            Button decline = Buttons.gray("editMessage_" + player.getFactionEmoji() + " declined Thalnos", "Decline");
            String thalnosMessage = "Use this button to roll for Thalnos.\n-# Note that if it matters, the dice were just rolled in the following format: (normal dice for unit 1)+(normal dice for unit 2)...etc...+(extra dice for unit 1)+(extra dice for unit 2)...etc.\n-# Sol and Letnev agents automatically are given as extra dice for unit 1.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), thalnosMessage, List.of(thalnosButton, decline));
        }
        if (!game.isFowMode() && rollType == CombatRollType.combatround && combatOnHolder instanceof Planet && opponent != null && opponent != player) {
            String msg2 = "\n" + opponent.getRepresentation(true, true, true, true) + " you suffered " + h + " hit" + (h == 1 ? "" : "s") + " in round #" + round2;
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            if (!automated) {
                if (h > 0) {
                    String msg = opponent.getRepresentationUnfogged() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
                    List<Button> buttons = new ArrayList<>();
                    if (opponent.isDummy()) {
                        if (round2 > round) {
                            buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                        }
                        buttons.add(Buttons.green(opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));
                    } else {
                        if (round2 > round) {
                            buttons.add(Buttons.blue("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Combat Round #" + (round + 1)));
                        }
                        buttons.add(Buttons.green(opponent.getFinsFactionCheckerPrefix() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat", "Manually Assign Hit" + (h == 1 ? "" : "s")));

                        buttons.add(Buttons.gray(opponent.getFinsFactionCheckerPrefix() + "cancelGroundHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
                    }
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
                    if (opponent.hasTech("vpw")) {
                        msg = player.getRepresentationUnfogged() + " you got hit by Valkyrie Particle Weave. You may autoassign 1 hit.";
                        buttons = new ArrayList<>();
                        buttons.add(Buttons.green(opponent.getFinsFactionCheckerPrefix() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_1", "Auto-assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat", "Manually Assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.gray(opponent.getFinsFactionCheckerPrefix() + "cancelGroundHits_" + tile.getPosition() + "_1", "Cancel a Hit"));
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
                    }
                } else {
                    String msg = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #" + (round + 1) + ".";
                    List<Button> buttons = new ArrayList<>();
                    if (opponent.isDummy()) {
                        if (round2 > round) {
                            buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
                        }

                    } else {
                        if (round2 > round) {
                            buttons.add(Buttons.blue("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Combat Round #" + (round + 1)));
                            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
                        }
                    }
                }
            } else {
                if (opponent.hasTech("vpw") && h > 0) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " suffered 1 hit due to valkyrie particle weave");
                }
            }
        } else {
            List<Button> buttons = new ArrayList<>();
            if (!game.isFowMode() && rollType == CombatRollType.combatround && opponent != null && opponent != player) {
                if (round2 > round) {
                    if (opponent.isDummy()) {
                        buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy For Combat Round #" + (round + 1)));
                    } else {
                        buttons.add(Buttons.blue("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Combat Round #" + (round + 1)));
                    }
                }
                String msg = "\n" + opponent.getRepresentation(true, true, true, true) + " you suffered " + h + " hit" + (h == 1 ? "" : "s") + " in round #" + round2;
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                if (h > 0) {

                    String finChecker = "FFCC_" + opponent.getFaction() + "_";
                    if (opponent.isDummy()) {
                        buttons.add(Buttons.green(opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));

                    } else {
                        buttons.add(Buttons.green(finChecker + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_spacecombat", "Manually Assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.gray(finChecker + "cancelSpaceHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
                    }

                    String msg2 = opponent.getFactionEmoji() + " may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit" + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n" + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
                } else {
                    String msg2 = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #" + (round + 1) + ".";
                    if (round2 > round) {
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
                    }
                }

            } else {
                if (game.isFowMode() && opponent != null && opponent.isDummy() && h > 0) {
                    if (combatOnHolder instanceof Planet) {
                        if (round2 > round) {
                            buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                        }
                        buttons.add(Buttons.green(opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));
                        String msg = opponent.getRepresentationUnfogged() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
                    } else {
                        buttons.add(Buttons.green(opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h, "Auto-assign Hits For Dummy"));
                        String msg2 = opponent.getFactionEmoji() + " may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit" + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n" + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
                    }
                }
            }
        }
        if (!game.isFowMode() && rollType == CombatRollType.AFB && opponent != null && opponent != player) {
            String msg2 = "\n" + opponent.getRepresentation(true, true, true, true) + " suffered " + h + " hit" + (h == 1 ? "" : "s") + " from AFB";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            if (h > 0) {
                String msg = opponent.getRepresentationUnfogged() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
                List<Button> buttons = new ArrayList<>();
                String finChecker = "FFCC_" + opponent.getFaction() + "_";
                buttons.add(Buttons.green(finChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
            }
        }
        if (!game.isFowMode() && rollType == CombatRollType.SpaceCannonOffence && h > 0 && opponent != null && opponent != player) {
            String msg = "\n" + opponent.getRepresentation(true, true, true, true) + " suffered " + h + " hit" + (h == 1 ? "" : "s") + " from space cannon offense";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            List<Button> buttons = new ArrayList<>();
            if (h > 0) {
                String finChecker = "FFCC_" + opponent.getFaction() + "_";
                buttons.add(Buttons.green(finChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_pds", "Manually Assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.gray(finChecker + "cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
                String msg2 = opponent.getFactionEmoji() + " may automatically assign " + (h == 1 ? "the hit" : "hits") + ". The hit" + (h == 1 ? "" : "s") + " would be assigned in the following way:\n\n" + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true, true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
            }
        }
        if (!game.isFowMode() && rollType == CombatRollType.bombardment && h > 0) {

            List<Button> buttons = new ArrayList<>();

            buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "_bombardment", "Assign Hit" + (h == 1 ? "" : "s")));

            String msg2 = " you may use this button to assign " + (h == 1 ? "the bombardment hit" : "bombardment hits") + ".";
            boolean someone = false;
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsInSystem(p2, tile)) {
                    msg2 = p2.getRepresentation() + msg2;
                    someone = true;
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), msg2, buttons);
                    }
                }
            }
            if (someone) {
                if (!game.isFowMode()) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
                }
            }

        }
        if (rollType == CombatRollType.bombardment && h > 0 && (player.hasAbility("meteor_slings") || player.getPromissoryNotes().containsKey("dspnkhra"))) {
            List<Button> buttons = new ArrayList<>();
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "meteorSlings_" + uH.getName(), "Infantry on " + Helper.getPlanetRepresentation(uH.getName(), game)));
            }
            buttons.add(Buttons.red("deleteButtons", "Done"));
            String msg2 = player.getRepresentation() + " you could potentially cancel " + (h == 1 ? "the bombardment hit" : "some bombardment hits") + " to place infantry instead. Use these buttons to do so, and press done when done. The bot did not track how many hits you got. ";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);

        }
        return h;

    }
}

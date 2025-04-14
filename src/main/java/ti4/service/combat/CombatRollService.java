package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.internal.utils.tuple.ImmutablePair;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.CombatModHelper;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.fow.FOWCombatThreadMirroring;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class CombatRollService {

    public boolean checkIfUnitsOfType(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName, CombatRollType rollType) {
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        Map<UnitModel, Integer> playerUnitsByQuantity = getUnitsInCombat(tile, combatOnHolder, player, event,
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
                "Cannot find the planet " + unitHolderName + " on tile " + tile.getPosition() + ".");
            return 0;
        }

        if (rollType == CombatRollType.SpaceCannonDefence && !(combatOnHolder instanceof Planet)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "Planet needs to be specified to fire SPACE CANNON against ships on tile " + tile.getPosition() + ".");
        }

        Map<UnitModel, Integer> playerUnitsByQuantity = getUnitsInCombat(tile, combatOnHolder, player, event, rollType, game);
        if (ButtonHelper.isLawInPlay(game, "articles_war")) {
            if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "naaz_mech_space".equals(unit.getAlias()))) {
                playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                    .filter(e -> !"naaz_mech_space".equals(e.getKey().getAlias()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Skipping Z-Grav Eidolon (Naaz-Rokha mech) combat rolls due to _Articles of War_.");
            }
            if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
                if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "xxcha_mech".equals(unit.getAlias()))) {
                    playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                        .filter(e -> !"xxcha_mech".equals(e.getKey().getAlias()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Skipping Indomitus (Xxcha mech) SPACE CANNON rolls due to _Articles of War_.");
                }
            }
            if (rollType == CombatRollType.bombardment) {
                if (playerUnitsByQuantity.keySet().stream().anyMatch(unit -> "l1z1x_mech".equals(unit.getAlias()))) {
                    playerUnitsByQuantity = new HashMap<>(playerUnitsByQuantity.entrySet().stream()
                        .filter(e -> !"l1z1x_mech".equals(e.getKey().getAlias()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Skipping Annihilator (L1Z1X mech) BOMBARDMENT rolls due to _Articles of War_.");
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
        Player opponent = getOpponent(player, combatHoldersForOpponent, game);
        if (opponent == null) {
            opponent = player;
        }
        Map<UnitModel, Integer> opponentUnitsByQuantity = getUnitsInCombat(tile, combatOnHolder, opponent, event, rollType, game);

        TileModel tileModel = TileHelper.getTileById(tile.getTileID());
        List<NamedCombatModifierModel> modifiers = CombatModHelper.getModifiers(player, opponent,
            playerUnitsByQuantity, tileModel, game, rollType, Constants.COMBAT_MODIFIERS);

        List<NamedCombatModifierModel> extraRolls = CombatModHelper.getModifiers(player, opponent,
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
        message += rollForUnits(playerUnitsByQuantity, extraRolls, modifiers, tempMods, player, opponent, game, rollType, event, tile);
        String hits = StringUtils.substringAfter(message, "Total hits ");
        hits = hits.split(" ")[0].replace("*", "");
        int h = Integer.parseInt(hits);
        int round;
        String combatName = "combatRoundTracker" + opponent.getFaction() + tile.getPosition() + combatOnHolder.getName();
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
        if (game.isFowMode() && rollType == CombatRollType.SpaceCannonOffence && isFoWPrivateChannelRoll(player, event)) {
            //If roll was from pds button in private channel, send the result to the target
            MessageHelper.sendMessageToChannel(opponent.getCorrectChannel(), opponent.getRepresentationUnfogged() + " " + FOWCombatThreadMirroring.parseCombatRollMessage(message).replace("Someone", player.getRepresentationNoPing()));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Roll result was sent to " + opponent.getRepresentationNoPing());
        }
        if (message.contains("adding +1, at the risk of your")) {
            Button thalnosButton = Buttons.green("startThalnos_" + tile.getPosition() + "_" + unitHolderName, "Roll Thalnos", ExploreEmojis.Relic);
            Button decline = Buttons.gray("editMessage_" + player.getFactionEmoji() + " declined Thalnos", "Decline");
            String thalnosMessage = "Use this button to roll for Thalnos.\n-# Note that if it matters, the dice were just rolled in the following format: (normal dice for unit 1)+(normal dice for unit 2)...etc...+(extra dice for unit 1)+(extra dice for unit 2)...etc.\n-# Sol and Letnev agents automatically are given as extra dice for unit 1.";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), thalnosMessage, List.of(thalnosButton, decline));
        }
        if (!game.isFowMode() && rollType == CombatRollType.combatround && combatOnHolder instanceof Planet && opponent != player) {
            String msg2 = "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered " + h + " hit" + (h == 1 ? "" : "s") + " in round #" + round2 + ".";
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
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                    if (opponent.hasTech("vpw")) {
                        msg = player.getRepresentationUnfogged() + " you got hit by _Valkyrie Particle Weave_. You may autoassign 1 hit.";
                        buttons = new ArrayList<>();
                        buttons.add(Buttons.green(opponent.getFinsFactionCheckerPrefix() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_1", "Auto-assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_groundcombat", "Manually Assign Hit" + (h == 1 ? "" : "s")));
                        buttons.add(Buttons.gray(opponent.getFinsFactionCheckerPrefix() + "cancelGroundHits_" + tile.getPosition() + "_1", "Cancel a Hit"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                    }
                } else {
                    String msg = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #" + (round + 1) + ".";
                    List<Button> buttons = new ArrayList<>();
                    if (opponent.isDummy()) {
                        if (round2 > round) {
                            buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                        }

                    } else {
                        if (round2 > round) {
                            buttons.add(Buttons.blue("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Combat Round #" + (round + 1)));
                            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                        }
                    }
                }
            } else {
                if (opponent.hasTech("vpw") && h > 0) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation() + " suffered 1 hit due to _Valkyrie Particle Weave_.");
                }
            }
        } else {
            List<Button> buttons = new ArrayList<>();
            if (!game.isFowMode() && rollType == CombatRollType.combatround && opponent != player) {
                if (round2 > round) {
                    if (opponent.isDummy()) {
                        buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy For Combat Round #" + (round + 1)));
                    } else {
                        buttons.add(Buttons.blue("combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Combat Round #" + (round + 1)));
                    }
                }
                String msg = "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered " + h + " hit" + (h == 1 ? "" : "s") + " in round #" + round2 + ".";
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

                    String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign " + (h == 1 ? "the hit" : "hits") + ". "
                        + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                } else {
                    String msg2 = opponent.getRepresentationUnfogged() + " you may roll dice for Combat Round #" + (round + 1) + ".";
                    if (round2 > round) {
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                    }
                }

            } else {
                if (game.isFowMode() && opponent.isDummy() && h > 0) {
                    if (combatOnHolder instanceof Planet) {
                        if (round2 > round) {
                            buttons.add(Buttons.blue(opponent.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_" + combatOnHolder.getName(), "Roll Dice For Dummy for Combat Round #" + (round + 1)));
                        }
                        buttons.add(Buttons.green(opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + combatOnHolder.getName() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s") + " For Dummy"));
                        String msg = opponent.getRepresentationUnfogged() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
                    } else {
                        buttons.add(Buttons.green(opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + tile.getPosition() + "_" + h, "Auto-assign Hits For Dummy"));
                        String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign " + (h == 1 ? "the hit" : "hits") + "."
                            + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true);
                        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                    }
                }
            }
        }
        if (!game.isFowMode() && rollType == CombatRollType.AFB && opponent != player) {
            String msg2 = "\n" + opponent.getRepresentation(true, true, true, true) + " suffered " + h + " hit" + (h == 1 ? "" : "s") + " from ANTI-FIGHTER BARRAGE.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
            if (h > 0) {
                String msg = opponent.getRepresentationUnfogged() + " you may autoassign " + h + " hit" + (h == 1 ? "" : "s") + ".";
                List<Button> buttons = new ArrayList<>();
                String finChecker = "FFCC_" + opponent.getFaction() + "_";
                buttons.add(Buttons.green(finChecker + "autoAssignAFBHits_" + tile.getPosition() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s")));
                buttons.add(Buttons.gray("cancelAFBHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
                buttons.add(Buttons.red("deleteButtons", "Decline"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            }
        }
        if ((!game.isFowMode() || isFoWPrivateChannelRoll(player, event)) && rollType == CombatRollType.SpaceCannonOffence && h > 0 && opponent != player) {
            MessageChannel channel = isFoWPrivateChannelRoll(player, event) ? opponent.getCorrectChannel() : event.getMessageChannel();
            String msg = "\n" + opponent.getRepresentation(true, true, true, true) + " suffered " + h + " hit" + (h == 1 ? "" : "s") + " from SPACE CANNON against your ships.";
            MessageHelper.sendMessageToChannel(channel, msg);
            List<Button> buttons = new ArrayList<>();
            String finChecker = "FFCC_" + opponent.getFaction() + "_";
            buttons.add(Buttons.green(finChecker + "autoAssignSpaceCannonOffenceHits_" + tile.getPosition() + "_" + h, "Auto-assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "deleteThis_pds", "Manually Assign Hit" + (h == 1 ? "" : "s")));
            buttons.add(Buttons.gray(finChecker + "cancelPdsOffenseHits_" + tile.getPosition() + "_" + h, "Cancel a Hit"));
            String msg2 = opponent.getRepresentationNoPing() + ", you may automatically assign " + (h == 1 ? "the hit" : "hits") + "."
                + ButtonHelperModifyUnits.autoAssignSpaceCombatHits(opponent, game, tile, h, event, true, true);
            MessageHelper.sendMessageToChannelWithButtons(channel, msg2, buttons);
        }
        if (!game.isFowMode() && rollType == CombatRollType.bombardment && h > 0) {
            List<Button> buttons = new ArrayList<>();

            buttons.add(Buttons.red("getDamageButtons_" + tile.getPosition() + "_bombardment", "Assign Hit" + (h == 1 ? "" : "s")));
            String msg2 = " you may use this button to assign " + (h == 1 ? "the BOMBARDMENT hit" : "BOMBARDMENT hits") + ".";
            boolean someone = false;
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (FoWHelper.playerHasUnitsInSystem(p2, tile)) {
                    msg2 = p2.getRepresentation() + msg2;
                    someone = true;
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), msg2, buttons);
                    }
                }
            }
            if (someone) {
                if (!game.isFowMode()) {
                    MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
                }
            }

        }
        if (rollType == CombatRollType.bombardment && h > 0 && (player.hasAbility("meteor_slings") || player.getPromissoryNotes().containsKey("dspnkhra"))) {
            List<Button> buttons = new ArrayList<>();
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                buttons.add(Buttons.green(player.getFinsFactionCheckerPrefix() + "meteorSlings_" + uH.getName(), "Infantry on " + Helper.getPlanetRepresentation(uH.getName(), game)));
            }
            buttons.add(Buttons.red("deleteButtons", "Done"));
            String msg2 = player.getRepresentation() + " you could potentially cancel " + (h == 1 ? "the BOMBARDMENT hit" : "some BOMBARDMENT hits") + " to place infantry instead. Use these buttons to do so, and press done when done. The bot did not track how many hits you got. ";
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);

        }
        return h;
    }

    //This roll was made from fow private channel and not from a combat thread
    private static boolean isFoWPrivateChannelRoll(Player player, GenericInteractionCreateEvent event) {
        return event.getMessageChannel().equals(player.getPrivateChannel());
    }

    public static String rollForUnits(
        Map<UnitModel, Integer> playerUnits,
        List<NamedCombatModifierModel> extraRolls,
        List<NamedCombatModifierModel> autoMods,
        List<NamedCombatModifierModel> tempMods,
        Player player, Player opponent,
        Game game, CombatRollType rollType,
        GenericInteractionCreateEvent event, Tile activeSystem
    ) {
        String result = "";

        List<NamedCombatModifierModel> mods = new ArrayList<>(autoMods);
        mods.addAll(tempMods);
        Set<NamedCombatModifierModel> set2 = new HashSet<>(mods);
        mods = new ArrayList<>(set2);

        List<NamedCombatModifierModel> modAndExtraRolls = new ArrayList<>(mods);
        modAndExtraRolls.addAll(extraRolls);
        Set<NamedCombatModifierModel> set = new HashSet<>(modAndExtraRolls);
        List<NamedCombatModifierModel> uniqueList = new ArrayList<>(set);
        result += CombatMessageHelper.displayModifiers("With modifiers: \n", playerUnits, uniqueList);

        // Actually roll for each unit
        int totalHits = 0;
        StringBuilder resultBuilder = new StringBuilder(result);
        List<UnitModel> playerUnitsList = new ArrayList<>(playerUnits.keySet());
        int totalMisses = 0;
        UnitHolder space = activeSystem.getUnitHolders().get("space");
        StringBuilder extra = new StringBuilder();
        for (Map.Entry<UnitModel, Integer> entry : playerUnits.entrySet()) {
            UnitModel unitModel = entry.getKey();
            int numOfUnit = entry.getValue();

            int toHit = unitModel.getCombatDieHitsOnForAbility(rollType, player, game);
            int modifierToHit = CombatModHelper.getCombinedModifierForUnit(unitModel, numOfUnit, mods, player, opponent, game,
                playerUnitsList, rollType, activeSystem);
            int extraRollsForUnit = CombatModHelper.getCombinedModifierForUnit(unitModel, numOfUnit, extraRolls, player,
                opponent, game, playerUnitsList, rollType, activeSystem);
            int numRollsPerUnit = unitModel.getCombatDieCountForAbility(rollType, player, game);
            boolean extraRollsCount = false;
            if ((numRollsPerUnit > 1 || extraRollsForUnit > 0) && game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                extraRollsCount = true;
                numRollsPerUnit = 1;
                extraRollsForUnit = 0;
            }
            if (rollType == CombatRollType.SpaceCannonOffence && numRollsPerUnit == 3 && unitModel.getBaseType().equalsIgnoreCase("spacedock")) {
                numOfUnit = 1;
                game.setStoredValue("EBSFaction", "");
            }
            if (rollType == CombatRollType.bombardment && numRollsPerUnit > 1 && unitModel.getBaseType().equalsIgnoreCase("destroyer")) {
                numOfUnit = 1;
                game.setStoredValue("TnelisAgentFaction", "");
            }
            int numRolls = (numOfUnit * numRollsPerUnit) + extraRollsForUnit;
            List<DiceHelper.Die> resultRolls = DiceHelper.rollDice(toHit - modifierToHit, numRolls);
            player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numRolls * (11 - toHit + modifierToHit)));

            int hitRolls = DiceHelper.countSuccesses(resultRolls);
            if (unitModel.getId().equalsIgnoreCase("jolnar_flagship")) {
                for (DiceHelper.Die die : resultRolls) {
                    if (die.getResult() > 8) {
                        hitRolls += 2;
                    }
                }
            }
            if (unitModel.getId().equalsIgnoreCase("sigma_jolnar_flagship_1") || unitModel.getId().equalsIgnoreCase("sigma_jolnar_flagship_2")) {
                int additionalDice = hitRolls;
                while (hitRolls < 100 && additionalDice > 0) {
                    List<DiceHelper.Die> additionalResultRolls = DiceHelper.rollDice(toHit - modifierToHit, additionalDice);
                    additionalDice = DiceHelper.countSuccesses(additionalResultRolls);
                    hitRolls += additionalDice;
                    resultRolls.addAll(additionalResultRolls);
                }
            }
            if (rollType == CombatRollType.combatround && (player.hasAbility("valor") || opponent.hasAbility("valor")) && ButtonHelperAgents.getGloryTokenTiles(game).contains(activeSystem)) {
                for (DiceHelper.Die die : resultRolls) {
                    if (die.getResult() > 9) {
                        hitRolls += 1;
                        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                            + " got an extra hit due to the **Valor** ability (it has been accounted for in the hit count).");
                    }
                }
            }
            if (unitModel.getId().equalsIgnoreCase("vaden_flagship") && CombatRollType.bombardment == rollType) {
                for (DiceHelper.Die die : resultRolls) {
                    if (die.getResult() > 4) {
                        player.setTg(player.getTg() + 1);
                        ButtonHelperAbilities.pillageCheck(player, game);
                        ButtonHelperAgents.resolveArtunoCheck(player, 1);
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                            + " gained 1 trade good due to hitting on a BOMBARDMENT roll with the Aurum Vadra (the Vaden flagship).");
                        break;

                    }
                }
            }
            int misses = numRolls - hitRolls;
            totalMisses += misses;

            if (misses > 0 && !extraRollsCount && game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                extra.append(player.getFactionEmoji()).append(" destroyed ").append(misses).append(" of their own ").append(unitModel.getName()).append(misses == 1 ? "" : "s").append(" due to ").append(misses == 1 ? "a Thalnos miss" : "Thalnos misses");
                for (String thalnosUnit : game.getThalnosUnits().keySet()) {
                    String pos = thalnosUnit.split("_")[0];
                    String unitHolderName = thalnosUnit.split("_")[1];
                    Tile tile = game.getTileByPosition(pos);
                    String unitName = unitModel.getBaseType();
                    thalnosUnit = thalnosUnit.split("_")[2].replace("damaged", "");
                    if (thalnosUnit.equals(unitName)) {
                        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), misses + " " + unitName + " " + unitHolderName);
                        if (unitName.equalsIgnoreCase("infantry")) {
                            ButtonHelper.resolveInfantryDeath(player, misses);
                        }
                        if (unitName.equalsIgnoreCase("mech")) {
                            if (player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < misses; x++) {
                                    ButtonHelper.rollMykoMechRevival(game, player);
                                }
                            }
                            if (player.hasTech("sar")) {
                                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                                    + " you gained " + misses + " trade good (" + player.getTg() + "->" + (player.getTg() + misses)
                                    + ") from _Self-Assembly Routines_ because of " + misses + " of your mechs dying."
                                    + " This is not an optional gain" + (misses > 1 ? ", and happens 1 trade good at a time" : "") + ".");
                                for (int x = 0; x < misses; x++) {
                                    player.setTg(player.getTg() + 1);
                                    ButtonHelperAbilities.pillageCheck(player, game);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, 1);
                            }
                        }
                        break;
                    }
                }

            } else {
                if (misses > 0 && game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                        player.getFactionEmoji() + " had " + misses + " " + unitModel.getName() + (misses == 1 ? "" : "s") + " miss" + (misses == 1 ? "" : "es")
                            + " on a Thalnos roll, but no units were removed due to extra rolls being unaccounted for.");
                }
            }

            totalHits += hitRolls;

            String unitRoll = CombatMessageHelper.displayUnitRoll(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, extraRollsForUnit, resultRolls, hitRolls);
            resultBuilder.append(unitRoll);
            List<DiceHelper.Die> resultRolls2 = new ArrayList<>();
            int numMisses = numRolls - hitRolls;
            if (game.playerHasLeaderUnlockedOrAlliance(player, "jolnarcommander") && rollType != CombatRollType.combatround && numMisses > 0) {
                resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numMisses);
                player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numMisses * (11 - toHit + modifierToHit)));
                int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                totalHits += hitRolls2;
                String unitRoll2 = CombatMessageHelper.displayUnitRoll(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                resultBuilder.append("Rerolling ").append(numMisses).append(" miss").append(numMisses == 1 ? "" : "es").append(" due to Ta Zern, the Jol-Nar Commander:\n ").append(unitRoll2);
            }
            if (rollType == CombatRollType.SpaceCannonOffence || rollType == CombatRollType.SpaceCannonDefence) {
                if (player.ownsUnit("gledge_pds2") && totalHits > 0) {
                    String msg = player.getRepresentation() + ", use the buttons to explore a planet with the PDS that got the hit. It should be " +
                        "noted that the bot has no idea which PDS rolled which dice, but default practice would be to go from lowest tile position to highest" +
                        ", with _Plasma Scoring_ applying to the last die. You can specify any order before rolling though.";
                    for (int x = 0; x < totalHits; x++) {
                        List<Button> buttons = new ArrayList<>();
                        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Pds)) {
                            for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(player, tile, "pds")) {
                                Planet planetUnit = game.getUnitHolderFromPlanet(planet);
                                if ("space".equalsIgnoreCase(planetUnit.getName())) {
                                    continue;
                                }
                                planet = planetUnit.getName();
                                if (isNotBlank(planetUnit.getOriginalPlanetType()) && player.getPlanetsAllianceMode().contains(planet)
                                    && FoWHelper.playerHasUnitsOnPlanet(player, tile, planet)) {
                                    List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetUnit, player);
                                    buttons.addAll(planetButtons);
                                }
                            }
                        }
                        buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                    }
                }
                if (player.ownsUnit("gledge_pds")) {
                    String msg = player.getRepresentation() + " use the buttons to explore a planet with the PDS that got the hit.";
                    for (DiceHelper.Die die : resultRolls) {
                        if (die.getResult() < 8) {
                            continue;
                        }
                        List<Button> buttons = new ArrayList<>();
                        for (String planet : ButtonHelper.getPlanetsWithSpecificUnit(player, activeSystem, "pds")) {
                            Planet planetUnit = game.getUnitHolderFromPlanet(planet);
                            if ("space".equalsIgnoreCase(planetUnit.getName())) {
                                continue;
                            }
                            planet = planetUnit.getName();
                            if (isNotBlank(planetUnit.getOriginalPlanetType()) && player.getPlanetsAllianceMode().contains(planet)
                                && FoWHelper.playerHasUnitsOnPlanet(player, activeSystem, planet)) {
                                List<Button> planetButtons = ButtonHelper.getPlanetExplorationButtons(game, planetUnit, player);
                                buttons.addAll(planetButtons);
                            }
                        }
                        buttons.add(Buttons.red("deleteButtons", "No Valid Exploration"));
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                    }

                }
            }

            if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround && numMisses > 0) {
                resultRolls2 = DiceHelper.rollDice(toHit - modifierToHit, numMisses);
                player.setExpectedHitsTimes10(player.getExpectedHitsTimes10() + (numMisses * (11 - toHit + modifierToHit)));
                int hitRolls2 = DiceHelper.countSuccesses(resultRolls2);
                totalHits += hitRolls2;
                String unitRoll2 = CombatMessageHelper.displayUnitRoll(unitModel, toHit, modifierToHit, numOfUnit, numRollsPerUnit, 0, resultRolls2, hitRolls2);
                resultBuilder.append("**Munitions Reserve** rerolling ").append(numMisses).append(" miss").append(numMisses == 1 ? "" : "es").append(": ").append(unitRoll2);
            }

            int argentInfKills = 0;
            if (player != opponent && unitModel.getId().equalsIgnoreCase("argent_destroyer2") && rollType == CombatRollType.AFB && space.getUnitCount(Units.UnitType.Infantry, opponent.getColor()) > 0) {
                for (DiceHelper.Die die : resultRolls) {
                    if (die.getResult() > 8) {
                        argentInfKills++;
                    }
                }
                for (DiceHelper.Die die : resultRolls2) {
                    if (die.getResult() > 8) {
                        argentInfKills++;
                    }
                }
                argentInfKills = Math.min(argentInfKills, space.getUnitCount(Units.UnitType.Infantry, opponent.getColor()));
            }
            if (argentInfKills > 0) {
                String kills = "\nDue to the Strike Wing Alpha II destroyer ability, " + argentInfKills + " of " + opponent.getRepresentation(false, true) + " infantry were destroyed\n";
                resultBuilder.append(kills);
                space.removeUnit(Mapper.getUnitKey(AliasHandler.resolveUnit("infantry"), opponent.getColorID()), argentInfKills);
                ButtonHelper.resolveInfantryDeath(opponent, argentInfKills);
            }
        }
        result = resultBuilder.toString();

        result += CombatMessageHelper.displayHitResults(totalHits);
        player.setActualHits(player.getActualHits() + totalHits);
        if (player.hasRelic("thalnos") && rollType == CombatRollType.combatround && totalMisses > 0 && !game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
            result += "\n" + player.getFactionEmoji() + " You have _The Crown of Thalnos_ and may reroll " + (totalMisses == 1 ? "the miss" : "misses")
                + ", adding +1, at the risk of your " + (totalMisses == 1 ? "troop's life" : "troops' lives") + ".";
        }
        if (totalHits > 0 && CombatRollType.bombardment == rollType && player.hasTech("dszelir")) {
            result += "\n" + player.getFactionEmoji() + " You have _Shard Volley_ and thus should produce an additional hit to the ones rolled above.";
        }
        if (!extra.isEmpty()) {
            result += "\n\n" + extra;
        }
        if (game.getStoredValue("munitionsReserves").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround) {
            game.setStoredValue("munitionsReserves", "");
        }
        return result;
    }

    public static Player getOpponent(Player player, List<UnitHolder> unitHolders, Game game) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        List<Player> opponents = unitHolders.stream().flatMap(holder -> holder.getUnitColorsOnHolder().stream())
            .filter(color -> !color.equals(playerColorID))
            .map(game::getPlayerByColorID)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if (!opponents.isEmpty()) {
            opponent = opponents.getFirst();
        }
        if (opponents.size() > 1) {
            Optional<Player> activeOpponent = opponents.stream()
                .filter(opp -> opp.getUserID().equals(game.getActivePlayerID()))
                .findAny();
            if (activeOpponent.isPresent()) {
                opponent = activeOpponent.get();
            }
        }
        return opponent;
    }

    public static Map<UnitModel, Integer> getUnitsInCombat(
        Tile tile, UnitHolder unitHolder, Player player,
        GenericInteractionCreateEvent event, CombatRollType roleType, Game game
    ) {
        Planet unitHolderPlanet = null;
        if (unitHolder instanceof Planet) {
            unitHolderPlanet = (Planet) unitHolder;
        }
        return switch (roleType) {
            case combatround -> getUnitsInCombatRound(unitHolder, player, event, tile);
            case AFB -> getUnitsInAFB(tile, player, event);
            case bombardment -> getUnitsInBombardment(tile, player, event);
            case SpaceCannonOffence -> getUnitsInSpaceCannonOffense(tile, player, event, game);
            case SpaceCannonDefence -> getUnitsInSpaceCannonDefence(unitHolderPlanet, player, event);
        };
    }

    public static Map<UnitModel, Integer> getUnitsInCombatRound(UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event, Tile tile) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> unitsInCombat = unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        HashMap<UnitModel, Integer> output;
        if (unitHolder.getName().equals(Constants.SPACE)) {
            if (unitsByAsyncId.containsKey("fs") && (player.hasUnit("nekro_flagship") || player.hasUnit("sigma_nekro_flagship_2"))) {
                output = new HashMap<>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey() != null
                        && (entry.getKey().getIsGroundForce() || entry.getKey().getIsShip()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                for (UnitHolder u2 : tile.getUnitHolders().values()) {
                    if (u2 == unitHolder) {
                        continue;
                    }
                    Map<String, Integer> unitsByAsyncId2 = u2.getUnitAsyncIdsOnHolder(colorID);
                    Map<UnitModel, Integer> unitsInCombat2 = unitsByAsyncId2.entrySet().stream().map(
                        entry -> new ImmutablePair<>(
                            player.getPriorityUnitByAsyncID(entry.getKey(), unitHolder),
                            entry.getValue()))
                        .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
                    HashMap<UnitModel, Integer> output2;
                    output2 = new HashMap<>(unitsInCombat2.entrySet().stream()
                        .filter(entry -> entry.getKey() != null
                            && (entry.getKey().getIsGroundForce() || entry.getKey().getIsShip()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
                    for (UnitModel unit : output2.keySet()) {
                        if (output.containsKey(unit)) {
                            output.put(unit, output.get(unit) + output2.get(unit));
                        } else {
                            output.put(unit, output2.get(unit));
                        }
                    }
                }
            } else {
                output = new HashMap<>(unitsInCombat.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getKey().getIsShip())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
            }
        } else {
            output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null
                    && (entry.getKey().getIsGroundForce() || entry.getKey().getIsShip()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> getUnitsInAFB(Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }

        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getAfbDieCount(player, player.getGame()) > 0)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    private static Map<UnitModel, Integer> getUnitsInCombat(Player player, Map<String, Integer> unitsByAsyncId) {
        return unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), null),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private static void getUnitsOnHolderByAsyncId(String colorID, Map<String, Integer> unitsByAsyncId, UnitHolder unitHolder) {
        Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }
    }

    public static Map<UnitModel, Integer> getUnitsInBombardment(Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }
        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getBombardDieCount(player, player.getGame()) > 0)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> getUnitsInSpaceCannonDefence(Planet planet, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        if (planet == null) {
            return new HashMap<>();
        }

        Map<String, Integer> unitsOnHolderByAsyncId = planet.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }

        Map<UnitModel, Integer> unitsOnPlanet = unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), null),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Check for space cannon die on planet
        PlanetModel planetModel = Mapper.getPlanet(planet.getName());
        String ccID = Mapper.getControlID(player.getColor());
        if (player.controlsMecatol(true) && Constants.MECATOLS.contains(planet.getName()) && player.hasIIHQ()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
            UnitModel planetFakeUnit = new UnitModel();
            planetFakeUnit.setSpaceCannonHitsOn(planet.getSpaceCannonHitsOn());
            planetFakeUnit.setSpaceCannonDieCount(planet.getSpaceCannonDieCount());
            planetFakeUnit.setName(Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
            planetFakeUnit.setAsyncId(planet.getName() + "pds");
            planetFakeUnit.setId(planet.getName() + "pds");
            planetFakeUnit.setBaseType("pds");
            planetFakeUnit.setFaction(player.getFaction());
            unitsOnPlanet.put(planetFakeUnit, 1);
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnPlanet.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount() > 0)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    public static Map<UnitModel, Integer> getUnitsInSpaceCannonOffense(Tile tile, Player player, GenericInteractionCreateEvent event, Game game) {
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();

        Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
        for (UnitHolder unitHolder : unitHolders) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }

        Map<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = game.getTileByPosition(adjacentTilePosition);
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                getUnitsOnHolderByAsyncId(colorID, adjacentUnitsByAsyncId, unitHolder);
            }
        }

        Map<UnitModel, Integer> unitsOnTile = unitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), null),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
        Map<UnitModel, Integer> unitsOnAdjacentTiles = adjacentUnitsByAsyncId.entrySet().stream().map(
            entry -> new ImmutablePair<>(
                player.getPriorityUnitByAsyncID(entry.getKey(), null),
                entry.getValue()))
            .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Check for space cannon die on planets

        for (UnitHolder unitHolder : unitHolders) {
            if (unitHolder instanceof Planet planet) {
                if (player.controlsMecatol(true) && Constants.MECATOLS.contains(planet.getName()) && player.hasIIHQ()) {
                    PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                    planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                    planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
                }
                PlanetModel planetModel = Mapper.getPlanet(planet.getName());
                String ccID = Mapper.getControlID(player.getColor());
                if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
                    UnitModel planetFakeUnit = new UnitModel();
                    planetFakeUnit.setSpaceCannonHitsOn(planet.getSpaceCannonHitsOn());
                    planetFakeUnit.setSpaceCannonDieCount(planet.getSpaceCannonDieCount());
                    planetFakeUnit
                        .setName(Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon");
                    planetFakeUnit.setAsyncId(planet.getName() + "pds");
                    planetFakeUnit.setId(planet.getName() + "pds");
                    planetFakeUnit.setBaseType("pds");
                    planetFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(planetFakeUnit, 1);
                }
            }
        }
        if (player.hasAbility("starfall_gunnery")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    UnitModel starfallFakeUnit = new UnitModel();
                    starfallFakeUnit.setSpaceCannonHitsOn(8);
                    starfallFakeUnit.setSpaceCannonDieCount(1);
                    starfallFakeUnit
                        .setName("Starfall Gunnery space cannon");
                    starfallFakeUnit.setAsyncId("starfallpds");
                    starfallFakeUnit.setId("starfallpds");
                    starfallFakeUnit.setBaseType("pds");
                    starfallFakeUnit.setFaction(player.getFaction());
                    unitsOnTile.put(starfallFakeUnit, count);
                }
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji()
                    + ", a reminder that due to the **Starfall Gunnery** ability, the SPACE CANNON of only 1 unit should be counted at this point."
                    + " Hopefully you declared beforehand what that unit was, but by default it's probably the best one. Only look at/count the rolls of that one unit.");
            }
        }

        HashMap<UnitModel, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount(player, game) > 0)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        HashMap<UnitModel, Integer> adjacentOutput = new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
            .filter(entry -> entry.getKey() != null
                && entry.getKey().getSpaceCannonDieCount(player, game) > 0
                && (entry.getKey().getDeepSpaceCannon() || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander") || (entry.getKey().getBaseType().equalsIgnoreCase("spacedock"))))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        int limit = 0;
        for (var entry : adjacentOutput.entrySet()) {
            if (entry.getKey().getDeepSpaceCannon()) {
                if (output.containsKey(entry.getKey())) {
                    output.put(entry.getKey(), entry.getValue() + output.get(entry.getKey()));
                } else {
                    output.put(entry.getKey(), entry.getValue());
                }
            } else {
                if (limit < 1) {
                    limit = 1;
                    if (output.containsKey(entry.getKey())) {
                        output.put(entry.getKey(), 1 + output.get(entry.getKey()));
                    } else {
                        output.put(entry.getKey(), 1);
                    }
                }
            }
        }

        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    private static void checkBadUnits(Player player, GenericInteractionCreateEvent event, Map<String, Integer> unitsByAsyncId, HashMap<UnitModel, Integer> output) {
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
            .filter(unit -> !duplicates.add(unit.getAsyncId()))
            .map(UnitModel::getBaseType)
            .toList();
        List<String> missing = unitsByAsyncId.keySet().stream()
            .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
            .collect(Collectors.toList());

        if (!dupes.isEmpty()) {
            CombatMessageHelper.displayDuplicateUnits(event, missing);
        }
        if (!missing.isEmpty()) {
            CombatMessageHelper.displayMissingUnits(event, missing);
        }
    }
}

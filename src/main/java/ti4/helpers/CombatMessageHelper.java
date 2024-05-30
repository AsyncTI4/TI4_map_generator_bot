package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;

import ti4.generator.Mapper;
import ti4.helpers.DiceHelper.Die;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;

public class CombatMessageHelper {
    public static void displayDuplicateUnits(GenericInteractionCreateEvent event, List<String> dupes) {
        if (dupes.isEmpty()) return;
        // Gracefully fail when units don't exist
        StringBuilder error = new StringBuilder();
        error.append("You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n");
        error.append("> Duplicate units: ").append(dupes);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), error.toString());
    }

    public static void displayMissingUnits(GenericInteractionCreateEvent event, List<String> missing) {
        if (missing.isEmpty()) return;
        // Gracefully fail when units don't exist
        StringBuilder error = new StringBuilder();
        error.append("You do not seem to own any of the following unit types, so they will be skipped.");
        error.append(" Ping bothelper if this seems to be in error.\n");
        error.append("> Unowned units: ").append(missing).append("\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), error.toString());
    }

    public static String displayUnitRoll(UnitModel unit, int toHit, int modifier, int unitQuantity, int numRollsPerUnit, int extraRolls, List<Die> resultRolls, int numHit) {
        String hitsSuffix = "";
        if (numHit > 1) {
            hitsSuffix = "s";
        }

        // Rolls str fragment
        String unitRollsTextInfo = "";
        int totalRolls = (numRollsPerUnit * unitQuantity) + extraRolls;
        if (totalRolls > 1) {
            unitRollsTextInfo = String.format("%s rolls,", numRollsPerUnit);
            if (extraRolls > 0 && numRollsPerUnit > 1) {
                unitRollsTextInfo = String.format("%s rolls (+%s rolls),",
                    numRollsPerUnit,
                    extraRolls);
            } else if (extraRolls > 0) {
                unitRollsTextInfo = String.format("(+%s rolls),",
                    extraRolls);
            }
        }

        String unitTypeHitsInfo = String.format("hits on %s", toHit);
        if (modifier != 0) {
            String modifierToHitString = Integer.toString(modifier);
            if (modifier > 0) {
                modifierToHitString = "+" + modifierToHitString;
            }

            if ((toHit - modifier) <= 1) {
                unitTypeHitsInfo = String.format("always hits (%s mods)",
                    modifierToHitString);
            } else {
                unitTypeHitsInfo = String.format("hits on %s (%s mods)", (toHit - modifier),
                    modifierToHitString);
            }
        }
        String upgradedUnitName = "";
        if (unit.getUpgradesFromUnitId().isPresent() || unit.getFaction().isPresent()) {
            upgradedUnitName = String.format(" %s", unit.getName());
        }

        List<String> optionalInfoParts = Arrays.asList(upgradedUnitName, unitRollsTextInfo,
            unitTypeHitsInfo);
        String optionalText = optionalInfoParts.stream().filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(" "));

        String unitEmoji = Emojis.getEmojiFromDiscord(unit.getBaseType());

        String resultRollsString = "[" + resultRolls.stream().map(die -> Integer.toString(die.getResult())).collect(Collectors.joining(", ")) + "]";
        return String.format("%s %s%s %s - %s hit%s\n", unitQuantity, unitEmoji, optionalText,
            resultRollsString, numHit, hitsSuffix);
    }

    public static String displayModifiers(String prefixText, Map<UnitModel, Integer> units,
        List<NamedCombatModifierModel> modifiers) {
        String result = "";
        if (!modifiers.isEmpty()) {

            result += prefixText;
            List<String> modifierMessages = new ArrayList<>();
            for (NamedCombatModifierModel namedModifier : modifiers) {
                CombatModifierModel mod = namedModifier.getModifier();
                String unitScope = mod.getScope();
                if (StringUtils.isNotBlank(unitScope)) {
                    Optional<UnitModel> unitScopeModel = units.keySet().stream()
                        .filter(unit -> unit.getAsyncId().equals(mod.getScope())).findFirst();
                    if (unitScopeModel.isPresent()) {
                        unitScope = Emojis.getEmojiFromDiscord(unitScopeModel.get().getBaseType());
                    }
                } else {
                    unitScope = "all";
                }

                String plusPrefix = "+";
                Integer modifierValue = mod.getValue();
                if (modifierValue < 0) {
                    plusPrefix = "";
                }
                String modifierName = namedModifier.getName();
                if (StringUtils.isNotBlank(modifierName)) {
                    modifierMessages.add(modifierName);
                } else {
                    modifierMessages.add(String.format("%s%s for %s", plusPrefix, modifierValue, unitScope));
                }

            }
            result += String.join("\n", modifierMessages) + "\n";
        }
        return result;
    }

    public static String displayHitResults(int totalHits) {
        return String.format("\n**Total hits %s** %s\n", totalHits, ":boom:".repeat(Math.max(0, totalHits)));
    }

    public static String displayCombatSummary(Player player, Tile tile, UnitHolder combatOnHolder, CombatRollType rollType) {
        String holderName = combatOnHolder.getName();
        Planet holderPlanet = null;
        if (combatOnHolder instanceof Planet) {
            holderPlanet = (Planet) combatOnHolder;
        } else {
            holderName = tile.getRepresentation();
        }
        if (holderPlanet != null) {
            PlanetModel planetModel = Mapper.getPlanet(holderPlanet.getName());
            holderName = planetModel.getName();
        }

        String combatTypeName = StringUtils.capitalize(holderName) + " Combat";
        if (rollType != CombatRollType.combatround) {
            combatTypeName = rollType.getValue();
            if (holderPlanet != null) {
                combatTypeName += " on " + StringUtils.capitalize(holderName);
            }
        } else {
            int round = 0;
            Game game = player.getGame();
            String combatName = "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
            if (game.getStoredValue(combatName).isEmpty()) {
                round = 1;
            } else {
                if (game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                    round = Integer.parseInt(game.getStoredValue(combatName));
                } else {
                    round = Integer.parseInt(game.getStoredValue(combatName)) + 1;
                }
            }
            game.setStoredValue(combatName, "" + round);
            if (game.getStoredValue("thalnosPlusOne").equalsIgnoreCase("true")) {
                combatTypeName = combatTypeName + " (Thalnos Reroll for Round #" + round + ")";
            } else {
                combatTypeName = combatTypeName + " (Round #" + round + ")";
                if (game.getStoredValue("solagent").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround) {
                    game.setStoredValue("solagent", "");
                }
                if (game.getStoredValue("letnevagent").equalsIgnoreCase(player.getFaction()) && rollType == CombatRollType.combatround) {
                    game.setStoredValue("letnevagent", "");
                }
            }
        }
        return String.format("%s rolls for %s %s :  \n",
            player.getFactionEmoji(), combatTypeName, Emojis.RollDice);
    }
}
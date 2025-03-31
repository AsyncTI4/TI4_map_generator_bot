package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.DiceHelper.Die;
import ti4.image.Mapper;
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
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;

public class CombatMessageHelper {
    public static void displayDuplicateUnits(GenericInteractionCreateEvent event, List<String> dupes) {
        if (dupes.isEmpty()) return;
        // Gracefully fail when units don't exist
        String error = "You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n" +
            "> Duplicate units: " + dupes;
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
    }

    public static void displayMissingUnits(GenericInteractionCreateEvent event, List<String> missing) {
        if (missing.isEmpty()) return;
        // Gracefully fail when units don't exist
        String error = "You do not seem to own any of the following unit types, so they will be skipped." +
            " Ping bothelper if this seems to be in error.\n" +
            "> Unowned units: " + missing + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
    }

    public static String displayUnitRoll(UnitModel unitModel, int toHit, int modifier, int unitQuantity, int numRollsPerUnit, int extraRolls, List<Die> resultRolls, int numHit) {
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

        String unitTypeHitsInfo = String.format("hits on **%s**", toHit);
        if (modifier != 0) {
            String modifierToHitString = Integer.toString(modifier);
            if (modifier > 0) {
                modifierToHitString = "+" + modifierToHitString;
            }

            if ((toHit - modifier) <= 1) {
                unitTypeHitsInfo = String.format("always hits (%s mods)", modifierToHitString);
            } else {
                unitTypeHitsInfo = String.format("hits on **%s** (%s mods)", (toHit - modifier), modifierToHitString);
            }
        }
        String upgradedUnitName = "";
        if (unitModel.getUpgradesFromUnitId().isPresent() || unitModel.getFaction().isPresent()) {
            upgradedUnitName = unitModel.getName();
        }

        List<String> optionalInfoParts = Arrays.asList(upgradedUnitName, unitRollsTextInfo, unitTypeHitsInfo);
        String optionalText = optionalInfoParts.stream().filter(StringUtils::isNotBlank)
            .collect(Collectors.joining(" "));

        TI4Emoji unitEmoji = unitModel.getUnitEmoji();

        String resultRollsString = "[" + resultRolls.stream().map(Die::getRedDieIfSuccessOrGrayDieIfFailure).collect(Collectors.joining("")) + "]";
        if ("jolnar_flagship".equals(unitModel.getId())) {
            resultRollsString = resultRollsString.replace(DiceEmojis.d10red_9.toString(), DiceEmojis.d10blue_9.toString());
            resultRollsString = resultRollsString.replace(DiceEmojis.d10red_0.toString(), DiceEmojis.d10blue_0.toString());
        }
        
        String winnu_sigma = "";
        if ("sigma_winnu_flagship_2".equals(unitModel.getId())) {
            winnu_sigma = "-# The number of dice may not be correct; if so, you will need to manually roll the extra.\n";
        }

        return String.format("> `%sx`%s %s %s - %s hit%s\n%s", unitQuantity, unitEmoji, optionalText, resultRollsString, numHit, hitsSuffix, winnu_sigma);
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
                        unitScope = unitScopeModel.get().getUnitEmoji().toString();
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

        String combatTypeName = StringUtils.capitalize(holderName) + " combat";
        if (rollType != CombatRollType.combatround) {
            combatTypeName = rollType.getValue();
            if (rollType == CombatRollType.bombardment || rollType == CombatRollType.AFB)
            {
                combatTypeName = combatTypeName.toUpperCase();
            }
            if (holderPlanet != null) {
                combatTypeName += " on " + StringUtils.capitalize(holderName);
            }
            if (rollType == CombatRollType.SpaceCannonOffence) {
                combatTypeName += " at " + tile.getRepresentation();
            }
        } else {
            int round;
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
                combatTypeName += " (Thalnos reroll for round #" + round + ")";
            } else {
                combatTypeName += " (round #" + round + ")";
                if (game.getStoredValue("solagent").equalsIgnoreCase(player.getFaction())) {
                    game.setStoredValue("solagent", "");
                }
                if (game.getStoredValue("letnevagent").equalsIgnoreCase(player.getFaction())) {
                    game.setStoredValue("letnevagent", "");
                }
            }
        }
        return String.format("%s rolls for %s %s :  \n",
            player.getFactionEmoji(), combatTypeName, MiscEmojis.RollDice);
    }
}
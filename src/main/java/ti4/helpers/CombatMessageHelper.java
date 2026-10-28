package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.DiceHelper.Die;
import ti4.message.MessageHelper;
import ti4.model.CombatModifierModel;
import ti4.model.Mapper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.leader.genome.homebrew.tknova.AristocraticGenome;
import ti4.service.leader.genome.homebrew.tknova.HumanGenome;

public final class CombatMessageHelper {
    private static final Pattern MENTIONS_DICE =
            Pattern.compile("(?<![a-z])(die|dice|roll|rolls|rolled)(?![a-z])", Pattern.CASE_INSENSITIVE);

    public static void displayDuplicateUnits(GenericInteractionCreateEvent event, List<String> dupes) {
        if (dupes.isEmpty()) return;
        // Gracefully fail when units don't exist
        String error =
                "You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n"
                        + "> Duplicate units: " + dupes;
        if (event != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
        }
    }

    public static void displayMissingUnits(GenericInteractionCreateEvent event, List<String> missing) {
        if (missing.isEmpty()) return;
        // Gracefully fail when units don't exist
        String error = "You do not seem to own any of the following unit types, so they will be skipped."
                + " Ping bothelper if this seems to be in error.\n"
                + "> Unowned units: "
                + missing + "\n";
        if (event != null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), error);
        }
    }

    public static String displayUnitRoll(
            UnitModel unitModel,
            int toHit,
            int modifier,
            int unitQuantity,
            int numRollsPerUnit,
            int extraRolls,
            List<Die> resultRolls,
            int numHit) {
        return displayUnitRoll(
                unitModel, toHit, modifier, unitQuantity, numRollsPerUnit, extraRolls, resultRolls, numHit, "");
    }

    public static String displayUnitRoll(
            UnitModel unitModel,
            int toHit,
            int modifier,
            int unitQuantity,
            int numRollsPerUnit,
            int extraRolls,
            List<Die> resultRolls,
            int numHit,
            String holderLabel) {
        return displayUnitRoll(
                unitModel,
                toHit,
                modifier,
                unitQuantity,
                numRollsPerUnit,
                extraRolls,
                resultRolls,
                numHit,
                holderLabel,
                List.of());
    }

    public static String displayUnitRoll(
            UnitModel unitModel,
            int toHit,
            int modifier,
            int unitQuantity,
            int numRollsPerUnit,
            int extraRolls,
            List<Die> resultRolls,
            int numHit,
            String holderLabel,
            List<Integer> modifiersByDie) {
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
                unitRollsTextInfo = String.format("%s rolls (+%s rolls),", numRollsPerUnit, extraRolls);
            } else if (extraRolls > 0) {
                unitRollsTextInfo = String.format("(+%s rolls),", extraRolls);
            }
        }

        boolean hasVariableModifiers = hasVariableModifiers(modifier, modifiersByDie, resultRolls.size());
        String unitTypeHitsInfo = getUnitTypeHitsInfo(toHit, modifier, modifiersByDie, resultRolls.size());
        if (!hasVariableModifiers && modifier != 0) {
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
        if (unitModel.getUpgradesFromUnitId().isPresent()
                || unitModel.getFaction().isPresent()) {
            upgradedUnitName = unitModel.getName();
        }

        String holderLabelSep = StringUtils.isNotBlank(holderLabel) && StringUtils.isBlank(unitRollsTextInfo)
                ? holderLabel + ","
                : holderLabel;
        List<String> optionalInfoParts =
                Arrays.asList(upgradedUnitName, holderLabelSep, unitRollsTextInfo, unitTypeHitsInfo);
        String optionalText =
                optionalInfoParts.stream().filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));

        TI4Emoji unitEmoji = unitModel.getUnitEmoji();

        String resultRollsString = "["
                + resultRolls.stream()
                        .map(Die::getRedDieIfSuccessOrGrayDieIfFailure)
                        .collect(Collectors.joining("")) + "]";
        if ("jolnar_flagship".equals(unitModel.getId())) {
            resultRollsString =
                    resultRollsString.replace(DiceEmojis.d10red_9.toString(), DiceEmojis.d10blue_9.toString());
            resultRollsString =
                    resultRollsString.replace(DiceEmojis.d10red_0.toString(), DiceEmojis.d10blue_0.toString());
        }

        String nice = "";
        if (resultRolls.size() == 2
                && resultRolls.get(0).getResult() == 6
                && resultRolls.get(1).getResult() == 9) {
            nice = " (nice)";
        }

        String winnu_sigma = "";
        if ("sigma_winnu_flagship_2".equals(unitModel.getId())) {
            winnu_sigma =
                    "-# The number of dice may not be correct; if so, you will need to manually roll the extra.\n";
        }

        return String.format(
                "> `%sx`%s %s %s - %s hit%s%s\n%s",
                unitQuantity, unitEmoji, optionalText, resultRollsString, numHit, hitsSuffix, nice, winnu_sigma);
    }

    private static String getUnitTypeHitsInfo(int toHit, int modifier, List<Integer> modifiersByDie, int rollCount) {
        if (!hasVariableModifiers(modifier, modifiersByDie, rollCount)) {
            return toHit <= 1 ? "always hits" : String.format("hits on **%s**", toHit);
        }

        List<String> groups = new ArrayList<>();
        int groupStart = 0;
        while (groupStart < modifiersByDie.size()) {
            int groupModifier = modifiersByDie.get(groupStart);
            int groupEnd = groupStart + 1;
            while (groupEnd < modifiersByDie.size() && modifiersByDie.get(groupEnd) == groupModifier) {
                groupEnd++;
            }
            int groupDice = groupEnd - groupStart;
            String diceLabel = groupStart == 0
                    ? "first " + groupDice + (groupDice == 1 ? " die" : " dice")
                    : "remaining " + groupDice + (groupDice == 1 ? " die" : " dice");
            String modifierLabel = groupModifier > 0 ? "+" + groupModifier : Integer.toString(groupModifier);
            int threshold = Math.max(1, toHit - groupModifier);
            String hitLabel = threshold == 1 ? "always hits" : "hits on **" + threshold + "**";
            groups.add(hitLabel + " for " + diceLabel + " (" + modifierLabel + " mods)");
            groupStart = groupEnd;
        }
        return String.join("; ", groups);
    }

    private static boolean hasVariableModifiers(int modifier, List<Integer> modifiersByDie, int rollCount) {
        return modifiersByDie != null
                && modifiersByDie.size() == rollCount
                && modifiersByDie.stream().anyMatch(perDieModifier -> perDieModifier != modifier);
    }

    public static String displayModifiers(
            String prefixText, Map<UnitModel, Integer> units, List<NamedCombatModifierModel> modifiers) {
        String result = "";
        if (!modifiers.isEmpty()) {

            result += prefixText;
            List<String> modifierMessages = new ArrayList<>();
            for (NamedCombatModifierModel namedModifier : modifiers) {
                CombatModifierModel mod = namedModifier.getModifier();
                String unitScope = mod.getScope();
                if (StringUtils.isNotBlank(unitScope)) {
                    Optional<UnitModel> unitScopeModel = units.keySet().stream()
                            .filter(unit -> unit.getAsyncId().equals(mod.getScope()))
                            .findFirst();
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
                String modifierMessage;
                if (StringUtils.isNotBlank(modifierName)) {
                    if (mod.getMaxDice() != null) {
                        modifierMessage = String.format(
                                "%s%s to up to %s %s from %s",
                                plusPrefix,
                                modifierValue,
                                mod.getMaxDice(),
                                mod.getMaxDice() == 1 ? "die" : "dice",
                                modifierName);
                    } else {
                        modifierMessage = modifierName;
                    }
                } else {
                    if (mod.getMaxDice() != null) {
                        modifierMessage = String.format(
                                "%s%s to up to %s %s for %s",
                                plusPrefix,
                                modifierValue,
                                mod.getMaxDice(),
                                mod.getMaxDice() == 1 ? "die" : "dice",
                                unitScope);
                    } else {
                        modifierMessage = String.format("%s%s for %s", plusPrefix, modifierValue, unitScope);
                    }
                }
                modifierMessages.add(
                        withExtraDiceNotice(mod.getType(), mod.getValue(), mod.getValueScalingType(), modifierMessage));
            }
            result += String.join("\n", modifierMessages).replace("Optional[", "") + "\n";
        }
        return result;
    }

    public static String withExtraDiceNotice(
            String modifierType, int value, String valueScalingType, String modifierMessage) {
        if (!Constants.COMBAT_EXTRA_ROLLS.equals(modifierType)
                || MENTIONS_DICE.matcher(modifierMessage).find()) {
            return modifierMessage;
        }
        String scaling =
                StringUtils.isBlank(valueScalingType) ? "" : ", scaling with " + valueScalingType.replace('_', ' ');
        return String.format(
                "%s (%+d extra %s%s)", modifierMessage, value, Math.abs(value) == 1 ? "die" : "dice", scaling);
    }

    public static String displayHitResults(int totalHits) {
        return String.format("\n**Total hits %s** %s\n", totalHits, ":boom:".repeat(Math.max(0, totalHits)));
    }

    public static String displayHitResults(int totalHits, boolean x89) {
        String fmt = "\n**Total hits %s** %s\n";
        String emoji = x89 ? MiscEmojis.DoubleBoom.emojiString() : ":boom:";
        int hits = Math.max(0, x89 ? totalHits / 2 : totalHits);
        return String.format(fmt, totalHits, emoji.repeat(hits));
    }

    public static String displayCombatSummary(
            Player player, Tile tile, UnitHolder combatOnHolder, CombatRollType rollType) {
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
            if (rollType == CombatRollType.bombardment || rollType == CombatRollType.AFB) {
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
            String combatName =
                    "combatRoundTracker" + player.getFaction() + tile.getPosition() + combatOnHolder.getName();
            if (game.getStoredValue(combatName).isEmpty()) {
                round = 1;
            } else {
                if ("true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"))) {
                    round = Integer.parseInt(game.getStoredValue(combatName));
                } else {
                    round = Integer.parseInt(game.getStoredValue(combatName)) + 1;
                }
            }
            game.setStoredValue(combatName, "" + round);
            if ("true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"))) {
                combatTypeName += " (_Crown of Thalnos_ reroll for round #" + round + ")";
            } else {
                combatTypeName += " (round #" + round + ")";
                if (game.getStoredValue("solagent").equalsIgnoreCase(player.getFaction())) {
                    game.setStoredValue("solagent", "");
                }
                if (game.getStoredValue("letnevagent").equalsIgnoreCase(player.getFaction())) {
                    game.setStoredValue("letnevagent", "");
                }
                if (game.getStoredValue(HumanGenome.ID).equalsIgnoreCase(player.getFaction())) {
                    game.setStoredValue(HumanGenome.ID, "");
                }
                if (game.getStoredValue(AristocraticGenome.ID).equalsIgnoreCase(player.getFaction())) {
                    game.setStoredValue(AristocraticGenome.ID, "");
                }
                if (game.getStoredValue("classifiedWeapons").startsWith(player.getFaction() + ";")) {
                    game.setStoredValue("classifiedWeapons", "");
                }
            }
        }
        if (rollType == CombatRollType.bombardment
                && !player.getGame()
                        .getStoredValue("bombardmentTarget" + player.getFaction())
                        .isEmpty()) {
            String planet = player.getGame().getStoredValue("bombardmentTarget" + player.getFaction());
            return String.format(
                    "%s rolls for %s on %s %s :\n",
                    player.getFactionEmoji(),
                    combatTypeName,
                    Helper.getPlanetRepresentationNoResInf(planet, player.getGame()),
                    MiscEmojis.RollDice);
        }
        return String.format("%s rolls for %s %s :\n", player.getFactionEmoji(), combatTypeName, MiscEmojis.RollDice);
    }
}

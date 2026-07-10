package ti4.service.combat.v2;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.image.Mapper;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.v2.CombatV2RollData.Context;

/** Builds combat messages and button labels without sending messages or mutating game state. */
@UtilityClass
public class CombatV2Messages {

    public static String missingHolder(String holderName, String tilePosition) {
        return "Cannot find the planet " + holderName + " on tile " + tilePosition + ".";
    }

    public static String spaceCannonNeedsPlanet(String tilePosition) {
        return "Planet needs to be specified to fire SPACE CANNON against ships on tile " + tilePosition + ".";
    }

    public static String noUnits(
            String location, String tilePosition, String color, String factionEmoji, CombatRollType rollType) {
        return "There are no units in " + location + " on tile " + tilePosition + " for player " + color + " "
                + factionEmoji + " for the combat roll type " + rollType
                + "\nPing bothelper if this seems to be in error.";
    }

    public static String duplicateUnits(List<String> duplicates) {
        if (duplicates.isEmpty()) return "";
        return "You seem to own multiple of the following unit types. I will roll all of them, just ignore any that you shouldn't have.\n"
                + "> Duplicate units: " + duplicates;
    }

    public static String missingUnits(List<String> missing) {
        if (missing.isEmpty()) return "";
        return "You do not seem to own any of the following unit types, so they will be skipped."
                + " Ping bothelper if this seems to be in error.\n"
                + "> Unowned units: " + missing + "\n";
    }

    public static String starfallReminder(Player player) {
        return player.getFactionEmoji()
                + ", a reminder that due to the **Starfall Gunnery** ability, the SPACE CANNON of only 1 unit should be counted at this point."
                + " Hopefully you declared beforehand what that unit was, but by default it's probably the best one. Only look at/count the rolls of that one unit.";
    }

    public static String coexistencePrompt(Player attacker, String planetName, Game game) {
        return attacker.getRepresentation() + " the game is unsure if a combat should occur on "
                + Helper.getPlanetRepresentation(planetName, game)
                + " or if you are coexisting. Please inform it with the buttons.";
    }

    public static String articlesOfWarNaazRokha() {
        return "Skipping Z-Grav Eidolon (Naaz-Rokha mech) combat rolls due to _Articles of War_.";
    }

    public static String articlesOfWarXxcha() {
        return "Skipping Indomitus (Xxcha mech) SPACE CANNON rolls due to _Articles of War_.";
    }

    public static String articlesOfWarL1z1x() {
        return "Skipping Annihilator (L1Z1X mech) BOMBARDMENT rolls due to _Articles of War_.";
    }

    public static String noBombardmentTarget() {
        return "No valid bombardment target found. Please assign bombardment to a planet using the buttons and try again.";
    }

    public static String rollDisplayName(Context context) {
        String holderName = context.combatHolder() instanceof Planet
                ? Mapper.getPlanet(context.holderName()).getName()
                : context.tileRepresentation();
        String displayName = StringUtils.capitalize(context.rollTypeName());
        if (context.rollType() == CombatRollType.AFB || context.rollType() == CombatRollType.bombardment) {
            displayName = displayName.toUpperCase();
        }
        if (context.rollType() == CombatRollType.bombardment) {
            String target = context.storedValue("bombardmentTarget" + context.getFaction());
            if (!target.isBlank()) {
                displayName += " on " + Helper.getPlanetRepresentationNoResInf(target, context.game());
            }
        } else if (context.combatHolder() instanceof Planet) {
            displayName += " on " + StringUtils.capitalize(holderName);
        }
        if (context.rollType() == CombatRollType.SpaceCannonOffence) {
            displayName += " at " + context.tileRepresentation();
        }
        return displayName;
    }

    public static String combatRoundDisplayName(String location, boolean thalnos, int round) {
        String name = org.apache.commons.lang3.StringUtils.capitalize(location) + " combat";
        return name + (thalnos ? " (_Crown of Thalnos_ reroll for round #" + round + ")" : " (round #" + round + ")");
    }

    public static String proximaCanceled(int canceled) {
        return "\n_Proxima Targeting VI_ canceled " + canceled + " hit" + (canceled == 1 ? "" : "s")
                + " automatically.";
    }

    public static String startOfRound(int round) {
        return "## __Start of Combat Round #" + round + "__";
    }

    public static String privateRollSent(Player opponent) {
        return "Roll result was sent to " + opponent.getRepresentationNoPing();
    }

    public static String privateSpaceCannonRelay(Player opponent, String parsedRoll) {
        return opponent.getRepresentationUnfogged() + " " + parsedRoll;
    }

    public static String thalnosPrompt() {
        return "Use this button to roll for Thalnos.\n-# Note that if it matters, the dice were just rolled in the following format: (normal dice for unit 1)+(normal dice for unit 2)...etc...+(extra dice for unit 1)+(extra dice for unit 2)...etc.\n-# Sol and Letnev agents automatically are given as extra dice for unit 1.";
    }

    public static String surprisingRoll(
            Player roller, Player opponent, String gameName, String rollMessage, boolean surprisinglyGood) {
        String description = surprisinglyGood ? " has rolled grievously against " : " has rolled dismally against ";
        StringBuilder message = new StringBuilder(roller.getRepresentation())
                .append(description)
                .append(opponent.getRepresentation())
                .append(" in ")
                .append(gameName)
                .append('.');
        for (String line : rollMessage.split("\n")) {
            if (line.startsWith("> `") || line.startsWith("**Total hits"))
                message.append('\n').append(line);
        }
        return message.toString();
    }

    public static String afbAssignment(Player opponent, int hits) {
        return opponent.getRepresentation() + ", you may automatically assign " + definiteHits(hits) + " from AFB.";
    }

    public static String spaceCannonSuffered(Player opponent, int hits) {
        return "\n" + opponent.getRepresentation(true, true, true, true) + " suffered "
                + StringHelper.pluralize(hits, "hit") + " from SPACE CANNON against your ships.";
    }

    public static String automaticAssignment(Player opponent, int hits, String automaticResult) {
        return opponent.getRepresentationNoPing() + ", you may automatically assign " + definiteHits(hits) + "."
                + automaticResult;
    }

    public static String combatHitsSuffered(Player opponent, int hits, int round) {
        return "\n" + opponent.getRepresentation(true, true, true, true) + ", you suffered "
                + StringHelper.pluralize(hits, "hit") + " in round #" + round + ".";
    }

    public static String valkyrieSuffered(Player player) {
        return player.getRepresentation() + " suffered 1 hit due to _Valkyrie Particle Weave_.";
    }

    public static String mayRollNextRound(Player player, int round) {
        return player.getRepresentationUnfogged() + " you may roll dice for Combat Round #" + round + ".";
    }

    public static String mayAutoAssign(Player player, int hits) {
        return player.getRepresentationUnfogged() + " you may autoassign " + StringHelper.pluralize(hits, "hit") + ".";
    }

    public static String valkyrieAssignment(Player player) {
        return player.getRepresentationUnfogged()
                + " you got hit by _Valkyrie Particle Weave_. You may autoassign 1 hit.";
    }

    public static String spaceAssignment(
            Player opponent, int hits, String automaticResult, String relicName, boolean defensiveArchitecture) {
        String message = opponent.getRepresentationNoPing() + ", you may automatically assign " + definiteHits(hits)
                + ". " + automaticResult;
        if (relicName != null) {
            message += "\nReminder: You have the _" + relicName
                    + "_ relic, you may SUSTAIN DAMAGE on one of your non-fighter ships instead of taking a hit.";
        }
        if (defensiveArchitecture) {
            message +=
                    "\nReminder: You have _Defensive Architecture_.\nFor each unit in the active system that is at capacity, you may give one other non-fighter ship in the same system SUSTAIN DAMAGE until the end of this combat. This is not tracked by the bot.";
        }
        return message;
    }

    public static String bombardmentNotRelayed(Player player) {
        return player.getRepresentationUnfogged()
                + " This roll result is not automatically relayed. Please communicate the hits to the opponent manually.";
    }

    public static String bombardmentAssignment(Player player, int hits) {
        return player.getRepresentation() + ", please assign the BOMBARDMENT hit" + pluralSuffix(hits) + ".";
    }

    public static String dummyBombardmentAssignment(Player roller, int hits) {
        return roller.getRepresentation() + ", please assign the BOMBARDMENT hit" + pluralSuffix(hits)
                + " for the dummy player.";
    }

    public static String meteorSlings(Player player, int hits) {
        return player.getRepresentation() + " you could potentially cancel "
                + (hits == 1 ? "the BOMBARDMENT hit" : "some BOMBARDMENT hits")
                + " to place infantry instead. Use these buttons to do so, and press done when done. The bot did not track how many hits you got. ";
    }

    public static String x89Exhausted(Player target, String planet, Game game, Player roller) {
        return target.getRepresentation() + ", your planet " + Helper.getPlanetRepresentation(planet, game)
                + " was exhausted when " + (game.isFowMode() ? "another player" : roller.getRepresentationNoPing())
                + " bombarded it with _X-89 Bacterial Weapon ΩΩ_.";
    }

    public static String x89AdditionalHits(Player player, int hits) {
        return "\n" + player.getFactionEmoji() + " produced " + StringHelper.pluralize(hits, "additional hit")
                + " using " + Mapper.getTech("x89c4").getNameRepresentation() + ".";
    }

    public static String shardVolley(Player player) {
        return "\n" + player.getFactionEmoji()
                + " You have _Shard Volley_ and thus produced an additional hit to the ones rolled above.";
    }

    public static String shardSaturation(Player player) {
        return "\n" + player.getFactionEmoji()
                + " You have _Shard Saturation_ and thus produced an additional hit to the ones rolled above.";
    }

    public static String gloryValor(Player player, boolean twilightFall) {
        return player.getRepresentation() + " got an extra hit due to the **"
                + (twilightFall ? "Glorious Halls" : "Valor")
                + "** ability (it has been accounted for in the hit count).";
    }

    public static String vadenBombardmentTradeGood(Player player) {
        return player.getRepresentation()
                + " gained 1 trade good due to hitting on a BOMBARDMENT roll with the Aurum Vadra (the Vaden flagship).";
    }

    public static String belkoseaCommodities(Player player, int hits) {
        return player.getRepresentation() + " please gain or convert 1 commodity a total of "
                + StringHelper.pluralize(hits, "time") + " due to your Uzean Wardog mech ability.";
    }

    public static String mercenaryCaptains(Player player) {
        return player.getRepresentation() + " you gained 1 commodity due to the mercenary captains ability.";
    }

    public static String dragonBombardment(Player target, int hits, String planet, Game game) {
        return target.getRepresentation() + ", please assign the Dragon BOMBARDMENT hit" + pluralSuffix(hits) + " on "
                + Helper.getPlanetRepresentation(planet, game) + ".";
    }

    public static String dragonBombardmentForDummy(Player roller, int hits, String planet, Game game) {
        return roller.getRepresentation() + ", please assign the Dragon BOMBARDMENT hit" + pluralSuffix(hits)
                + " for the dummy player on " + Helper.getPlanetRepresentation(planet, game) + ".";
    }

    public static String gledgePds2Explore(Player player) {
        return player.getRepresentation()
                + ", use the buttons to explore a planet with the PDS that got the hit. It should be noted that the bot has no idea which PDS rolled which dice, but default practice would be to go from lowest tile position to highest, with _Plasma Scoring_ applying to the last die. You can specify any order before rolling though.";
    }

    public static String gledgePdsExplore(Player player) {
        return player.getRepresentation() + " use the buttons to explore a planet with the PDS that got the hit.";
    }

    public static String autoAssignLabel(int hits, boolean dummy) {
        return "Auto-assign Hit" + pluralSuffix(hits) + (dummy ? " For Dummy" : "");
    }

    public static String afbAutoAssignLabel(int hits, boolean dummy) {
        return "Auto-assign Hit" + (hits == 1 ? "" : dummy ? "s For Dummy" : "s");
    }

    public static String manualAssignLabel(int hits) {
        return "Manually Assign Hit" + pluralSuffix(hits);
    }

    public static String assignLabel(int hits) {
        return "Assign Hit" + pluralSuffix(hits);
    }

    public static String nextRollLabel(boolean dummy, int round) {
        return "Roll Dice " + (dummy ? "For Dummy " : "") + "For Combat Round #" + round;
    }

    public static String infantryOnPlanet(String planet, Game game) {
        return "Infantry on " + Helper.getPlanetRepresentation(planet, game);
    }

    public static String engageInCombatLabel() {
        return "Engage in Combat";
    }

    public static String coexistLabel() {
        return "They Are Coexisting";
    }

    public static String rollThalnosLabel() {
        return "Roll Thalnos";
    }

    public static String declineLabel() {
        return "Decline";
    }

    public static String cancelHitLabel() {
        return "Cancel a Hit";
    }

    public static String autoAssignHitsForDummyLabel() {
        return "Auto-assign Hits For Dummy";
    }

    public static String doneLabel() {
        return "Done";
    }

    private static String definiteHits(int hits) {
        return hits == 1 ? "the hit" : "hits";
    }

    private static String pluralSuffix(int hits) {
        return hits == 1 ? "" : "s";
    }
}

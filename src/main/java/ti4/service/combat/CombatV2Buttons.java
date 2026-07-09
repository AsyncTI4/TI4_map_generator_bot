package ti4.service.combat;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Player;
import ti4.service.combat.CombatV2RollData.Context;
import ti4.service.combat.CombatV2RollData.Round;
import ti4.service.emoji.ExploreEmojis;

/** Builds combat button groups while preserving the established Discord custom IDs and labels. */
@UtilityClass
class CombatV2Buttons {

    static List<Button> thalnos(Context context) {
        return List.of(
                Buttons.green(
                        "startThalnos_" + context.getTilePosition() + "_" + context.unitHolderName(),
                        CombatV2Messages.rollThalnosLabel(),
                        ExploreEmojis.Relic),
                Buttons.gray("deleteButtons", CombatV2Messages.declineLabel()));
    }

    static List<Button> antiFighterBarrage(Context context, Player opponent, int hits) {
        List<Button> buttons = new ArrayList<>();
        if (opponent.isNpc() || opponent.isDummy()) {
            buttons.add(Buttons.green(
                    opponent.dummyPlayerSpoof() + "autoAssignAFBHits_" + context.getTilePosition() + "_" + hits,
                    CombatV2Messages.afbAutoAssignLabel(hits, true)));
            return buttons;
        }
        String checker = opponent.factionButtonChecker();
        buttons.add(Buttons.green(
                checker + "autoAssignAFBHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.afbAutoAssignLabel(hits, false)));
        buttons.add(Buttons.red(
                checker + "getDamageButtons_" + context.getTilePosition() + "_afb",
                CombatV2Messages.manualAssignLabel(hits)));
        buttons.add(Buttons.gray(
                checker + "cancelAFBHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.cancelHitLabel()));
        return buttons;
    }

    static List<Button> spaceCannonOffense(Context context, Player opponent, int hits) {
        List<Button> buttons = new ArrayList<>();
        String checker = opponent.factionButtonChecker();
        String autoAssign = opponent.isDummy() || opponent.isNpc()
                ? opponent.dummyPlayerSpoof() + "autoAssignSpaceCannonOffenceHits_"
                : checker + "autoAssignSpaceCannonOffenceHits_";
        buttons.add(Buttons.green(
                autoAssign + context.getTilePosition() + "_" + hits,
                opponent.isDummy() || opponent.isNpc()
                        ? CombatV2Messages.afbAutoAssignLabel(hits, true)
                        : CombatV2Messages.autoAssignLabel(hits, false)));
        buttons.add(Buttons.red(
                "getDamageButtons_" + context.getTilePosition() + "deleteThis_pds",
                CombatV2Messages.manualAssignLabel(hits)));
        buttons.add(Buttons.gray(
                checker + "cancelPdsOffenseHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.cancelHitLabel()));
        return buttons;
    }

    static List<Button> nextRound(Context context, Round round, Player opponent) {
        if (round.rollingSideRound() <= round.opponentRound()) return new ArrayList<>();
        String id = "combatRoll_" + context.getTilePosition() + "_" + context.holderName();
        if (opponent.isDummy() || opponent.isNpc()) id = opponent.dummyPlayerSpoof() + id;
        return new ArrayList<>(List.of(Buttons.blue(
                id,
                CombatV2Messages.nextRollLabel(opponent.isDummy() || opponent.isNpc(), round.opponentRound() + 1))));
    }

    static List<Button> groundAssignment(Context context, Player opponent, int hits, List<Button> buttons) {
        if (opponent.isDummy() || opponent.isNpc()) {
            buttons.add(Buttons.green(
                    opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + context.holderName() + "_" + hits,
                    CombatV2Messages.autoAssignLabel(hits, true)));
            return buttons;
        }
        String checker = opponent.factionButtonChecker();
        buttons.add(Buttons.green(
                checker + "autoAssignGroundHits_" + context.holderName() + "_" + hits,
                CombatV2Messages.autoAssignLabel(hits, false)));
        buttons.add(Buttons.red(
                "getDamageButtons_" + context.getTilePosition() + "deleteThis_groundcombat",
                CombatV2Messages.manualAssignLabel(hits)));
        buttons.add(Buttons.gray(
                checker + "cancelGroundHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.cancelHitLabel()));
        return buttons;
    }

    static List<Button> valkyrie(Context context, Player roller, int originalHits) {
        return new ArrayList<>(List.of(
                Buttons.green(
                        roller.factionButtonChecker() + "autoAssignGroundHits_" + context.holderName() + "_1",
                        CombatV2Messages.autoAssignLabel(originalHits, false)),
                Buttons.red(
                        "getDamageButtons_" + context.getTilePosition() + "deleteThis_groundcombat",
                        CombatV2Messages.manualAssignLabel(originalHits)),
                Buttons.gray(
                        roller.factionButtonChecker() + "cancelGroundHits_" + context.getTilePosition() + "_1",
                        CombatV2Messages.cancelHitLabel())));
    }

    static List<Button> spaceAssignment(Context context, Player opponent, int hits, List<Button> buttons) {
        if (opponent.isDummy() || opponent.isNpc()) {
            buttons.add(Buttons.green(
                    opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + context.getTilePosition() + "_" + hits,
                    CombatV2Messages.autoAssignLabel(hits, true)));
            return buttons;
        }
        String checker = opponent.factionButtonChecker();
        buttons.add(Buttons.green(
                checker + "autoAssignSpaceHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.autoAssignLabel(hits, false)));
        buttons.add(Buttons.red(
                "getDamageButtons_" + context.getTilePosition() + "deleteThis_spacecombat",
                CombatV2Messages.manualAssignLabel(hits)));
        buttons.add(Buttons.gray(
                checker + "cancelSpaceHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.cancelHitLabel()));
        return buttons;
    }

    static Button dummyGroundAssignment(Context context, Player opponent, int hits) {
        return Buttons.green(
                opponent.dummyPlayerSpoof() + "autoAssignGroundHits_" + context.holderName() + "_" + hits,
                CombatV2Messages.autoAssignLabel(hits, true));
    }

    static Button dummySpaceAssignment(Context context, Player opponent, int hits) {
        return Buttons.green(
                opponent.dummyPlayerSpoof() + "autoAssignSpaceHits_" + context.getTilePosition() + "_" + hits,
                CombatV2Messages.autoAssignHitsForDummyLabel());
    }

    static List<Button> bombardmentAssignment(Context context, int hits) {
        return List.of(Buttons.red(
                "getDamageButtons_" + context.getTilePosition() + "_bombardment", CombatV2Messages.assignLabel(hits)));
    }

    static List<Button> dummyBombardmentAssignment(Player target, String planet, int hits) {
        return List.of(Buttons.green(
                target.dummyPlayerSpoof() + "autoAssignGroundHits_" + planet + "_" + hits,
                CombatV2Messages.autoAssignLabel(hits, true)));
    }

    static List<Button> meteorSlings(Context context, String planet) {
        return List.of(
                Buttons.green(
                        context.factionButtonChecker() + "meteorSlings_" + planet,
                        CombatV2Messages.infantryOnPlanet(planet, context.game())),
                Buttons.red("deleteButtons", CombatV2Messages.doneLabel()));
    }
}

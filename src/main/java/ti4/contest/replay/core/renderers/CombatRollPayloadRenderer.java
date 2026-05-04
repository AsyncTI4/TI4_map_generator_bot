package ti4.contest.replay.core.renderers;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNote;
import ti4.contest.replay.core.CombatRollPayload.CombatRollNotePlacement;
import ti4.contest.replay.core.CombatRollPayload.DieRoll;
import ti4.contest.replay.core.CombatRollPayload.ModifierDisplay;
import ti4.contest.replay.core.CombatRollPayload.UnitRoll;
import ti4.service.emoji.DiceEmojis;
import ti4.service.emoji.MiscEmojis;

/**
 * Converts structured combat roll payloads into the Discord markdown shown during replay.
 */
@UtilityClass
public class CombatRollPayloadRenderer {

    public String render(CombatRollPayload payload) {
        if (payload == null) return "";
        StringBuilder message = new StringBuilder();
        appendHeader(message, payload);
        appendNotes(message, payload.notes(), CombatRollNotePlacement.BEFORE_MODIFIERS);
        appendModifiers(message, payload.modifiers());
        appendNotes(message, payload.notes(), CombatRollNotePlacement.BEFORE_UNIT_ROLLS);
        appendUnitRolls(message, payload.unitRolls());
        appendNotes(message, payload.notes(), CombatRollNotePlacement.AFTER_UNIT_ROLLS);
        appendTotal(message, payload);
        appendNotes(message, payload.notes(), CombatRollNotePlacement.AFTER_TOTAL);
        return message.toString();
    }

    private void appendHeader(StringBuilder message, CombatRollPayload payload) {
        CombatRollPayload.RollHeader header = payload.header();
        if (header == null) return;
        message.append(header.actorEmoji())
                .append(" rolls for ")
                .append(header.combatDisplayName())
                .append(" ")
                .append(MiscEmojis.RollDice)
                .append(" :\n");
    }

    private void appendModifiers(StringBuilder message, List<ModifierDisplay> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) return;
        message.append("With modifiers: \n");
        for (ModifierDisplay modifier : modifiers) {
            if (StringUtils.isNotBlank(modifier.sourceName())) {
                message.append(modifier.sourceName().replace("Optional[", "")).append("\n");
            } else {
                String plusPrefix = modifier.value() < 0 ? "" : "+";
                String scope = StringUtils.defaultIfBlank(modifier.scopeDisplay(), "all");
                message.append(plusPrefix)
                        .append(modifier.value())
                        .append(" for ")
                        .append(scope)
                        .append("\n");
            }
        }
    }

    private void appendUnitRolls(StringBuilder message, List<UnitRoll> unitRolls) {
        if (unitRolls == null || unitRolls.isEmpty()) return;
        for (UnitRoll unitRoll : unitRolls) {
            appendRollPrefix(message, unitRoll);
            message.append(renderUnitRoll(unitRoll));
        }
    }

    private void appendRollPrefix(StringBuilder message, UnitRoll unitRoll) {
        int count = Math.max(0, unitRoll.dice().size());
        switch (unitRoll.segmentType()) {
            case JOL_NAR_COMMANDER_REROLL_HITS ->
                message.append("Rerolling ")
                        .append(count)
                        .append(" hit")
                        .append(count == 1 ? "" : "s")
                        .append(" due to Ta Zern, the Jol-Nar Commander:\n");
            case JOL_NAR_COMMANDER_REROLL_MISSES ->
                message.append("Rerolling ")
                        .append(count)
                        .append(" miss")
                        .append(count == 1 ? "" : "es")
                        .append(" due to Ta Zern, the Jol-Nar Commander:\n");
            case KALTRIM_COMMANDER_REROLL_ONES ->
                message.append("Rerolling ")
                        .append(count)
                        .append(" roll")
                        .append(count == 1 ? "" : "s")
                        .append(" of 1 due to the Kaltrim Commander:\n ");
            case MUNITIONS_RESERVES_REROLL ->
                message.append("**Munitions Reserve** rerolling ")
                        .append(count)
                        .append(" miss")
                        .append(count == 1 ? "" : "es")
                        .append(": ");
            default -> {}
        }
    }

    private String renderUnitRoll(UnitRoll unitRoll) {
        String hitsSuffix = unitRoll.hits() > 1 ? "s" : "";
        int totalRolls = (unitRoll.dicePerUnit() * unitRoll.quantity()) + unitRoll.extraDice();
        String unitRollsTextInfo = "";
        if (totalRolls > 1) {
            unitRollsTextInfo = unitRoll.dicePerUnit() + " rolls,";
            if (unitRoll.extraDice() > 0 && unitRoll.dicePerUnit() > 1) {
                unitRollsTextInfo = unitRoll.dicePerUnit() + " rolls (+" + unitRoll.extraDice() + " rolls),";
            } else if (unitRoll.extraDice() > 0) {
                unitRollsTextInfo = "(+" + unitRoll.extraDice() + " rolls),";
            }
        }

        String unitTypeHitsInfo = "hits on **" + unitRoll.printedHitsOn() + "**";
        if (unitRoll.modifier() != 0) {
            String modifier =
                    unitRoll.modifier() > 0 ? "+" + unitRoll.modifier() : Integer.toString(unitRoll.modifier());
            if (unitRoll.effectiveThreshold() <= 1) {
                unitTypeHitsInfo = "always hits (" + modifier + " mods)";
            } else {
                unitTypeHitsInfo = "hits on **" + unitRoll.effectiveThreshold() + "** (" + modifier + " mods)";
            }
        }

        String optionalText = String.join(
                        " ",
                        List.of(
                                StringUtils.defaultString(unitRoll.unitDisplayName()),
                                unitRollsTextInfo,
                                unitTypeHitsInfo))
                .replaceAll(" +", " ")
                .trim();
        String nice = isNice(unitRoll.dice()) ? " (nice)" : "";
        String winnuSigma = "sigma_winnu_flagship_2".equals(unitRoll.unitId())
                ? "-# The number of dice may not be correct; if so, you will need to manually roll the extra.\n"
                : "";
        return String.format(
                "> `%sx`%s %s [%s] - %s hit%s%s\n%s",
                unitRoll.quantity(),
                unitRoll.unitEmoji(),
                optionalText,
                renderDice(unitRoll),
                unitRoll.hits(),
                hitsSuffix,
                nice,
                winnuSigma);
    }

    private String renderDice(UnitRoll unitRoll) {
        StringBuilder dice = new StringBuilder();
        for (var die : unitRoll.dice()) {
            dice.append(renderDie(unitRoll.unitId(), die));
        }
        return dice.toString();
    }

    private String renderDie(String unitId, DieRoll die) {
        if (die.success()) {
            String redDie = DiceEmojis.getRedDieEmoji(die.result());
            if ("jolnar_flagship".equals(unitId) && (die.result() == 9 || die.result() == 10)) {
                return DiceEmojis.getDieEmoji("blue", die.result());
            }
            return redDie;
        }
        return DiceEmojis.getGrayDieEmoji(die.result());
    }

    private boolean isNice(List<DieRoll> dice) {
        return dice != null
                && dice.size() == 2
                && dice.get(0).result() == 6
                && dice.get(1).result() == 9;
    }

    private void appendTotal(StringBuilder message, CombatRollPayload payload) {
        if (payload.total() == null) return;
        int totalHits = payload.total().displayedTotalHits();
        message.append(String.format("\n**Total hits %s** %s\n", totalHits, ":boom:".repeat(Math.max(0, totalHits))));
    }

    private void appendNotes(StringBuilder message, List<CombatRollNote> notes, CombatRollNotePlacement placement) {
        if (notes == null || notes.isEmpty()) return;
        for (CombatRollNote note : notes) {
            if (note.placement() == placement) {
                appendNote(message, note);
            }
        }
    }

    private void appendNote(StringBuilder message, CombatRollNote note) {
        switch (note.type()) {
            case UNIT_REPAIRED -> appendRepairNote(message, note);
            case SINGLE_UNIT_ROLL_MOD_APPLIED -> appendSingleUnitModNote(message, note);
            case UNIT_DESTROYED_FROM_ROLL -> appendUnitDestroyedNote(message, note);
            case OPPONENT_UNIT_DESTROYED_FROM_ROLL -> appendOpponentUnitDestroyedNote(message, note);
            case REROLL_AVAILABLE -> appendRerollAvailableNote(message, note);
        }
    }

    private void appendRepairNote(StringBuilder message, CombatRollNote note) {
        if ("letnev_flagship".equals(note.sourceId())) {
            message.append("Repaired the Arc Secundus at start of this combat round with its ability.\n");
        } else if ("naaz_voltron".equals(note.sourceId())) {
            message.append("The Eidolon Maximum self-repaired at the start of this combat round.\n");
        }
    }

    private void appendSingleUnitModNote(StringBuilder message, CombatRollNote note) {
        String modifier = note.details().getOrDefault("modifier", "0");
        if ("tf-supercharge".equals(note.sourceId())) {
            message.append("Applied +").append(modifier).append(" to the rolls of 1 unit with _Supercharge_.\n");
        } else if ("letnevbt".equals(note.sourceId())) {
            message.append("Applied +")
                    .append(modifier)
                    .append(" to the rolls of 1 unit with _Gravleash Maneuvers_.\n");
        }
    }

    private void appendUnitDestroyedNote(StringBuilder message, CombatRollNote note) {
        String actorEmoji = note.details().getOrDefault("actorEmoji", "");
        String unitName = note.details().getOrDefault("unitName", note.unitId());
        int count = note.count() == null ? 0 : note.count();
        message.append("\n\n")
                .append(actorEmoji)
                .append(" destroyed ")
                .append(count)
                .append(" of their own ")
                .append(unitName)
                .append(count == 1 ? "" : "s")
                .append(" due to ")
                .append(count == 1 ? "a Thalnos miss" : "Thalnos misses")
                .append(".");
    }

    private void appendOpponentUnitDestroyedNote(StringBuilder message, CombatRollNote note) {
        int count = note.count() == null ? 0 : note.count();
        String opponent = note.details().getOrDefault("opponent", "the opponent");
        message.append("\nDue to the Strike Wing Alpha II destroyer ability, ")
                .append(count)
                .append(" of ")
                .append(opponent)
                .append(" infantry were destroyed\n");
    }

    private void appendRerollAvailableNote(StringBuilder message, CombatRollNote note) {
        int misses = note.count() == null ? 0 : note.count();
        String actorEmoji = note.details().getOrDefault("actorEmoji", "");
        message.append("\n")
                .append(actorEmoji)
                .append(" You have _The Crown of Thalnos_ and may reroll ")
                .append(misses == 1 ? "the miss" : "misses")
                .append(", adding +1, at the risk of your ")
                .append(misses == 1 ? "troop's life" : "troops' lives")
                .append(".");
    }
}

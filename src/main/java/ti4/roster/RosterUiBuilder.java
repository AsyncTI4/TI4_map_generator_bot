package ti4.roster;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.entities.Member;
import ti4.buttons.Buttons;

public class RosterUiBuilder {

    public static String renderRoster(String gameFunName, List<Member> members) {
        StringBuilder buttonMsg = new StringBuilder();
        buttonMsg.append("Game Fun Name: ").append(gameFunName.replace(":", "")).append("\nPlayers:\n");
        int counter = 1;
        for (Member member : members) {
            buttonMsg
                    .append(counter)
                    .append(":")
                    .append(member.getId())
                    .append(".(")
                    .append(member.getEffectiveName().replace(":", ""))
                    .append(")\n");
            counter++;
        }
        buttonMsg
                .append("\n\n")
                .append(" Please hit the Create Game button after confirming that the members are the correct ones.");
        return buttonMsg.toString();
    }

    public static List<ActionRow> initialActionRows() {
        List<ActionRow> rows = new ArrayList<>();
        // Row 1: Add/Remove Myself
        rows.add(ActionRow.of(
                Buttons.green("roster_add_self", "Add Myself"),
                Buttons.gray("roster_remove_self", "Remove Myself"),
                Buttons.green("roster_add_someone", "Add Someone")));

        // Row 2: Remove Someone, Edit Name, Create Game
        rows.add(ActionRow.of(
                Buttons.gray("roster_remove_someone", "Remove Someone"),
                Buttons.blue("roster_edit_name", "Edit Game Name"),
                Buttons.green("createGameChannels", "Create Game")));

        return rows;
    }
}

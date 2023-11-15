package ti4.selections.selectmenus;

import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.selections.Selection;

public class SelectFaction implements Selection {

    @Getter
    String selectionID = "select_faction";

    @Override
    public void execute(StringSelectInteractionEvent event) {
        event.getChannel().sendMessage("You're selecting a faction!").queue();
    }
    
}

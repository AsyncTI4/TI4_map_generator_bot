package ti4.jda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;

@UtilityClass
public class JdaComponentHelper {

    public static void removeComponentFromMessage(ComponentInteraction event) {
        event.editComponents(event.getMessage()
                        .getComponentTree()
                        .replace(ComponentReplacer.byUniqueId(event.getUniqueId(), (Component) null)))
                .queue();
    }

    public static boolean removeComponentFromMessageAndDeleteIfEmpty(ComponentInteraction event) {
        boolean isSingleComponent = event.getMessage().getComponents().size() == 1;
        if (isSingleComponent) {
            event.getMessage().delete().queue();
            return true;
        }
        removeComponentFromMessage(event);
        return false;
    }
}

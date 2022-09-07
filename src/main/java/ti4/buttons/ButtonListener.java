package ti4.buttons;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ButtonListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (Objects.equals(event.getButton().getId(), "test_button1")){

           event.reply("button pressed" + event.getButton().getId()).queue();
//            event.getChannel().sendMessage("aa").queue();
        }
    }
}

package ti4.service.option;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

@UtilityClass
public class FOWOptionService {
  
    public enum FOWOption {
        MANAGED_COMMS("Managed comms", "Use managed player-to-player communication threads"),
        ALLOW_AGENDA_COMMS("Allow comms in agenda", "Managed player-to-player communication threads allow talking with everyone in agenda phase"),
        HIDE_TOTAL_VOTES("Hide total votes", "Hide total votes amount in agenda"),
        HIDE_VOTE_ORDER("Hide voting order", "Hide player colors from vote order"),
        HIDE_PLAYER_NAMES("Hide real names", "Completely hide player Discord names on the map"),
        STATUS_SUMMARY("Status summary", "Prints explores info as summary thread in status homework"),
        FOW_PLUS("Extra Dark Mode", "Hello darkness my old friend... WIP"),

        //Hidden from normal options
        RIFTSET_MODE("RiftSet Mode", "For Eronous to run fow300", false);

        private final String title;
        private final String description;
        private final boolean visible;

        FOWOption(String title, String description) {
            this(title, description, true);
        }

        FOWOption(String title, String description, boolean visible) {
            this.title = title;
            this.description = description;
            this.visible = visible;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public boolean isVisible() {
            return visible;
        }

        public static FOWOption fromString(String value) {
            for (FOWOption option : FOWOption.values()) {
                if (option.name().equalsIgnoreCase(value)) {
                    return option;
                }
            }
            throw new IllegalArgumentException("No FOWOption enum for '" + value + "'");
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public static void offerFOWOptionButtons(Game game, MessageChannel channel) {
        List<Button> optionButtons = new ArrayList<>();
        StringBuilder sb = new StringBuilder("## FoW Options\n");
        for (FOWOption option : FOWOption.values()) {
            if (!option.isVisible()) continue;

            boolean currentValue = game.getFowOption(option);
            sb.append("**").append(option.getTitle()).append("**: ").append(valueRepresentation(currentValue)).append("\n");
            sb.append("-# ").append(option.getDescription()).append("\n");

            optionButtons.add(currentValue
                ? Buttons.red("fowOption_false_" + option, "Disable " + option.getTitle())
                : Buttons.green("fowOption_true_" + option, "Enable " + option.getTitle()));
        }
        optionButtons.add(Buttons.gray("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, sb.toString(), optionButtons);
    }

    @ButtonHandler("fowOption_")
    public static void changeFOWOptions(ButtonInteractionEvent event, Game game, String buttonID) {
        String[] parts = buttonID.split("fowOption_")[1].split("_", 2);
        String value = parts[0];
        String option = parts[1];

        game.setFowOption(FOWOption.fromString(option), Boolean.parseBoolean(value));
        ButtonHelper.deleteMessage(event);
        offerFOWOptionButtons(game, event.getChannel());
    }

    public static String valueRepresentation(boolean value) {
        return value ? "✅" : "🚫";
    }
}

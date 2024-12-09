package ti4.buttons.handlers.map;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.listeners.annotations.ButtonHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.map.Game;
import ti4.service.map.AddTileListService;

class AddTileListButtonHandler {

    @ButtonHandler("addMapString~MDL")
    public static void presentMapStringModal(ButtonInteractionEvent event, Game game) {
        String modalId = "addMapString";
        String fieldID = "mapString";
        TextInput tags = TextInput.create(fieldID, "Enter Map String", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Paste the map string here.")
            .setValue(game.getMapString())
            .setRequired(true)
            .build();
        Modal modal = Modal.create(modalId, "Add Map String for " + game.getName()).addActionRow(tags).build();
        event.replyModal(modal).queue();
    }

    @ModalHandler("addMapString")
    public static void getMapStringFromModal(ModalInteractionEvent event, Game game) {
        ModalMapping mapping = event.getValue("mapString");
        if (mapping == null) return;
        String mapStringRaw = mapping.getAsString();
        AddTileListService.addTileListToMap(game, mapStringRaw, event);
    }
}

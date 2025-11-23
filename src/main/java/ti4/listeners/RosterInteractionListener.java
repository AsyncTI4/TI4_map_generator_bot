package ti4.listeners;

import java.util.List;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.CommandHelper;
import ti4.message.MessageHelper;
import ti4.roster.RosterMessageParser;
import ti4.roster.RosterUiBuilder;
import ti4.spring.jda.JdaService;

public class RosterInteractionListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getButton().getId();
        if (id == null) return;

        try {
            switch (id) {
                case "roster_add_self" -> handleAddSelf(event);
                case "roster_remove_self" -> handleRemoveSelf(event);
                case "roster_add_someone" -> openAddSomeoneModal(event);
                case "roster_remove_someone" -> openRemoveSomeoneModal(event);
                case "roster_edit_name" -> openEditNameModal(event);
                default -> {}
            }
        } catch (Exception e) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Error handling roster interaction.");
        }
    }

    private void handleAddSelf(ButtonInteractionEvent event) {
        String content = event.getMessage().getContentRaw();
        List<String> ids = RosterMessageParser.parsePlayerIds(content);
        String myId = event.getUser().getId();
        if (ids.contains(myId)) {
            event.reply("You are already in the roster.").setEphemeral(true).queue();
            return;
        }
        ids.add(myId);
        List<Member> members = RosterMessageParser.resolveMembers(event.getGuild(), ids);
        String gameFun = RosterMessageParser.parseGameFunName(content);
        String newContent = RosterUiBuilder.renderRoster(gameFun, members);
        event.getMessage()
                .editMessage(newContent)
                .setComponents(RosterUiBuilder.initialActionRows())
                .queue();
        event.reply("Added you to the roster.").setEphemeral(true).queue();
    }

    private void handleRemoveSelf(ButtonInteractionEvent event) {
        String content = event.getMessage().getContentRaw();
        List<String> ids = RosterMessageParser.parsePlayerIds(content);
        String myId = event.getUser().getId();
        if (!ids.contains(myId)) {
            event.reply("You are not in the roster.").setEphemeral(true).queue();
            return;
        }
        ids.removeIf(id -> id.equals(myId));
        List<Member> members = RosterMessageParser.resolveMembers(event.getGuild(), ids);
        String gameFun = RosterMessageParser.parseGameFunName(content);
        String newContent = RosterUiBuilder.renderRoster(gameFun, members);
        event.getMessage()
                .editMessage(newContent)
                .setComponents(RosterUiBuilder.initialActionRows())
                .queue();
        event.reply("Removed you from the roster.").setEphemeral(true).queue();
    }

    private void openAddSomeoneModal(ButtonInteractionEvent event) {
        String messageId = event.getMessageId();
        var inputBuilder = TextInput.create("roster_add_someone_input", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("123456789012345678 or @user");
        Modal modal = Modal.create("roster_add_someone~" + messageId, "Add Someone")
                .addComponents(Label.of("Enter User", inputBuilder.build()))
                .build();
        event.replyModal(modal).queue();
    }

    private void openRemoveSomeoneModal(ButtonInteractionEvent event) {
        String messageId = event.getMessageId();
        var removeInputBuilder = TextInput.create("roster_remove_someone_input", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("123456789012345678 or @user");
        Modal modal = Modal.create("roster_remove_someone~" + messageId, "Remove Someone")
                .addComponents(Label.of("Enter User", removeInputBuilder.build()))
                .build();
        event.replyModal(modal).queue();
    }

    private void openEditNameModal(ButtonInteractionEvent event) {
        // allow bothelpers/admins to edit the name. TODO: allow thread creator as well.
        if (!CommandHelper.hasRole(event, JdaService.bothelperRoles)
                && !CommandHelper.hasRole(event, JdaService.adminRoles)) {
            event.reply("Only bothelpers or admins can edit the game name for now.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String messageId = event.getMessageId();
        var nameInputBuilder = TextInput.create("roster_edit_name_input", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("Fun-Name");
        Modal modal = Modal.create("roster_edit_name~" + messageId, "Edit Game Name")
                .addComponents(Label.of("Game Name", nameInputBuilder.build()))
                .build();
        event.replyModal(modal).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (id == null) return;
        try {
            if (id.startsWith("roster_add_someone~")) {
                String messageId = StringUtils.substringAfter(id, "~");
                String input = event.getValue("roster_add_someone_input").getAsString();
                handleAddSomeoneModal(event, messageId, input);
            } else if (id.startsWith("roster_remove_someone~")) {
                String messageId = StringUtils.substringAfter(id, "~");
                String input = event.getValue("roster_remove_someone_input").getAsString();
                handleRemoveSomeoneModal(event, messageId, input);
            } else if (id.startsWith("roster_edit_name~")) {
                String messageId = StringUtils.substringAfter(id, "~");
                String input = event.getValue("roster_edit_name_input").getAsString();
                handleEditNameModal(event, messageId, input);
            }
        } catch (Exception e) {
            event.reply("Error processing modal.").setEphemeral(true).queue();
        }
    }

    private void handleAddSomeoneModal(ModalInteractionEvent event, String messageId, String input) {
        String id = extractIdFromInput(input);
        if (id == null) {
            event.reply("Could not parse that user. Please provide a user ID or mention.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.getChannel()
                .retrieveMessageById(messageId)
                .queue(
                        message -> {
                            String content = message.getContentRaw();
                            List<String> ids = RosterMessageParser.parsePlayerIds(content);
                            if (ids.contains(id)) {
                                event.reply("That user is already in the roster.")
                                        .setEphemeral(true)
                                        .queue();
                                return;
                            }
                            ids.add(id);
                            List<Member> members = RosterMessageParser.resolveMembers(event.getGuild(), ids);
                            String gameFun = RosterMessageParser.parseGameFunName(content);
                            String newContent = RosterUiBuilder.renderRoster(gameFun, members);
                            message.editMessage(newContent)
                                    .setComponents(RosterUiBuilder.initialActionRows())
                                    .queue();
                            event.reply("Added user to roster.")
                                    .setEphemeral(true)
                                    .queue();
                        },
                        failure -> event.reply("Could not find the roster message to edit.")
                                .setEphemeral(true)
                                .queue());
    }

    private void handleRemoveSomeoneModal(ModalInteractionEvent event, String messageId, String input) {
        String id = extractIdFromInput(input);
        if (id == null) {
            event.reply("Could not parse that user. Please provide a user ID or mention.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        event.getChannel()
                .retrieveMessageById(messageId)
                .queue(
                        message -> {
                            String content = message.getContentRaw();
                            List<String> ids = RosterMessageParser.parsePlayerIds(content);
                            if (!ids.contains(id)) {
                                event.reply("That user is not in the roster.")
                                        .setEphemeral(true)
                                        .queue();
                                return;
                            }
                            ids.removeIf(s -> s.equals(id));
                            List<Member> members = RosterMessageParser.resolveMembers(event.getGuild(), ids);
                            String gameFun = RosterMessageParser.parseGameFunName(content);
                            String newContent = RosterUiBuilder.renderRoster(gameFun, members);
                            message.editMessage(newContent)
                                    .setComponents(RosterUiBuilder.initialActionRows())
                                    .queue();
                            event.reply("Removed user from roster.")
                                    .setEphemeral(true)
                                    .queue();
                        },
                        failure -> event.reply("Could not find the roster message to edit.")
                                .setEphemeral(true)
                                .queue());
    }

    private void handleEditNameModal(ModalInteractionEvent event, String messageId, String input) {
        event.getChannel()
                .retrieveMessageById(messageId)
                .queue(
                        message -> {
                            String content = message.getContentRaw();
                            List<String> ids = RosterMessageParser.parsePlayerIds(content);
                            List<Member> members = RosterMessageParser.resolveMembers(event.getGuild(), ids);
                            String newContent = RosterUiBuilder.renderRoster(input.trim(), members);
                            message.editMessage(newContent)
                                    .setComponents(RosterUiBuilder.initialActionRows())
                                    .queue();
                            event.reply("Updated game name.").setEphemeral(true).queue();
                        },
                        failure -> event.reply("Could not find the roster message to edit.")
                                .setEphemeral(true)
                                .queue());
    }

    private String extractIdFromInput(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.matches("^<@!?(\\d+)>$")) {
            return StringUtils.substringBetween(raw, "<@", ">").replace("!", "");
        }
        if (raw.matches("^\\d+$")) return raw;
        return null;
    }
}

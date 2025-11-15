package ti4.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.helpers.StringHelper;
import ti4.message.logging.BotLogger;
import ti4.model.Source.ComponentSource;

@Data
public class RuleModel implements ModelInterface, EmbeddableModel {
    private String id;
    private String title;
    private List<String> description;
    private List<RuleField> fields;
    private ComponentSource source;

    @Data
    public static class RuleField {
        private String title;
        private List<String> text;
        private Boolean inline = false;

        public String getTitle() {
            return StringHelper.replaceWithEmojis(title);
        }

        public String plainDescription() {
            if (text == null) return "";
            String descr = String.join("", text);
            return StringHelper.replaceWithEmojis(descr);
        }

        public boolean inline() {
            return inline != null && inline;
        }
    }

    public boolean isValid() {
        return id != null && title != null && !title.isBlank();
    }

    public List<RuleField> getFields() {
        if (fields == null) return new ArrayList<>();
        return fields;
    }

    public String getAlias() {
        return id;
    }

    public String getTitle() {
        return StringHelper.replaceWithEmojis(title);
    }

    public String getTitleRaw() {
        return StringHelper.stripEmojis(title);
    }

    public String plainDescription() {
        if (description == null) return "";
        String descr = String.join("", description);
        return StringHelper.replaceWithEmojis(descr);
    }

    public MessageEmbed getRepresentationEmbed() {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("**__" + getTitle() + ":__**");

        if (!plainDescription().isBlank()) {
            builder.setDescription(">>> " + plainDescription());
        }

        for (RuleField field : getFields()) {
            String fieldTitle = "**__" + field.getTitle() + ":__**";
            String fieldDescr = ">>> " + field.plainDescription();
            builder.addField(fieldTitle, fieldDescr, field.inline());
        }

        try {
            return builder.build();
        } catch (Exception e) {
            BotLogger.error("Could not build rule embed for " + getTitle(), e);
            return null;
        }
    }

    public String getAutoCompleteName() {
        return getTitleRaw().trim();
    }

    public boolean search(String searchString) {
        String lower = searchString.toLowerCase();
        if (getTitle().toLowerCase().contains(lower)) return true;
        if (plainDescription().toLowerCase().contains(lower)) return true;
        if (getFields().stream().anyMatch(t -> t.getTitle().toLowerCase().contains(lower))) return true;
        return getFields().stream()
                .anyMatch(t -> t.plainDescription().toLowerCase().contains(lower));
    }
}

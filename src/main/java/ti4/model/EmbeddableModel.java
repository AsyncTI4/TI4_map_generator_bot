package ti4.model;

import net.dv8tion.jda.api.entities.MessageEmbed;

public interface EmbeddableModel {
    public MessageEmbed getRepresentationEmbed();
    abstract boolean search(String searchString);
    abstract String getAutoCompleteName();
}

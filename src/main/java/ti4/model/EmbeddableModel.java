package ti4.model;

import net.dv8tion.jda.api.entities.MessageEmbed;

public interface EmbeddableModel {
    public MessageEmbed getRepresentationEmbed();
    public boolean search(String searchString);
    public String getAutoCompleteName();
}

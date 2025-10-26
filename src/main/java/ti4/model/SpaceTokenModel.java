package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import ti4.image.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.model.WormholeModel.Wormhole;
import ti4.service.emoji.MiscEmojis;

@Data
public class SpaceTokenModel implements TokenModelInterface, EmbeddableModel {
    private String id;
    private String imagePath;
    private String tokenPlanetName;
    private Boolean isAnomaly;
    private Boolean isRift;
    private Boolean isNebula;
    private Boolean isFullPlanetToken;
    private List<String> aliasList;
    private List<Wormhole> wormholes;
    private ComponentSource source;

    @Override
    public boolean isValid() {
        return id != null && imagePath != null;
    }

    @Override
    public String getAlias() {
        return getId();
    }

    public UnitHolderType getUnitHolderType() {
        return UnitHolderType.SPACE;
    }

    public Set<String> getAliasList() {
        Set<String> aliases = new HashSet<>();
        if (aliasList != null) aliases.addAll(aliasList);
        aliases.add(imagePath);
        aliases.add(id);
        return aliases;
    }

    @JsonIgnore
    public String getFileName() {
        return imagePath;
    }

    @JsonIgnore
    public String getFilePath() {
        return Mapper.getTokenPath(imagePath);
    }

    public List<Wormhole> getWormholes() {
        if (wormholes == null) return new ArrayList<>();
        return new ArrayList<>(wormholes);
    }

    public boolean isRift() {
        return getIsRift() != null && getIsRift();
    }

    public boolean isNebula() {
        return getIsNebula() != null && getIsNebula();
    }

    public boolean isAnomaly() {
        return (getIsAnomaly() != null && getIsAnomaly()) || isRift() || isNebula();
    }

    public MessageEmbed getRepresentationEmbed() {
        return getRepresentationEmbed(false);
    }

    public MessageEmbed getRepresentationEmbed(boolean includeAliases) {
        EmbedBuilder eb = new EmbedBuilder();

        // TITLE
        eb.setTitle(getID());

        StringBuilder sb = new StringBuilder();
        // if (isAsteroidField()) sb.append(MiscEmojis.Asteroids);
        // if (isSupernova()) sb.append(MiscEmojis.Supernova);
        if (isNebula()) sb.append(MiscEmojis.Nebula);
        if (isRift()) sb.append(MiscEmojis.GravityRift);
        if (getIsFullPlanetToken()) sb.append("\nPlanet: ").append(getTokenPlanetName());
        eb.setDescription(sb.toString());

        // Image
        eb.setThumbnail("https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tokens/"
                + getImagePath() + "?raw=true");

        if (includeAliases) eb.setFooter("Aliases: " + getAliasList());
        return eb.build();
    }

    public boolean search(String searchString) {
        return getId().toLowerCase().contains(searchString)
                || (getAliasList() != null
                        && getAliasList().stream().anyMatch(a -> a.toLowerCase().contains(searchString)));
    }

    public String getAutoCompleteName() {
        return getId();
    }
}

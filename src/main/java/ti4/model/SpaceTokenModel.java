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
    private String spaceOrPlanet;
    private String tokenPlanetName;
    private Boolean isAnomaly;
    private Boolean isRift;
    private Boolean isNebula;
    private Boolean isAsteroids;
    private Boolean isNova;
    private Boolean isScar;
    private Boolean isFullPlanetToken;
    private List<String> aliasList;
    private List<Wormhole> wormholes;
    private ComponentSource source;
    private String placement;
    private Double scale;
    private Boolean isPlanet;
    private String attachmentID;

    @Override
    public boolean isValid() {
        return id != null && imagePath != null;
    }

    @Override
    public String getAlias() {
        return id;
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
        return isRift != null && isRift;
    }

    public boolean isNebula() {
        return isNebula != null && isNebula;
    }

    public boolean isNova() {
        return isNova != null && isNova;
    }

    public boolean isAsteroids() {
        return isAsteroids != null && isAsteroids;
    }

    public boolean isScar() {
        return isScar != null && isScar;
    }

    public boolean isAnomaly() {
        return (isAnomaly != null && isAnomaly) || isRift() || isNebula() || isNova() || isAsteroids() || isScar();
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
        if (isNova()) sb.append(MiscEmojis.Supernova);
        if (isNebula()) sb.append(MiscEmojis.Nebula);
        if (isRift()) sb.append(MiscEmojis.GravityRift);
        if (isAsteroids()) sb.append(MiscEmojis.Asteroids);
        if (isScar()) sb.append("\nEntropic Scar");
        if (isFullPlanetToken) sb.append("\nPlanet: ").append(tokenPlanetName);
        eb.setDescription(sb.toString());

        // Image
        eb.setThumbnail("https://github.com/AsyncTI4/TI4_map_generator_bot/blob/master/src/main/resources/tokens/"
                + imagePath + "?raw=true");

        if (includeAliases) eb.setFooter("Aliases: " + getAliasList());
        return eb.build();
    }

    public boolean search(String searchString) {
        return id.toLowerCase().contains(searchString)
                || (getAliasList() != null
                        && getAliasList().stream().anyMatch(a -> a.toLowerCase().contains(searchString)));
    }

    public String getAutoCompleteName() {
        return id;
    }
}

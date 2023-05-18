package ti4.map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import ti4.MapGenerator;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.*;

public class Faction {

    private String baseFaction;

    private List<String> factionAbilities;
    private List<String> factionTechs;
    private List<String> factionPNs;
    private List<Leader> leaders;

    private String homeSystem;
    private String flagship;

    private int baseCommodities;
    private String startingFleet;
    private List<String> startingTech;

    //Constructors
    public Faction(String baseFaction) {
        this.baseFaction = baseFaction;
        Mapper.getFactionSetup(baseFaction);
    }
}

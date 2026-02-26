package ti4.spring.api.community;

import java.util.List;

public record CommunityGameSample(String id, String name, int round, int vpTarget, List<String> factions) {}

package ti4.spring.service.persistence;

import java.util.List;

public record GameEntitySnapshot(GameEntity game, List<UserEntity> users, List<TitleEntity> titles) {}

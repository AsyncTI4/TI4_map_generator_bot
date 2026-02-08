package ti4.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import ti4.testUtils.BaseTi4Test;

public abstract class ModelTest<T extends ModelInterface> extends BaseTi4Test {

    public abstract Map<String, T> getModels();

    public List<T> getModelList() {
        return getModels().values().stream()
                .sorted(Comparator.comparing(ModelInterface::getAlias))
                .toList();
    }

    public int count() {
        return getModels().size();
    }
}

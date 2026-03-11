package ti4.model;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import ti4.testUtils.BaseTi4Test;

public abstract class ModelTest<T extends ModelInterface> extends BaseTi4Test {

    public String type = "???";
    public Map<String, T> models;

    public abstract void loadData();

    protected ModelTest() {
        loadData();

        int amt = count();
        if (amt == 0) {
            String error = "Did not load any models of type " + type;
            System.err.println(error);
            Assertions.fail(error);
        } else {
            System.out.println("Validating `" + amt + "` models of type " + type);
        }
    }

    public Map<String, T> getModels() {
        if (models == null) return new HashMap<>();
        return models;
    }

    public List<T> getModelList() {
        return getModels().values().stream()
                .sorted(Comparator.comparing(ModelInterface::getAlias))
                .toList();
    }

    public int count() {
        return getModels().size();
    }
}

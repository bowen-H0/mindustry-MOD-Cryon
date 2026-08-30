package example;

import mindustry.type.Item;
import mindustry.world.consumers.ConsumeItemEfficiency;

public class ConsumeItemCharge extends ConsumeItemEfficiency {
    public float minCharge;

    public ConsumeItemCharge(float minCharge) {
        this.minCharge = minCharge;
        filter = item -> item.charge >= this.minCharge &&
                !item.name.equals("cryon-magnesium") &&
                !item.name.equals("cryon-sodium");
    }

    public ConsumeItemCharge() {
        this(0.1f);
    }

    @Override
    public float itemEfficiencyMultiplier(Item item) {
        return item.charge;
    }
}
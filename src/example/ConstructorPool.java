package example;

public class ConstructorPool {
    public static float stored = 0f;

    public static void add(float amount) {
        if (amount > 0f) stored += amount;
    }

    public static float consume(float amount) {
        if (amount <= 0f) return 1f;
        if (stored >= amount) {
            stored -= amount;
            return 1f;
        } else {
            float ratio = stored / amount;
            stored = 0f;
            return ratio;
        }
    }
}
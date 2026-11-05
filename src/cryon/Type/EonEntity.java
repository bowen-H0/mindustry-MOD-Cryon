package cryon.Type;

import mindustry.gen.UnitEntity;

/** Damage reduction at the raw-damage boundary also covers armor-piercing attacks. */
public class EonEntity extends UnitEntity{
    public static int entityId;
    public transient float barrierAngle;
    @Override public void collision(mindustry.gen.Hitboxc other, float x, float y){
        barrierAngle = angleTo(other);
        super.collision(other, x, y);
    }
    @Override public float mass(){ return super.mass() * 4f; }
    @Override public int classId(){ return entityId; }
    @Override public void rawDamage(float amount){
        super.rawDamage(amount > 0f ? amount * 0.1f : amount);
    }
}

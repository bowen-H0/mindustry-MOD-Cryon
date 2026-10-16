package cryon.Story;

import arc.graphics.Color;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.units.AIController;
import mindustry.gen.UnitEntity;
import mindustry.type.UnitType;
import mindustry.world.blocks.environment.Floor;
import mindustry.world.meta.BuildVisibility;

/** Small native assets shared by the actual map and its Java processor script. */
public final class IntrusionContent {
    public static Floor gridFloor;
    public static UnitType virus;
    public static BasicBulletType packet, seeker;

    public static void load(){
        gridFloor = new Floor("intrusion-grid", 0);
        gridFloor.mapColor = Color.valueOf("09282f");
        gridFloor.buildVisibility = BuildVisibility.hidden;
        virus = new UnitType("intrusion-virus"){{
            constructor = UnitEntity::create;
            aiController = () -> new AIController(){
                @Override public void updateUnit(){ unit.vel.setZero(); }
            };
            flying = true;
            speed = 0f;
            drag = 1f;
            health = 15000f;
            armor = 0f;
            hitSize = 38f;
            itemCapacity = 0;
            useUnitCap = false;
            physics = false;
            playerControllable = false;
            logicControllable = false;
            drawCell = false;
            engineSize = 0f;
            rotateSpeed = 0f;
        }};
        packet = new BasicBulletType(1.8f, 45f){{
            width = height = 8f;
            hitSize = 5f;
            lifetime = 700f;
            collidesAir = collidesGround = true;
            collidesTiles = false;
            hittable = reflectable = absorbable = false;
            keepVelocity = false;
            frontColor = Color.valueOf("aa5b56");
            backColor = hitColor = Color.valueOf("6c3035");
            despawnEffect = mindustry.content.Fx.none;
            hitEffect = mindustry.content.Fx.hitSquaresColor;
            shootEffect = smokeEffect = mindustry.content.Fx.none;
        }};
        seeker = new BasicBulletType(Math.min(1.6f, mindustry.content.UnitTypes.gamma.speed * 0.45f), 45f){
            @Override public void updateHoming(mindustry.gen.Bullet bullet){
                if(bullet.time < homingDelay) return;
                cryon.Script.VirusIntrusionScript script = cryon.Script.VirusIntrusionScript.current();
                mindustry.gen.Unit target = script == null ? null : script.controlledGamma();
                if(target == null || target.dead || !target.isAdded()) return;
                float angle = arc.math.Angles.moveToward(bullet.rotation(), bullet.angleTo(target),
                        1.4f * arc.util.Time.delta);
                bullet.vel.setAngle(angle);
            }
        };
        seeker.width = seeker.height = 9f;
        seeker.hitSize = 5f;
        seeker.lifetime = 420f;
        seeker.homingPower = 0.03f;
        seeker.homingDelay = 18f;
        seeker.collidesTiles = false;
        seeker.hittable = seeker.reflectable = seeker.absorbable = false;
        seeker.keepVelocity = false;
        seeker.frontColor = Color.valueOf("aa5b56");
        seeker.backColor = seeker.hitColor = Color.valueOf("6c3035");
        seeker.despawnEffect = seeker.shootEffect = seeker.smokeEffect = mindustry.content.Fx.none;
        seeker.hitEffect = mindustry.content.Fx.hitSquaresColor;

    }
}

package cryon.Content;

import arc.math.*;
import arc.util.Time;
import cryon.CryonJavaMod;
import cryon.Type.*;
import mindustry.ai.types.FlyingAI;
import mindustry.entities.bullet.RailBulletType;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.meta.*;

public final class EonUnit{
    public static final float hullScale = 1.6f;
    public static UnitType eon;
    public static PhaseDamageStatus phaseDamage;

    public static void load(){
        cryon.Features.EonAudio.load();
        phaseDamage = new PhaseDamageStatus();
        EonEntity.entityId = EntityMapping.register("cryon-eon-entity", EonEntity::new);
        arc.Events.on(mindustry.game.EventType.UnitDamageEvent.class, event -> {
            if(event.unit instanceof EonEntity && event.bullet != null)
                ((EonEntity)event.unit).barrierAngle = event.bullet.rotation() + 180f;
        });
        eon = new UnitType("eon"){
            @Override public void load(){
                super.load();
                // Scale drawing only: keep the artist's 640x896 source and cell unchanged.
                region.scale = hullScale;
                cellRegion.scale = hullScale;
                if(outlineRegion != null && arc.Core.atlas.isFound(outlineRegion)) outlineRegion.scale = hullScale;
                if(shadowRegion != null) shadowRegion.scale = hullScale;
            }
            @Override public void drawShadow(Unit unit){
                if(shadowRegion != null) shadowRegion.scale = hullScale;
                super.drawShadow(unit);
            }
            @Override public void drawBody(Unit unit){
                cryon.Fx.EonCombatFx.drive(unit);
                super.drawBody(unit);
                cryon.Fx.EonCombatFx.reactor(unit);
            }
            @Override public void createIcons(mindustry.graphics.MultiPacker packer){
                // Icon composition uses source pixels, while actual mounts use world units.
                float[] positions = new float[weapons.size * 2];
                for(int i = 0; i < weapons.size; i++){
                    Weapon weapon = weapons.get(i);
                    positions[i * 2] = weapon.x;
                    positions[i * 2 + 1] = weapon.y;
                    weapon.x /= hullScale;
                    weapon.y /= hullScale;
                }
                try{
                    super.createIcons(packer);
                }finally{
                    for(int i = 0; i < weapons.size; i++){
                        weapons.get(i).x = positions[i * 2];
                        weapons.get(i).y = positions[i * 2 + 1];
                    }
                }
                for(mindustry.entities.abilities.Ability ability : abilities){
                    if(ability instanceof HighDimensionBarrierAbility)
                        ((HighDimensionBarrierAbility)ability).prepare(packer, name, packer.get(name));
                }
            }
        {
            constructor = EonEntity::new;
            aiController = FlyingAI::new;
            flying = true;
            lowAltitude = true;
            isEnemy = true;
            health = 30000f;
            armor = 45f;
            hitSize = 160f;
            clipSize = 600f;
            outlineColor = arc.graphics.Color.valueOf("302e3b");
            speed = 0.38f;
            accel = 0.05f;
            drag = 0.045f;
            rotateSpeed = 0.85f;
            engineOffset = 174f;
            loopSound = cryon.Features.EonAudio.engine;
            loopSoundVolume = 0.55f;
            engineSize = 0f;
            engineColor = cryon.Fx.EonCombatFx.phase;
            // Advance into the auxiliary battery envelope instead of stopping at main-cannon range.
            range = 300f;
            maxRange = 1120f;
            targetAir = targetGround = true;
            targetFlags = new BlockFlag[]{BlockFlag.core, BlockFlag.reactor, null};
            envEnabled = Env.any;
            abilities.add(new HighDimensionBarrierAbility());
            weapons.add(new Weapon("cryon-eon-main"){{
                mirror = false;
                x = 0f; y = 100f;
                shootY = 13f;
                reload = 600f;
                // Spinal cannon: its long barrel stays aligned with the narrow forward deck.
                rotate = false;
                rotateSpeed = 2f;
                recoil = 8f;
                recoilTime = 65f;
                cooldownTime = 90f;
                shake = 22f;
                shoot.firstShotDelay = 90f;
                chargeSound = cryon.Features.EonAudio.charge;
                shootSound = cryon.Features.EonAudio.cannon;
                soundPitchMin = 0.9f;
                soundPitchMax = 1.0f;
                shootSoundVolume = 1.2f;
                bullet = new EonPhaseBulletType();
            }});
            weapons.add(new Weapon("cryon-eon-rail"){{
                mirror = true;
                x = 26f; y = 20f;
                shootY = 9.5f;
                reload = 36f;
                shoot.shots = 3;
                shoot.shotDelay = 3f;
                rotate = true;
                rotateSpeed = 5f;
                recoil = 3f;
                shake = 2f;
                shootSound = Sounds.shootForeshadow;
                soundPitchMin = 1.05f;
                soundPitchMax = 1.2f;
                shootSoundVolume = 0.45f;
                bullet = new RailBulletType(){
                    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
                        super.hitEntity(b, entity, health);
                        if(entity instanceof Unit) ((Unit)entity).apply(phaseDamage, 240f);
                    }
                    @Override public void init(){
                        // Reuse Aurora's exact visual effects after turret initialization.
                        RailBulletType source = (RailBulletType)CryonJavaMod.aurora.shootType;
                        shootEffect = source.shootEffect;
                        hitEffect = source.hitEffect;
                        pierceEffect = source.pierceEffect;
                        despawnEffect = source.despawnEffect;
                        pointEffect = source.pointEffect;
                        pointEffectSpace = source.pointEffectSpace;
                        super.init();
                    }
                    {
                        damage = 600f;
                        length = 760f;
                        pierceDamageFactor = 1f;
                        hitColor = arc.graphics.Color.white;
                        status = mindustry.content.StatusEffects.none;
                        statusDuration = 240f;
                    }
                };
            }});
            weapons.add(minelayer(true, 43f, -38f));
            weapons.add(minelayer(false, 24f, -82f));
        }};
    }

    private static Weapon minelayer(boolean gravity, float mountX, float mountY){
        return new Weapon("cryon-eon-" + (gravity ? "gravity" : "bomb")){
            {
                this.x = mountX; this.y = mountY;
                mirror = true;
                rotate = true;
                rotateSpeed = 8f;
                alternate = true;
                reload = gravity ? 180f : 150f;
                shoot.shots = 1;
                shoot.shotDelay = 7f;
                recoil = 2f;
                shootY = gravity ? 4f : 4.75f;
                inaccuracy = 14f;
                shootCone = 35f;
                shootSound = Sounds.shootQuad;
                bullet = new EonMineBulletType(gravity);
            }
            @Override public void update(Unit unit, WeaponMount mount){
                if(!unit.isPlayer()){
                    long seed = unit.id * 31L + (mount.weapon.x < 0f ? 7919L : 0L)
                        + (gravity ? 433L : 811L) + (long)(Time.time / 60f) * 104729L;
                    Rand random = new Rand(seed);
                    Teamc target = mindustry.entities.Units.closestTarget(unit.team, unit.x, unit.y, 620f);
                    float angle, distance;
                    if(target != null){
                        angle = unit.angleTo(target) + random.range(gravity ? 12f : 20f);
                        distance = Mathf.clamp(unit.dst(target) + random.range(55f), 100f, 500f);
                    }else{
                        angle = random.random(360f);
                        distance = random.random(140f, 420f);
                    }
                    mount.aimX = unit.x + Angles.trnsx(angle, distance);
                    mount.aimY = unit.y + Angles.trnsy(angle, distance);
                    mount.shoot = mount.rotate = true;
                }
                super.update(unit, mount);
            }
        };
    }
}

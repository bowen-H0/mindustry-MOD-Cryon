package example;

import arc.Core;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.entities.part.DrawPart;
import mindustry.gen.Sounds;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.world.meta.BlockFlag;
import arc.graphics.GL20;

public class OblivionUnit {
    //This will be left here for now.
    public static UnitType oblivion;

    static final Color plasma1 = Color.valueOf("ffd06b");
    static final Color plasma2 = Color.valueOf("ff361b");

    public static void load() {


        oblivion = new UnitType("oblivion") {

            {
                constructor = UnitEntity::create;
                parts.add(new DrawPart() {
                    TextureRegion[] regions = null;

                    void init() {
                        if (regions != null) return;
                        regions = new TextureRegion[4];
                        for (int i = 0; i < 4; i++) {
                            regions[i] = Core.atlas.find("impact-reactor-plasma-" + i);
                        }
                    }

                    @Override
                    public void draw(PartParams params) {
                        init();

                        Draw.flush();
                        Core.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

                        for (int i = 0; i < regions.length; i++) {
                            TextureRegion reg = regions[i];
                            float r = reg.width * reg.scl() - 3f
                                    + Mathf.absin(Time.time, 2f + i * 1f, 5f - i * 0.5f);
                            Draw.color(plasma1, plasma2, (float) i / regions.length);
                            Draw.alpha(0.3f + Mathf.absin(Time.time, 2f + i * 2f, 0.3f + i * 0.05f));
                            Draw.rect(reg, params.x, params.y, r, r, Time.time * (12f + i * 6f));
                        }

                        Draw.flush();
                        Core.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                        Draw.color();
                    }
                });
                targetFlags = new BlockFlag[]{BlockFlag.core, BlockFlag.reactor, null};

                health = 50000f;
                armor = 25f;
                hitSize = 72f;
                speed = 1.2f;
                rotateSpeed = 2.5f;
                flying = true;
                lowAltitude = true;
                drag = 0.05f;
                accel = 0.06f;
                range = 20f;
                targetAir = true;
                targetGround = true;
                isEnemy = true;
                engineOffset = 38f;
                engineSize = 7.3f;
                // missile-air x8
                float[] airAngles = {0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f};
                float airDist = 18f;
                for (float a : airAngles) {
                    weapons.add(new Weapon("cryon-java-dependency-oblivion-missile-air") {{
                        x = Mathf.cosDeg(a) * airDist;
                        y = Mathf.sinDeg(a) * airDist;
                        mirror = false;
                        reload = 60f;
                        top = true;
                        shake = 4f;
                        rotateSpeed = 2f;
                        rotate = true;
                        shootSound = Sounds.shootMissile;
                        bullet = new MissileBulletType(4f, 35f) {{
                            homingPower = 0.15f;
                            homingRange = 400f;
                            lifetime = 180f;
                            width = 6f; height = 10f;
                            trailColor = Color.valueOf("88ccff");
                            splashDamage = 10f;
                            splashDamageRadius = 30f;
                            collidesAir = true;
                            collidesGround = false;
                        }};
                    }});
                }

                // missile-heavy x4
                float[][] heavyPos = {{-20f, -20f}, {20f, -20f}, {-20f, 20f}, {20f, 20f}};
                for (float[] pos : heavyPos) {
                    weapons.add(new Weapon("cryon-java-dependency-oblivion-missile-heavy") {{
                        x = pos[0]; y = pos[1];
                        mirror = false;
                        reload = 150f;
                        top = true;
                        rotate = true;
                        shake = 4f;
                        rotateSpeed = 2f;
                        alternate = true;
                        shootSound = Sounds.shootMissile;
                        bullet = new MissileBulletType(3.5f, 180f) {{
                            homingPower = 0.08f;
                            homingRange = 600f;
                            lifetime = 180f;
                            width = 10f; height = 16f;
                            trailColor = Color.valueOf("ff8844");
                            splashDamage = 220f;
                            splashDamageRadius = 80f;
                            collidesAir = true;
                            collidesGround = true;
                        }};
                    }});
                }
                //todo This capability will be removed in the future.
                abilities.add(new OblivionBossAbility() {{
                    absorbMaxCharge = 1000f;
                    absorbCapacity = 1800f;
                    absorbHealPerSecond = 100f;
                    reflectRadius = 90f;
                    reflectMaxDamage = 10000f;
                    phaseDuration = 1200f;
                    shieldCooldown = 600f;
                    phantomCount = 8;
                    phantomTypeName = "flare";
                }});
                //It cannot pause the attack.
                //todo This will be changed to "weapons".
                abilities.add(new NukeMissileAbility() {{
                    cooldown = 300f;
                    range = 500f;
                    bulletType = new MissileBulletType(5f, 800f) {{
                        lifetime = 80f;
                        width = 42f; height = 60f;
                        trailColor = Color.valueOf("ff4400");
                        splashDamage = 1200f;
                        splashDamageRadius = 200f;
                        homingPower = 0f;
                        homingRange = 0f;
                        hitEffect = Fx.impactReactorExplosion;
                        despawnEffect = Fx.impactReactorExplosion;
                        collidesAir = false;
                        collidesGround = true;
                    }};
                }});
            }
        };
    }
}
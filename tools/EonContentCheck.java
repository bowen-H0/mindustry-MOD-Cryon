import arc.*;
import arc.files.Fi;
import arc.mock.*;
import arc.util.*;
import mindustry.*;
import mindustry.content.*;
import mindustry.entities.bullet.*;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.net.Net;
import mindustry.type.*;

/** Actual packaged-content and combat checks against the installed v157 runtime. */
public class EonContentCheck{
    static void require(boolean ok, String message){ if(!ok) throw new AssertionError(message); }
    static void close(float actual, float expected, String message){ require(Math.abs(actual - expected)<0.1f,message+": "+actual+" != "+expected); }
    public static void main(String[] args)throws Exception{
        Core.app=new MockApplication(); Core.files=new MockFiles(); Core.graphics=new MockGraphics(); Core.audio=new MockAudio();
        Core.settings=new Settings(); Core.settings.setDataDirectory(new Fi("/tmp/cryon-eon-check"));
        Core.bundle=I18NBundle.createEmptyBundle();
        new Fi("build/libs/mindustry-MOD-JavaDesktop.jar").copyTo(Core.settings.getDataDirectory().child("mods/cryon.jar"));
        Vars.headless=true; Vars.loadLocales=false; Vars.net=new Net(null); Vars.platform=new mindustry.core.Platform(){};
        mindustry.core.Version.init(); Vars.init(); Vars.content.createBaseContent(); Vars.content.createModContent(); Vars.content.init();
        for(mindustry.mod.Mods.LoadedMod mod:Vars.mods.list()) require(!mod.hasContentErrors(),"Content errors: "+mod.erroredContent);
        Vars.world.loadGenerator(180,180,tiles->{ for(int x=0;x<180;x++)for(int y=0;y<180;y++)tiles.set(x,y,new mindustry.world.Tile(x,y,Blocks.sand,Blocks.air,Blocks.air)); });
        Vars.state.set(mindustry.core.GameState.State.playing);
        Time.delta=1f;
        UnitType type=Vars.content.unit("cryon-eon");
        require(type!=null,"Missing Eon");
        require(type.weapons.size==7,"Expected 1 main + 2 rails + 4 minelayers, got "+type.weapons.size);
        float hullScale=type.getClass().getEnclosingClass().getField("hullScale").getFloat(null);
        StringBuilder actualLayout=new StringBuilder("{\"hullScale\":"+hullScale+",\"mounts\":[");
        for(int i=0;i<type.weapons.size;i++){
            Weapon w=type.weapons.get(i);
            String kind=w.name.substring("cryon-eon-".length());
            if(kind.equals("gravity")||kind.equals("bomb"))
                require(Math.abs(w.x)>12f,"Actual minelayer remained near center: "+w.name+" x="+w.x);
            if(i>0)actualLayout.append(',');
            actualLayout.append("{\"kind\":\"").append(kind).append("\",\"x\":").append(w.x)
                .append(",\"y\":").append(w.y).append(",\"recoil\":").append(w.recoil)
                .append(",\"rotate\":").append(w.rotate).append('}');
        }
        new Fi("/tmp/cryon-eon-runtime-layout.json").writeString(actualLayout.append("]}").toString());
        Unit boss=type.create(Team.sharded); boss.set(500,500); boss.add();
        boss.armor=0; boss.healthMultiplier=1; boss.health=10000;
        boss.rawDamage(1000); close(boss.health,9900,"90% raw damage reduction");
        boss.damagePierce(1000); close(boss.health,9800,"90% piercing damage reduction");
        require(EntityMapping.map(boss.classId()).get().getClass()==boss.getClass(),"Entity mapping/save constructor");
        StatusEffect phase=Vars.content.statusEffect("cryon-phase-damage");
        Unit target=UnitTypes.reign.create(Team.crux); target.set(700,500); target.maxHealth=target.health=100000; target.armor=0; target.healthMultiplier=1; target.add();
        target.apply(phase,480); target.update(); target.armorOverride=0; target.healthMultiplier=1;
        BasicBulletType shot=new BasicBulletType(1,100); shot.init();
        Bullet first=shot.create(boss,Team.sharded,target.x,target.y,0);
        float before=target.health;
        shot.hitEntity(first,target,target.health);
        close(before-target.health,500,"Fivefold first eligible hit");
        before=target.health;
        shot.hitEntity(shot.create(boss,Team.sharded,target.x,target.y,0),target,target.health);
        close(before-target.health,100,"Mark consumed exactly once");
        target.apply(phase,480);
        before=target.health;
        shot.hitEntity(shot.create(boss,Team.sharded,target.x,target.y,0),target,target.health);
        close(before-target.health,100,"Extension does not recharge mark");
        before=target.health;
        target.heal(500); target.update();
        close(target.health,before,"Healing suppression");
        target.unapply(phase); target.apply(phase,480); target.update(); target.armorOverride=0; target.healthMultiplier=1;
        before=target.health;
        shot.hitEntity(shot.create(boss,Team.sharded,target.x,target.y,0),target,target.health);
        close(before-target.health,500,"Fresh application rearms mark");
        // Actual spatial index is used by all custom area weapons.
        target.team.data().tree().insert(target); Vars.state.teams.updateTeamStats();
        target.unapply(phase); target.armorOverride=0; target.healthMultiplier=1;
        BulletType cannon=type.weapons.first().bullet;
        Bullet shell=cannon.create(boss,Team.sharded,target.x,target.y,0);
        before=target.health;
        cannon.hit(shell,target.x,target.y);
        close(before-target.health,7000,"Main cannon first explosion");
        int count=Groups.bullet.size(); cannon.hit(shell,target.x,target.y);
        require(Groups.bullet.size()==count,"Impact/despawn must not duplicate collapse");
        Bullet collapse=Groups.bullet.find(b->b.type.getClass().getName().contains("EonPhaseBulletType$"));
        require(collapse!=null,"Missing collapse entity");
        // Consume the initial mark, then independently check the final base damage.
        phase.getClass().getMethod("consume",Unit.class).invoke(phase,target);
        target.health=100000; target.armorOverride=0; target.healthMultiplier=1;
        collapse.type.despawned(collapse);
        close(100000-target.health,16000,"Main cannon final explosion");
        RailBulletType rail=(RailBulletType)type.weapons.get(1).bullet;
        mindustry.world.blocks.defense.turrets.Turret aurora=(mindustry.world.blocks.defense.turrets.Turret)Vars.content.block("cryon-aurora");
        require(rail.pointEffect==((RailBulletType)aurora.getClass().getField("shootType").get(aurora)).pointEffect,"Rail must reuse exact Aurora barrage effects");
        // Expiry uses BulletType's four-argument hit dispatch, unlike direct collisions.
        target.unapply(phase); target.health=100000; target.armorOverride=0; target.healthMultiplier=1;
        Bullet expired=cannon.create(boss,Team.sharded,target.x,target.y,0);
        cannon.despawned(expired);
        close(100000-target.health,7000,"Main cannon also detonates at maximum range");
        BulletType gravity=null,bomb=null;
        for(Weapon weapon:type.weapons){
            if(weapon.name.endsWith("gravity")) gravity=weapon.bullet;
            if(weapon.name.endsWith("bomb")) bomb=weapon.bullet;
        }
        require(gravity!=null && bomb!=null,"Missing mine variants");
        target.unapply(phase); target.health=100000; target.armorOverride=0; target.healthMultiplier=1;
        Bullet trap=gravity.create(boss,Team.sharded,target.x,target.y,0);
        trap.time=45; gravity.update(trap);
        require(trap.data!=null && trap.data.getClass().getSimpleName().equals("GravityWellState") && trap.isAdded(),"Gravity trap must remain after triggering");
        close(trap.lifetime-trap.time,90,"1.5-second persistent gravity well");
        target.set(trap.x+20,trap.y); target.vel.setZero(); gravity.update(trap);
        require(target.vel.x<0,"Gravity trap pulls toward center");
        require(target.getDuration(StatusEffects.unmoving)==0,"Gravity trap must not apply unmoving");
        target.unapply(phase); target.update();
        float reload=target.reloadMultiplier, damage=target.damageMultiplier, speed=target.speedMultiplier;
        target.set(trap.x+180f,trap.y); target.vel.set(10f,0f);
        gravity.update(trap);
        require(target.dst(trap)<=140f*0.38f+0.01f,"Captured target escaped the well");
        require(target.vel.x<=0f,"Captured target retains outward escape velocity");
        require(target.canShoot(),"Imprisonment must preserve weapon use");
        close(target.reloadMultiplier,reload,"Confinement must preserve reload");
        close(target.damageMultiplier,damage,"Confinement must preserve damage");
        close(target.speedMultiplier,speed,"Confinement must not apply a movement status");
        target.unapply(phase); target.health=100000; target.armorOverride=0; target.healthMultiplier=1;
        Bullet mine=bomb.create(boss,Team.sharded,target.x,target.y,0);
        mine.time=45; bomb.update(mine);
        close(100000-target.health,6500,"High-dimensional bomb damage");
        require(!mine.isAdded(),"Bomb removed after one detonation");
        Bullet idle=bomb.create(boss,Team.sharded,100,100,0);
        for(int i=0;i<150;i++) idle.update();
        require(idle.vel.len()<0.5f,"Deployed mines slow to a stop");
        // Run real AI, delayed burst scheduling, bullet physics and collisions together.
        Groups.clear();
        Team.sharded.data().tree().clear(); Team.crux.data().tree().clear();
        Vars.state.rules.waves=Vars.state.rules.canGameOver=false;
        Vars.state.rules.unitCap=100;
        Unit attacker=type.create(Team.crux); attacker.set(400,600); attacker.rotation=0; attacker.add();
        attacker.controller(new mindustry.ai.types.FlyingAI());
        Unit bait=UnitTypes.eclipse.create(Team.sharded); bait.set(1000,600);
        bait.health=bait.maxHealth=100000000f; bait.add();
        bait.controller(new mindustry.entities.units.AIController(){ @Override public void updateUnit(){} });
        Vars.state.teams.updateTeamStats();
        Time.setDeltaProvider(()->1f);
        for(int tick=0;tick<1200;tick++){
            Time.update(); Vars.state.teams.updateTeamStats();
            Groups.unit.updatePhysics(); Groups.bullet.updatePhysics();
            Groups.unit.update(); Groups.bullet.update(); Groups.bullet.collide();
        }
        StringBuilder fireReport=new StringBuilder();
        for(mindustry.entities.units.WeaponMount mount:attacker.mounts){
            require(mount.totalShots>0,"AI weapon never fired: "+mount.weapon.name+" at distance "+attacker.dst(bait));
            if(mount.weapon.name.endsWith("rail")) require(mount.totalShots>=40,"Rail battery did not sustain fire");
            fireReport.append(mount.weapon.name).append('=').append(mount.totalShots).append(' ');
        }
        require(bait.health<99900000f,"Twenty-second sustained attack lacks combined firepower: "+bait.health);
        System.out.println("PASS: actual 20-second AI battle: "+fireReport+" damage="+(bait.maxHealth-bait.health));
        System.out.println("PASS: Eon loads; 7 mounts; 90% reduction including piercing; entity mapping; fivefold one-shot mark and refresh rules; healing suppression; 7000 -> collapse -> 16000; Aurora effects; max-range detonation; persistent gravity pull and imprisonment; high-dimensional bomb and drifting mine deceleration.");
        System.exit(0);
    }
}

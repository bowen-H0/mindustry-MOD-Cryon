import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.entities.Puddles;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import java.util.HashSet;

/** Checks distinct real bullet classes and native damage, homing, liquid and fragmentation behavior. */
public class PorphyraTurretCheck{
    static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    static BulletType ammo(String name,String item){return ((ItemTurret)Vars.content.block("cryon-porphyra-"+name)).ammoTypes.get(Vars.content.item("cryon-"+item));}
    static Unit target(float x,float y,boolean air,Team team){
        Unit unit=(air?UnitTypes.flare:UnitTypes.dagger).create(team);unit.set(x,y);unit.health=unit.maxHealth=10000;unit.add();
        team.data().tree().insert(unit);Vars.state.teams.updateTeamStats();return unit;
    }
    public static void main(String[] args)throws Exception{
        PorphyraIndustryCheck.main(args);
        Vars.logic.reset();Vars.state.rules=new Rules();Vars.state.rules.unitCap=100;Time.delta=1f;
        Vars.world.loadGenerator(100,100,tiles->{for(int x=0;x<100;x++)for(int y=0;y<100;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        BulletType arc=ammo("excite","rhenium"), pulse=ammo("pulse","technetium"), missile=ammo("overload","neptunium"), shell=ammo("collapse","oganesson");
        BulletType plasma=((LiquidTurret)Vars.content.block("cryon-porphyra-dissociate")).ammoTypes.get(Vars.content.liquid("cryon-porphyra-plasma"));
        check(((ItemTurret)Vars.content.block("cryon-porphyra-excite")).shoot.shots==3
                && ((ItemTurret)Vars.content.block("cryon-porphyra-collapse")).shoot.shots==3
                && ((ItemTurret)Vars.content.block("cryon-porphyra-pulse")).shoot.shots==2,
                "Turret spread/alternate patterns did not parse correctly");
        check(arc instanceof LightningBulletType && !arc.collidesAir,"Excite is not ground lightning");
        check(pulse instanceof LaserBulletType,"Pulse is not an instant laser");
        check(plasma instanceof LiquidBulletType && ((LiquidBulletType)plasma).liquid==Vars.content.liquid("cryon-porphyra-plasma"),"Dissociate is not actual plasma liquid");
        check(missile instanceof MissileBulletType && missile.homingPower>0 && missile.lightning>0,"Overload lacks homing/electric warhead");
        check(shell instanceof ArtilleryBulletType && shell.fragBullet instanceof LightningBulletType,"Collapse lacks artillery/electric fragments");
        var glow=(LaserTurret)Vars.content.block("cryon-porphyra-glow");
        check(glow.shootType instanceof ContinuousLaserBulletType,"Glow is not continuous laser");
        var critical=Vars.content.block("cryon-porphyra-critical");
        check(critical instanceof PowerTurret && !(critical instanceof LaserTurret)
                && ((PowerTurret)critical).shootType instanceof LaserBulletType,"Critical must use a charged instant-beam turret");
        HashSet<Class<?>> types=new HashSet<>();
        for(String[] entry:new String[][]{{"etch","rhenium"},{"excite","rhenium"},{"pulse","technetium"},{"deflect","promethium"},{"detune","neptunium"},{"overload","neptunium"},{"collapse","oganesson"}})types.add(ammo(entry[0],entry[1]).getClass());
        types.add(plasma.getClass());types.add(glow.shootType.getClass());
        check(types.size()>=8,"Turrets still use mostly identical bullet classes");
        Vars.world.tile(22,20).setBlock(Blocks.copperWall,Team.crux);
        Building wall=Vars.world.tile(22,20).build;float before=wall.health;
        pulse.create((Entityc)null,Team.sharded,12*8,20*8,0);
        check(wall.health<before,"Pulse laser did not apply instant native beam damage");
        var liquidShot=plasma.create((Entityc)null,Team.sharded,30*8,30*8,0);
        plasma.hit(liquidShot,30*8,30*8);
        check(Puddles.get(Vars.world.tile(30,30))!=null && Puddles.get(Vars.world.tile(30,30)).liquid==Vars.content.liquid("cryon-porphyra-plasma"),"Plasma impact did not deposit real liquid");
        Unit ground=target(170,420,false,Team.crux);target(170,380,false,Team.sharded);
        var guided=missile.create((Entityc)null,Team.sharded,100,400,0);guided.time=missile.homingDelay+2;
        float angle=guided.vel.angle();missile.update(guided);
        check(guided.vel.angle()>angle,"Homing missile did not steer toward the enemy");
        ground.set(220,600);Team.crux.data().tree().remove(ground);Team.crux.data().tree().insert(ground);
        Unit electricTarget=target(130,600,false,Team.crux);float hp=electricTarget.health;
        Mathf.rand.setSeed(18);
        for(int i=0;i<8;i++)arc.create((Entityc)null,Team.sharded,100,600,0);
        // Native lightning creates damage bullets; let their physics collide with nearby units.
        for(int i=0;i<3;i++){Groups.update();}
        check(electricTarget.health<hp,"Lightning did not damage a ground target");
        var artillery=shell.create((Entityc)null,Team.sharded,400,400,0);shell.hit(artillery,400,400);
        check(Groups.bullet.copy().contains(b->b.type==shell.fragBullet),"Artillery burst did not emit electric fragments");
        System.out.println("PASS: eight distinct turret bullet classes; real pulse-laser damage, plasma puddles, missile steering, lightning damage and electric artillery fragments");
    }
}

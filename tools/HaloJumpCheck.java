import arc.*;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.util.*;
import mindustry.*;
import mindustry.ai.UnitCommand;
import mindustry.ai.types.CommandAI;
import mindustry.content.*;
import mindustry.entities.abilities.Ability;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.UnitType;
import mindustry.world.Tile;

/** Actual Halo AI, continuous weapon and real fold begin/finish/update lifecycle. */
public final class HaloJumpCheck extends TransitCheck{
    static Class<?> jumpType;
    static Object jump;
    static void step() throws Exception{
        Time.delta = 1f; Time.update();
        jumpType.getMethod("update", Unit.class).invoke(jump, owner);
        owner.controller().updateUnit();
        for(var mount : owner.mounts) mount.weapon.update(owner, mount);
        for(Bullet b : Groups.bullet.copy()) b.update();
        jumpType.getMethod("afterUpdate", Unit.class).invoke(jump, owner);
        owner.move(owner.vel.x, owner.vel.y); owner.vel.scl(1f-owner.type.drag); index();
    }
    static boolean active() throws Exception{ return (boolean)jumpType.getMethod("active").invoke(jump); }
    public static void main(String[] args){
        try{ run(); checkJump(); System.exit(0); }catch(Throwable error){ error.printStackTrace(); System.exit(1); }
    }
    static void checkJump() throws Exception{
        var loader = Vars.mods.locateMod("cryon").main.getClass().getClassLoader();
        jumpType = loader.loadClass("cryon.Type.SpaceJumpAbility");
        UnitCommand fold = (UnitCommand)loader.loadClass("cryon.Features.SpaceJump").getField("jumpCommand").get(null);
        Vars.logic.reset(); Vars.state.rules.unitCap=100; Vars.state.rules.canGameOver=false;
        Vars.world.loadGenerator(240,150, tiles -> {
            for(int x=0;x<240;x++) for(int y=0;y<150;y++) tiles.set(x,y,new Tile(x,y,Blocks.stone,Blocks.air,Blocks.air));
            tiles.get(10,10).setBlock(Blocks.coreShard,Team.sharded);
        });
        Vars.state.set(mindustry.core.GameState.State.playing); Time.setDeltaProvider(()->1f);
        UnitType halo=Vars.content.unit("cryon-halo");
        halo.commands=halo.commands.copy(); halo.commands.addUnique(fold);
        owner=halo.spawn(Team.sharded,310,396);
        Unit oldTarget=spawn(UnitTypes.reign,Team.crux,310,300);
        Unit newTarget=spawn(UnitTypes.reign,Team.crux,850,600);
        jump=jumpType.getConstructor().newInstance(); owner.abilities=new Ability[]{(Ability)jump}; index();
        for(int i=0;i<120;i++)step();
        Bullet oldBeam=owner.mounts[0].bullet;
        check(oldBeam!=null && oldTarget.health<100000f,"Halo did not fire before fold");
        CommandAI ai=(CommandAI)owner.controller(); ai.command(fold); ai.commandPosition(new Vec2(850,696));
        jumpType.getMethod("update",Unit.class).invoke(jump,owner);
        check(active(),"RTS fold did not begin");
        check(!oldBeam.isAdded() && owner.mounts[0].bullet==null,"fold charge retained the old continuous beam");
        float oldHp=oldTarget.health;
        int ticks=0;
        while(active() && ticks++<1000){
            step();
            if(active()) check(!owner.isShooting && owner.mounts[0].bullet==null,"hover AI fought the fold charge by firing");
        }
        check(!active() && owner.within(850,696,2f),"fold did not arrive at destination");
        check(ai.currentCommand()==UnitCommand.moveCommand,"Halo remained in the completed fold command");
        for(int i=0;i<180;i++)step();
        check(owner.mounts[0].target==newTarget && newTarget.health<100000f,"Halo did not reacquire and burn a local target after folding");
        check(oldTarget.health==oldHp,"old-position target took stale beam damage after fold");
        check(owner.within(850,696,16f),"post-fold Halo returned to the old hover station");

        // The server/client finish callback must also invalidate stale mounts immediately.
        Bullet second=owner.mounts[0].bullet;
        owner.maxHealth=16000f;
        Unit thirdTarget=spawn(UnitTypes.reign,Team.crux,1300,704);
        jumpType.getMethod("finish",Unit.class,float.class,float.class,float.class).invoke(jump,owner,1300f,800f,0f);
        check(second==null || !second.isAdded(),"direct fold finish retained the old beam");
        check(owner.mounts[0].target==null,"direct fold finish retained an old mount target");
        for(int i=0;i<30;i++){
            step();
            check(owner.within(1300,800,.01f) && owner.mounts[0].bullet==null,"reconstruction resumed hovering/firing before completion");
        }
        for(int i=0;i<130;i++)step();
        check(thirdTarget.health<100000f,"reconstruction never restored local combat AI");
        ai.command(fold); ai.commandPosition(new Vec2(1600,850)); ai.commandQueue.add(new Vec2(1710,900));
        jumpType.getMethod("finish",Unit.class,float.class,float.class,float.class).invoke(jump,owner,1600f,850f,0f);
        check(ai.currentCommand()==UnitCommand.moveCommand && ai.commandQueue.size==1
            && ((Vec2)ai.commandQueue.first()).dst(1710,900)<.01f,"arrival discarded the queued movement order");

        // Offset checks use the real rear-route override, not Weapon.shootY (which Xi He discards).
        UnitType transit=Vars.content.unit("cryon-transit");
        check(transit.engineOffset==7f,"T2 engine was not moved forward by three world units");
        UnitType arsenal=Vars.content.unit("cryon-aphelion");
        Unit ship=arsenal.spawn(Team.sharded,600,800);
        for(float heading:new float[]{0f,90f,180f,270f}){
            ship.rotation=heading;
            Bullet b=arsenal.weapons.first().bullet.create(ship,ship.team,ship.x,ship.y,heading,-1,1,1,null,null,950,800);
            float radius=ship.hitSize*.8f+14f+(b.id%3)*6f;
            Vec2 expected=new Vec2().trns(heading,6f-radius).add(ship);
            check(b.within(expected,.001f),"T4 missile spawn was not moved forward six units at heading "+heading);
            Vec2 center=(Vec2)field(b.data,"center");
            check(center.dst(new Vec2().trns(heading,6f).add(ship))<.001f,"T4 launch route did not share its shifted anchor");
            check(((Vec2)field(b.data,"target")).dst(950,800)<.001f,"T4 launch shift changed the intended target");
            b.remove();
        }
        System.out.println("HALO_JUMP_PASS: charge suppresses old beam/hover, actual RTS fold, move-mode restore, immediate local reacquisition, no remote damage/return, finish callback cleanup, T4 forward spawn on four headings, T2 forward engine offset");
    }
}

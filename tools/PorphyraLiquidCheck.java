import arc.math.geom.Geometry;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.*;
import mindustry.world.*;
import mindustry.world.blocks.production.Pump;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.entities.bullet.LiquidBulletType;
import java.util.ArrayDeque;

/** Verifies real saved basin maps, safe starter areas/routes, powered pumps and liquid ammunition. */
public class PorphyraLiquidCheck{
    static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        PorphyraTurretCheck.main(args);
        var planet=Vars.content.planet("cryon-porphyra");
        for(String name:new String[]{"gamma-brine","gamma-brine-deep","crystal-slurry","crystal-slurry-deep"}){
            var floor=Vars.content.block("cryon-porphyra-"+name).asFloor();
            check(floor.cacheLayer==mindustry.graphics.CacheLayer.water,
                    "Liquid floor still uses lava/slag rendering: "+name);
            check(floor.variants==2,"Missing varied liquid surface tiles: "+name);
        }

        String[] names={"landing","glassfield","recrystallization","lightband","pale-ridge","phase-scar","neptunium-basin","ashen-arc","heavy-element","final-core"};
        for(int stage=0;stage<names.length;stage++){
            var preset=Vars.content.sector("cryon-porphyra-"+names[stage]);
            Vars.logic.reset();Vars.state.rules.sector=preset.sector;Vars.state.rules.planet=planet;
            Vars.world.loadGenerator(192,192,tiles->preset.generator.generate(tiles,new WorldParams()));
            int brine=0,slurry=0,deep=0;Tile enemy=null;
            for(var tile:Vars.world.tiles){
                if(tile.floor().liquidDrop==Vars.content.liquid("cryon-gamma-brine"))brine++;
                if(tile.floor().liquidDrop==Vars.content.liquid("cryon-crystal-slurry"))slurry++;
                if(tile.floor().isLiquid && tile.floor().drownTime>0)deep++;
                if(arc.math.Mathf.dst(tile.x,tile.y,96,38)<=18)check(!tile.floor().isLiquid,"Flooded landing area on stage "+stage);
                if(tile.overlay().itemDrop!=null)check(!tile.floor().isLiquid,"Ore generated underwater on stage "+stage);
                if(tile.isCenter() && tile.team()==Team.get(169) && tile.block() instanceof mindustry.world.blocks.storage.CoreBlock)enemy=tile;
            }
            check(brine>100&&slurry>100&&deep>20,"Missing shallow/deep basins on stage "+stage);
            check(enemy!=null && dryPath(enemy),"No dry assault route on stage "+stage);
        }
        Vars.logic.reset();Vars.state.rules=new Rules();
        Vars.world.loadGenerator(64,64,tiles->{for(int x=0;x<64;x++)for(int y=0;y<64;y++)tiles.set(x,y,new Tile(x,y,Blocks.sand,Blocks.air,Blocks.air));});
        var pump=(Pump)Vars.content.block("cryon-porphyra-shore-pump");
        check(pump.techNode!=null,"Pump is not researchable");
        var turret=(LiquidTurret)Vars.content.block("cryon-porphyra-dissociate");
        int x=10;
        for(String name:new String[]{"gamma-brine","crystal-slurry"}){
            var floor=Vars.content.block("cryon-porphyra-"+name).asFloor();var liquid=Vars.content.liquid("cryon-"+name);
            check(floor.liquidDrop==liquid && floor.isLiquid && floor.drownTime<=0,"Shallow liquid definition incorrect");
            check(Vars.content.block("cryon-porphyra-"+name+"-deep").asFloor().drownTime>0,"Deep pool does not drown");
            check(turret.ammoTypes.get(liquid) instanceof LiquidBulletType,"Environment liquid is not usable turret ammunition");
            for(int dx=0;dx<2;dx++)for(int dy=0;dy<2;dy++)Vars.world.tile(x+dx,20+dy).setFloor(floor);
            check(pump.canPlaceOn(Vars.world.tile(x,20),Team.sharded,0),"Pump cannot be placed on "+name);
            Vars.world.tile(x,20).setBlock(pump,Team.sharded);var build=(Pump.PumpBuild)Vars.world.tile(x,20).build;
            build.power.status=0;ticks(build,30);check(build.liquids.get(liquid)==0,"Pump ran without power");
            build.power.status=1;ticks(build,30);check(build.liquids.get(liquid)>0,"Powered pump cannot extract "+name);
            x+=12;
        }
        check(!pump.canPlaceOn(Vars.world.tile(50,40),Team.sharded,0),"Pump incorrectly accepts dry terrain");
        System.out.println("PASS: all ten saved maps have brine/slurry shallow and deep basins, dry land routes and starter ore areas; both liquids pump with power and work as ammunition");
    }
    static void ticks(Pump.PumpBuild build,int count){for(int i=0;i<count;i++){Time.time++;Time.delta=1;build.updateConsumption();build.updateTile();}}
    static boolean dryPath(Tile enemy){
        int width=Vars.world.width(),height=Vars.world.height();boolean[] visited=new boolean[width*height];
        ArrayDeque<Tile> queue=new ArrayDeque<>();queue.add(Vars.world.tile(96,34));
        while(!queue.isEmpty()){
            Tile tile=queue.remove();
            if(arc.math.Mathf.dst(tile.x,tile.y,enemy.x,enemy.y)<8)return true;
            for(var direction:Geometry.d4){
                Tile next=Vars.world.tile(tile.x+direction.x,tile.y+direction.y);
                if(next==null || next.block().solid || next.floor().isLiquid)continue;
                int index=next.x+next.y*width;if(visited[index])continue;visited[index]=true;queue.add(next);
            }
        }
        return false;
    }
}

package cryon.Type;

import arc.graphics.Color;
import arc.util.Time;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Features.DisasterFactions;
import mindustry.entities.bullet.ContinuousLaserBulletType;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.ContinuousTurret;
import mindustry.world.meta.*;

/** A warehouse defense beam, paid in six-frame material batches. No free persistent beam. */
public class NanoCleaver extends ContinuousTurret{
    public NanoCleaver(String name){
        super(name);requirements(Category.turret,BuildVisibility.sandboxOnly,ItemStack.empty);
        size=3;health=6000;armor=12;range=240;rotateSpeed=4;shootY=12;
        hasItems=acceptsItems=true;itemCapacity=120;canOverdrive=false;
        targetAir=targetGround=true;shootCone=12;
        ContinuousLaserBulletType beam=new ContinuousLaserBulletType(140f);
        beam.length=240;beam.width=6;beam.lifetime=18;beam.incendChance=0;
        beam.colors=new Color[]{Color.valueOf("237e4a66"),Color.valueOf("4ce68c"),Color.valueOf("d9ffe5")};
        shootType=beam;buildType=NanoCleaverBuild::new;
    }
    public class NanoCleaverBuild extends ContinuousTurretBuild{
        public float paidTime;
        @Override public boolean acceptItem(Building source,Item item){return source.team==team&&item==CryonItems.nanoMaterial&&items.get(item)<itemCapacity;}
        @Override public boolean canUnload(){return false;}
        @Override public boolean hasAmmo(){return enabled&&team==DisasterFactions.nano&&(paidTime>0||items.has(CryonItems.nanoMaterial));}
        @Override public void updateTile(){
            if(!mindustry.Vars.net.client()&&!hasAmmo()){
                for(var entry:bullets)entry.bullet.remove();bullets.clear();
            }
            super.updateTile();
            if(mindustry.Vars.net.client()||bullets.isEmpty())return;
            if(paidTime<=0){
                if(!items.has(CryonItems.nanoMaterial)){for(var entry:bullets)entry.bullet.remove();bullets.clear();return;}
                items.remove(CryonItems.nanoMaterial,1);paidTime=6f;
            }
            paidTime=Math.max(0f,paidTime-Time.delta);
        }
        @Override public byte version(){return 4;}
        @Override public void write(Writes w){super.write(w);w.f(paidTime);}
        @Override public void read(Reads r,byte revision){super.read(r,revision);paidTime=revision>=4?r.f():0f;}
    }
}

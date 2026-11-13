package cryon.Type;

import cryon.Features.OrbitalTargeting;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;

/** Native turret targeting, with opt-in access to orbital aircraft. */
public class OrbitalItemTurret extends ItemTurret{
    public static final Stat targetsOrbit = new Stat("cryon-targets-orbit");
    public OrbitalItemTurret(String name){
        super(name);
        buildType = OrbitalItemTurretBuild::new;
    }
    @Override public void setStats(){
        super.setStats();
        stats.add(targetsOrbit, true);
    }
    public class OrbitalItemTurretBuild extends ItemTurretBuild{
        @Override protected void findTarget(){ OrbitalTargeting.run(super::findTarget); }
        @Override protected boolean validateTarget(){
            return OrbitalTargeting.test(super::validateTarget);
        }
    }
}

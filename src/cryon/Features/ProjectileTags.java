package cryon.Features;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.*;
import cryon.Type.*;
import cryon.Type.Constructor.ConstructorTurret;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.graphics.Pal;
import mindustry.ui.dialogs.ContentInfoDialog;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import mindustry.entities.bullet.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.meta.*;

/** Explicit type metadata plus per-shot inherited tags. Never occupies Bullet.data. */
public final class ProjectileTags{
    public static final int NORMAL = 0, PHASE = 1, CONSTRUCTOR = 2, SPATIAL = 4;
    public static final Stat tagStat = new Stat("cryon-projectile-tags", StatCat.function);
    private static final ObjectIntMap<BulletType> masks = new ObjectIntMap<>();
    private static boolean installed;
    public static MapAlliances.AllianceBullet current;

    public static void mark(BulletType type, int tags){
        if(type == null) return;
        if((tags & (CONSTRUCTOR | SPATIAL)) != 0) tags |= PHASE;
        masks.put(type, masks.get(type, 0) | tags);
    }
    public static int mask(BulletType type){ return mask(type, 0); }
    private static int mask(BulletType type, int depth){
        if(type == null) return 0;
        int value = masks.get(type, 0);
        if(type instanceof EonPhaseBulletType || type instanceof EonMineBulletType || type instanceof BlackHoleBulletType) value |= PHASE | SPATIAL;
        if(type.status != null && type.status.name.equals("cryon-phase-damage")) value |= PHASE;
        if(depth < 5){
            value |= mask(type.fragBullet, depth + 1) | mask(type.intervalBullet, depth + 1);
            if(type.spawnBullets != null) for(BulletType child : type.spawnBullets) value |= mask(child, depth + 1);
        }
        return value;
    }
    public static void inherit(BulletType from, BulletType to){ mark(to, mask(from)); }
    public static String label(BulletType type){ return label(mask(type)); }
    public static String label(int mask){
        if(mask == 0) return Core.bundle.get("tag.cryon-normal");
        String text = Core.bundle.get("tag.cryon-phase");
        if((mask & CONSTRUCTOR) != 0) text += " · " + Core.bundle.get("tag.cryon-constructor");
        if((mask & SPATIAL) != 0) text += " · " + Core.bundle.get("tag.cryon-spatial");
        return text;
    }
    public static void install(){
        if(installed) return; installed = true;
        if(!Vars.headless) Events.on(mindustry.game.EventType.ClientLoadEvent.class, e -> Vars.ui.content = new TaggedContentDialog());
        for(Block block : Vars.content.blocks()){
            if(block instanceof ConstructorTurret){ mark(((ConstructorTurret)block).shootType, CONSTRUCTOR); }
        }
        for(Block block : Vars.content.blocks()){
            if(!(block instanceof Turret)) continue;
            Seq<BulletType> ammo = ammo((Turret)block);
            if(ammo.isEmpty()) continue;
            block.checkStats();
            block.stats.add(tagStat, table -> display(block, table));
        }
        for(UnitType unit : Vars.content.units()){
            if(unit.weapons.isEmpty()) continue;
            unit.checkStats();
            unit.stats.add(tagStat, table -> display(unit, table));
        }
    }
    public static void display(UnlockableContent content, Table table){
        if(content instanceof UnitType){
            UnitType unit = (UnitType)content;
            for(int i = 0; i < unit.weapons.size; i++){
                Weapon weapon = unit.weapons.get(i);
                String fallback = Core.bundle.format("tag.cryon-weapon", i + 1);
                String name = weapon.name == null || weapon.name.isEmpty() ? fallback : Core.bundle.get("weapon." + weapon.name + ".name", fallback);
                table.add(name + ": " + label(weapon.bullet)).left().row();
            }
        }else if(content instanceof ItemTurret){
            ((ItemTurret)content).ammoTypes.each((item, bullet) -> table.add(item.localizedName + ": " + label(bullet)).left().row());
        }else if(content instanceof LiquidTurret){
            ((LiquidTurret)content).ammoTypes.each((liquid, bullet) -> table.add(liquid.localizedName + ": " + label(bullet)).left().row());
        }else if(content instanceof ContinuousLiquidTurret){
            ((ContinuousLiquidTurret)content).ammoTypes.each((liquid, bullet) -> table.add(liquid.localizedName + ": " + label(bullet)).left().row());
        }else if(content instanceof Turret) for(BulletType type : ammo((Turret)content)) table.add(label(type)).left().row();
    }
    /** v160 recomputes a fresh Stats object on every open; v157 keeps the original object. */
    private static class TaggedContentDialog extends ContentInfoDialog{
        @Override public void show(UnlockableContent content){
            super.show(content);
            boolean weapon = content instanceof Turret && !ammo((Turret)content).isEmpty()
                || content instanceof UnitType && !((UnitType)content).weapons.isEmpty();
            if(!weapon) return;
            var category = content.stats.toMap().get(tagStat.category);
            if(category != null && category.containsKey(tagStat)) return;
            content.stats.add(tagStat, table -> display(content, table));
            ScrollPane pane = cont.find(element -> element instanceof ScrollPane);
            if(pane != null && pane.getWidget() instanceof Table){
                Table table = (Table)pane.getWidget();
                table.row(); table.add(tagStat.localized()).color(Pal.accent).left().padTop(12f).row();
                table.table(tags -> display(content, tags)).left().padLeft(10f).row();
            }
        }
    }
    private static Seq<BulletType> ammo(Turret turret){
        Seq<BulletType> types = new Seq<>();
        if(turret instanceof ItemTurret) ((ItemTurret)turret).ammoTypes.each((key, value) -> types.addUnique(value));
        else if(turret instanceof LiquidTurret) ((LiquidTurret)turret).ammoTypes.each((key, value) -> types.addUnique(value));
        else if(turret instanceof PowerTurret) types.add(((PowerTurret)turret).shootType);
        else if(turret instanceof ContinuousLiquidTurret) ((ContinuousLiquidTurret)turret).ammoTypes.each((key, value) -> types.addUnique(value));
        else if(turret instanceof ContinuousTurret) types.add(((ContinuousTurret)turret).shootType);
        types.removeAll(t -> t == null); return types;
    }
    public static void initialize(MapAlliances.AllianceBullet bullet){
        bullet.projectileTags = mask(bullet.type);
        if(current != null){
            bullet.projectileTags |= current.projectileTags;
            if(current.realityWeakened && (bullet.projectileTags & PHASE) != 0) weaken(bullet);
        }
        check(bullet);
    }
    public static void weaken(MapAlliances.AllianceBullet bullet){
        if(bullet.realityWeakened) return;
        bullet.realityWeakened = true; bullet.damage *= 0.5f;
    }
    public static void check(MapAlliances.AllianceBullet bullet){
        if(bullet.realityWeakened || (bullet.projectileTags & PHASE) == 0) return;
        float length = -1f;
        BulletType type = bullet.type;
        if(type instanceof cryon.Unity.UnityHybridBullet) type = ((cryon.Unity.UnityHybridBullet)type).carrier;
        if(type instanceof LaserBulletType) length = ((LaserBulletType)type).length;
        else if(type instanceof ContinuousBulletType) length = ((ContinuousBulletType)type).length;
        else if(type instanceof RailBulletType) length = ((RailBulletType)type).length;
        else if(type instanceof ShrapnelBulletType) length = ((ShrapnelBulletType)type).length;
        float ex, ey;
        if((type instanceof BeaconBulletType || type instanceof KismetBulletType) && bullet.aimX >= 0f && bullet.aimY >= 0f){
            ex = bullet.aimX; ey = bullet.aimY;
        }else if(length >= 0f){ ex = bullet.x + Mathf.cosDeg(bullet.rotation()) * length; ey = bullet.y + Mathf.sinDeg(bullet.rotation()) * length; }
        else{
            float delta = type instanceof PointBulletType ? bullet.lifetime : arc.util.Time.delta;
            ex = bullet.x + bullet.vel.x * delta; ey = bullet.y + bullet.vel.y * delta;
        }
        if(RealityAnchoringMatrix.crosses(bullet.team, bullet.x, bullet.y, ex, ey)) weaken(bullet);
    }
}

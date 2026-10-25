package cryon.Unity;

import arc.Core;
import arc.struct.*;
import arc.util.serialization.*;
import mindustry.Vars;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.*;

/** Versioned, name-based recipes shared by local library, configurations and saves. */
public class UnityBlueprint{
    public static final int maxModules = 16;
    public String name = "团结方案";
    public String color = "";
    public int targeting; // 0 native, 1 closest, 2 strongest, 3 weakest, 4 air, 5 buildings
    public Seq<Module> modules = new Seq<>();
    public static class Module{
        public String turret = "", ammo = "";
        public Module(){}
        public Module(String turret){this.turret = turret;}
        public Turret source(){return (Turret)Vars.content.block(turret);}
    }
    private static Json json(){Json json=new Json();json.setTypeName(null);return json;}
    public String encode(){return json().toJson(this);}
    public UnityBlueprint copy(){return decode(encode());}
    public static UnityBlueprint decode(String text){return decode(text,true);}
    public static UnityBlueprint decodeStored(String text){return decode(text,false);}
    private static UnityBlueprint decode(String text,boolean strict){
        if(text == null || text.length() > 16000) throw new IllegalArgumentException("方案过长");
        UnityBlueprint b = json().fromJson(UnityBlueprint.class, text);
        if(b == null || b.modules == null || b.modules.size > maxModules || b.name == null || b.name.length() > 48
            || b.targeting < 0 || b.targeting > 5 || b.color == null || !b.color.matches("([0-9a-fA-F]{6})?"))
            throw new IllegalArgumentException("无效方案");
        for(Module m : b.modules){
            if(m == null || m.ammo == null || m.turret == null || !(strict?eligible(Vars.content.block(m.turret)):Vars.content.block(m.turret) instanceof Turret))
                throw new IllegalArgumentException("无效炮塔");
            Turret t=m.source();
            if(t instanceof ItemTurret && !((ItemTurret)t).ammoTypes.containsKey(Vars.content.item(m.ammo)))
                throw new IllegalArgumentException("请选择物品弹药");
            if(t instanceof LiquidTurret && !((LiquidTurret)t).ammoTypes.containsKey(Vars.content.liquid(m.ammo)))
                throw new IllegalArgumentException("请选择液体弹药");
            if(t instanceof ContinuousLiquidTurret && !((ContinuousLiquidTurret)t).ammoTypes.containsKey(Vars.content.liquid(m.ammo)))
                throw new IllegalArgumentException("请选择液体弹药");
        }
        return b;
    }
    public static boolean eligible(Block b){
        return b instanceof Turret && b.minfo.mod != null && b.minfo.mod.name.equals("cryon") && cryon.TechTree.CryonTechTree.fusionMember(b) && !b.isHidden();
    }
    public static Seq<Turret> catalog(){
        Seq<Turret> out=new Seq<>();
        for(Block b:Vars.content.blocks()) if(eligible(b)) out.add((Turret)b);
        out.sort((a,b)->a.localizedName.compareTo(b.localizedName));
        return out;
    }
    public static Seq<String> ammoNames(Turret t){
        Seq<String> out=new Seq<>();
        if(t instanceof ItemTurret) for(Item i:((ItemTurret)t).ammoTypes.keys())out.add(i.name);
        if(t instanceof LiquidTurret) for(Liquid i:((LiquidTurret)t).ammoTypes.keys())out.add(i.name);
        if(t instanceof ContinuousLiquidTurret) for(Liquid i:((ContinuousLiquidTurret)t).ammoTypes.keys())out.add(i.name);
        out.sort();return out;
    }
    public ItemStack[] requirements(){return calculateCost(1f);}
    public ItemStack[] cost(){
        ItemStack[] result=requirements();float multiplier=Vars.state==null?1f:Vars.state.rules.buildCostMultiplier;
        for(ItemStack stack:result)stack.amount=Math.round(stack.amount*multiplier);return result;
    }
    private ItemStack[] calculateCost(float rule){
        ObjectIntMap<Item> total=new ObjectIntMap<>();
        double surcharge=(100+Math.max(0, modules.size-1)*20)/100d;
        for(Module m:modules) for(ItemStack stack:m.source().requirements){
            total.increment(stack.item, 0, Math.max(0,Math.round((float)Math.ceil(stack.amount*surcharge)*rule)));
        }
        Seq<ItemStack> out=new Seq<>();
        for(ObjectIntMap.Entry<Item> e:total)out.add(new ItemStack(e.key,e.value));
        out.sort(s -> s.item.id);return out.toArray(ItemStack.class);
    }
    public float work(){
        float work=0;
        for(Module m:modules) work+=m.source().buildTime;
        return Math.max(600f,work*(1f+Math.max(0,modules.size-1)*0.2f));
    }
    public boolean researched(){
        for(Module m:modules)if(Vars.state.isCampaign() && !m.source().unlockedNow())return false;
        return true;
    }
    public static Seq<UnityBlueprint> library(){
        Seq<UnityBlueprint> out=new Seq<>();
        try{
            JsonValue root=new JsonReader().parse(Core.settings.getString("cryon-unity-library-v1","[]"));
            for(JsonValue entry:root){try{out.add(decode(entry.asString()));}catch(Exception ignored){}}
        }catch(Exception ignored){}
        return out;
    }
    public static void saveLibrary(Seq<UnityBlueprint> library){
        Seq<String> entries=new Seq<>();for(UnityBlueprint b:library)entries.add(b.encode());
        Core.settings.put("cryon-unity-library-v1",json().toJson(entries.toArray(String.class),String[].class));
        Core.settings.manualSave();
    }
}

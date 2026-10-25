package cryon.Unity;

import arc.scene.ui.layout.Table;
import arc.util.Strings;
import cryon.Type.UnityTurret.UnityBuild;
import cryon.Constructor.ConstructorConsumer;
import mindustry.Vars;
import mindustry.type.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.consumers.*;

/** The same resource summary is used in blueprint previews and the finished turret HUD. */
public class UnityConsumption{
    public static void display(Table table,UnityBlueprint recipe,UnityBuild installed){
        table.left().top();
        if(recipe.modules.isEmpty()){table.add("无攻击模块，无运行消耗").left();return;}
        table.add("融合攻击的供给组成").left().row();
        if(installed!=null && installed.fusion!=null){
            table.add("单一主弹 · 基础伤害 "+Strings.autoFixed(installed.fusion.bullet==null?0f:installed.fusion.bullet.damage,1)).left().row();
            table.add("融合射程 "+Strings.autoFixed(installed.fusion.range/Vars.tilesize,1)+" 格 · 装填 "+Strings.autoFixed(installed.fusion.reload/60f,2)+" 秒").left().row();
            table.add("攻击目标："+(installed.fusion.targetAir?(installed.fusion.targetGround?"对空、对地（票数相同）":"仅对空"):"仅对地")).left().row();
        }
        if(installed!=null)table.label(()->"当前电力请求："+Strings.autoFixed(installed.powerUse()*60f,2)+" / 秒").left().row();
        if(installed!=null)table.label(()->"攻击状态："+installed.fusionStatus).left().row();
        if(installed!=null && installed.heatRequirement()>0f)table.label(()->"热量供给："+Strings.autoFixed(installed.fusionHeat,1)+" / "+Strings.autoFixed(installed.heatRequirement(),1)).left().row();
        if(installed!=null && installed.constructorUse()>0f)table.label(()->"构能满足率："+Strings.autoFixed(installed.constructorSatisfaction()*100f,1)+"%").left().row();
        for(int index=0;index<recipe.modules.size;index++){
            UnityBlueprint.Module spec=recipe.modules.get(index);Turret source=spec.source();
            table.add((index+1)+". "+source.localizedName).left().row();
            if(source instanceof ItemTurret){
                Item item=Vars.content.item(spec.ammo);
                float amount=source.ammoPerShot/((ItemTurret)source).ammoTypes.get(item).ammoMultiplier;
                row(table,item.uiIcon,item.localizedName+" · "+Strings.autoFixed(amount,2)+" / 次耗弹");
            }
            if(source instanceof LiquidTurret || source instanceof ContinuousLiquidTurret){
                Liquid liquid=Vars.content.liquid(spec.ammo);
                row(table,liquid.uiIcon,"弹药液体："+liquid.localizedName);
            }
            if(source.consPower!=null)table.add("电力："+Strings.autoFixed(source.consPower.usage*60f,2)+" / 秒（工作时）").left().row();
            for(Consume consumer:source.consumers){
                String optional=consumer.optional?"（可选冷却）":"";
                if(consumer instanceof ConsumeLiquid){
                    ConsumeLiquid c=(ConsumeLiquid)consumer;
                    row(table,c.liquid.uiIcon,c.liquid.localizedName+" · "+Strings.autoFixed(c.amount*60f,2)+" / 秒"+optional);
                }else if(consumer instanceof ConsumeLiquids){
                    for(LiquidStack stack:((ConsumeLiquids)consumer).liquids)
                        row(table,stack.liquid.uiIcon,stack.liquid.localizedName+" · "+Strings.autoFixed(stack.amount*60f,2)+" / 秒"+optional);
                }else if(consumer instanceof ConsumeLiquidFilter){
                    ConsumeLiquidFilter c=(ConsumeLiquidFilter)consumer;
                    if(!consumer.optional && !spec.ammo.isEmpty() && !(source instanceof ItemTurret)){
                        Liquid selected=Vars.content.liquid(spec.ammo);
                        if(selected!=null && c.consumes(selected)){
                            row(table,selected.uiIcon,selected.localizedName+" · 基准 "+Strings.autoFixed(c.amount*60f,2)+" / 秒");
                            continue;
                        }
                    }
                    StringBuilder accepted=new StringBuilder();int count=0;
                    for(Liquid liquid:Vars.content.liquids())if(c.consumes(liquid)){
                        if(count++>0)accepted.append(" / ");accepted.append(liquid.localizedName);if(count>=4){accepted.append(" …");break;}
                    }
                    table.add("液体："+accepted+" · 基准 "+Strings.autoFixed(c.amount*60f,2)+" / 秒"+optional).wrap().width(320f).left().row();
                }else if(consumer instanceof ConsumeItems){
                    for(ItemStack stack:((ConsumeItems)consumer).items)
                        row(table,stack.item.uiIcon,stack.item.localizedName+" × "+stack.amount+" / 次消耗");
                }
            }
            if(source instanceof cryon.Type.Constructor.ConstructorTurret){
                float use=((cryon.Type.Constructor.ConstructorTurret)source).constructorUse;
                table.add("构能："+Strings.autoFixed(use*60f,2)+" / 秒").left().row();
            }
            if(source.heatRequirement>0f)table.add("必需热量："+Strings.autoFixed(source.heatRequirement,1)).left().row();
            mindustry.gen.Building heatProbe=installed!=null && index<installed.mounts.size?installed.mounts.get(index).build:source.buildType.get();
            if(heatProbe instanceof cryon.Injector.HeatBoostTurret.HeatBoostTurretBuild){
                cryon.Injector.HeatBoostTurret.HeatBoostTurretBuild heat=(cryon.Injector.HeatBoostTurret.HeatBoostTurretBuild)heatProbe;
                table.add("可选热量加速：满载热量 "+Strings.autoFixed(heat.heatRequirement(),1)).left().row();
            }
            if(installed!=null && index<installed.mounts.size){
                final UnityBuild.Mount mount=installed.mounts.get(index);
                table.label(()->mount.build.canConsume() && mount.build.hasAmmo()?"[green]供给就绪[]":"[orange]缺少供给或正在冷却[]").left().row();
            }
        }
        table.add("每次融合攻击统一消耗所选弹药；液体、电力及冷却按工作状态消耗。").wrap().width(320f).left().padTop(5f).row();
    }
    private static void row(Table table,arc.graphics.g2d.TextureRegion icon,String text){
        table.table(t->{if(icon!=null)t.image(icon).size(22f).padRight(5f);t.add(text).wrap().width(290f).left();}).left().row();
    }
}

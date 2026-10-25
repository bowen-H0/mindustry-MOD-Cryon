package cryon.Unity;

import arc.Core;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import cryon.Type.UnityTurret.UnityBuild;
import mindustry.Vars;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.blocks.defense.turrets.*;

/** Touch-friendly editor; only a build request changes the world. */
public class UnityEditor extends BaseDialog{
    private final UnityBuild build;
    private UnityBlueprint draft;
    private final Table body=new Table();
    private int tab=1;
    private static final String[] targeting={"主体炮塔逻辑","最近目标","最大生命值最高","当前生命值最低","只攻击空中","只攻击建筑"};

    public UnityEditor(UnityBuild build){
        super("团结 · 组合炮塔工坊");this.build=build;
        shouldPause=true;
        draft=build.blueprint.copy();
        cont.top();
        cont.table(t->{
            t.defaults().height(48f).growX();
            t.button("炮塔目录",()->{tab=0;refresh();});
            t.button("组合编辑",()->{tab=1;refresh();});
            t.button("炮塔库",()->{tab=2;refresh();});
        }).growX().row();
        cont.add(body).grow().row();
        if(Vars.net.active())cont.add("联机编辑不暂停服务器。方案库保存在本机。").color(arc.graphics.Color.lightGray).pad(6f).row();
        addCloseButton();refresh();
    }
    private void refresh(){
        body.clear();body.top();
        if(tab==0)catalog();else if(tab==1)recipe();else library();
    }
    private void catalog(){
        body.add("克莱恩科技树炮塔 · 点击 + 添加，可重复融合（最多 16 个模块）").wrap().growX().pad(8f).row();
        body.pane(list->{
            list.top();list.defaults().growX().pad(3f);
            for(Turret turret:UnityBlueprint.catalog())list.table(row->{
                row.image(turret.uiIcon).size(44f).pad(6f);
                row.add(turret.localizedName+(Vars.state.isCampaign() && !turret.unlockedNow()?" [gray]未研究[]":"")).growX().left();
                row.button(Icon.add,Styles.clearNonei,()->{
                    if(draft.modules.size>=UnityBlueprint.maxModules){message("最多融合 16 个模块。");return;}
                    UnityBlueprint.Module module=new UnityBlueprint.Module(turret.name);
                    Seq<String> ammo=UnityBlueprint.ammoNames(turret);
                    if(ammo.any())module.ammo=ammo.first();
                    draft.modules.add(module);
                    Vars.ui.showInfoToast("已添加 "+turret.localizedName+" · "+draft.modules.size+" 个模块",2f);
                }).size(54f);
            }).row();
        }).grow();
    }
    private void recipe(){
        body.table(form->{
            form.add("方案名").padRight(8f);
            form.field(draft.name,s->draft.name=s).maxTextLength(48).growX();form.row();
            form.add("弹幕颜色").padRight(8f);
            form.table(colors->{
                colors.image(mindustry.gen.Tex.whiteui).color(draft.color.isEmpty()?arc.graphics.Color.lightGray:arc.graphics.Color.valueOf(draft.color)).size(30f).padRight(8f);
                colors.button(draft.color.isEmpty()?"选择颜色":"更改颜色",()->Vars.ui.picker.show(
                    draft.color.isEmpty()?arc.graphics.Color.valueOf("70FFD5"):arc.graphics.Color.valueOf(draft.color),false,
                    color->{draft.color=color.toString().substring(0,6);refresh();})).growX().height(44f);
                colors.button("原色",()->{draft.color="";refresh();}).height(44f).padLeft(5f);
            }).growX();form.row();
            form.add(draft.color.isEmpty()?"保留各来源炮塔原色":"已启用弹幕颜色覆盖").colspan(2).wrap().growX().color(arc.graphics.Color.lightGray).pad(6f);form.row();
            form.add("锁敌逻辑");
            form.button(targeting[draft.targeting],()->{
                draft.targeting=(draft.targeting+1)%targeting.length;refresh();
            }).growX().height(44f);
        }).growX().pad(8f).row();
        body.add("第一项决定弹体主体；其余融合分裂、激光等属性。成品共用装填，只发射一套混合攻击。").wrap().growX().pad(6f).row();
        if(draft.modules.isEmpty())body.add("尚无攻击模块。到“炮塔目录”点击 + 添加。").pad(16f).row();
        ScrollPane pane=body.pane(list->{
            list.top();list.defaults().growX().height(84f).padBottom(4f);
            for(int i=0;i<draft.modules.size;i++){
                final int index=i;UnityBlueprint.Module module=draft.modules.get(i);Turret turret=module.source();
                list.table(Styles.black6,row->{
                    ImageButton handle=row.button(Icon.move,Styles.clearNonei,()->{}).size(48f,78f).get();
                    handle.addListener(new InputListener(){
                        float start;
                        @Override public boolean touchDown(InputEvent event,float x,float y,int pointer,KeyCode button){
                            start=event.stageY;return true;
                        }
                        @Override public void touchUp(InputEvent event,float x,float y,int pointer,KeyCode button){
                            int to=Mathf.clamp(index+Math.round((start-event.stageY)/88f),0,draft.modules.size-1);
                            if(to!=index){draft.modules.remove(index);draft.modules.insert(to,module);Core.app.post(UnityEditor.this::refresh);}
                        }
                    });
                    row.image(turret.uiIcon).size(36f).pad(4f);
                    row.table(info->{
                        info.add((index+1)+". "+turret.localizedName).left().growX().row();
                        if(!module.ammo.isEmpty())info.button("弹药: "+ammoLabel(module),()->selectAmmo(module)).growX().height(34f);
                    }).growX();
                    row.table(arrows->{
                        arrows.button(Icon.upOpen,Styles.clearNonei,()->move(index,-1)).size(36f).disabled(index==0).row();
                        arrows.button(Icon.downOpen,Styles.clearNonei,()->move(index,1)).size(36f).disabled(index==draft.modules.size-1);
                    });
                    row.button(Icon.trash,Styles.clearNonei,()->{draft.modules.remove(index);refresh();}).size(42f);
                }).row();
            }
        }).grow().get();
        // A drag handle must not become a flick gesture on touch screens; scrollbars still work.
        pane.setFlickScroll(false);
        body.row();
        body.pane(cost->{showCost(cost,draft);cost.row();UnityConsumption.display(cost,draft,null);}).height(170f).growX().row();
        body.table(actions->{
            actions.defaults().height(50f).growX();
            actions.button("保存到炮塔库",Icon.save,()->save());
            actions.button("施工此方案",Icon.hammer,()->construct(draft));
        }).growX().pad(6f);
    }
    private void move(int from,int delta){
        int to=from+delta;if(to<0 || to>=draft.modules.size)return;
        draft.modules.swap(from,to);refresh();
    }
    private String ammoLabel(UnityBlueprint.Module module){
        if(module.source() instanceof ItemTurret){Item item=Vars.content.item(module.ammo);return item==null?module.ammo:item.localizedName;}
        Liquid l=Vars.content.liquid(module.ammo);return l==null?module.ammo:l.localizedName;
    }
    private void selectAmmo(UnityBlueprint.Module module){
        BaseDialog dialog=new BaseDialog("选择弹种");
        dialog.cont.pane(list->{for(String name:UnityBlueprint.ammoNames(module.source())){
            String old=module.ammo;module.ammo=name;String label=ammoLabel(module);module.ammo=old;
            list.button(label,()->{module.ammo=name;dialog.hide();refresh();}).width(280f).height(50f).row();
        }});dialog.addCloseButton();dialog.show();
    }
    private void library(){
        Seq<UnityBlueprint> library=UnityBlueprint.library();
        body.add("本机方案库 · "+library.size+" 个方案").pad(8f).row();
        body.add("像普通方块一样排入建造队列，随施工消耗核心材料；完工替换为不可编辑的成品炮塔。").wrap().growX().pad(6f).row();
        if(library.isEmpty())body.add("在组合编辑中保存你的第一个方案。").pad(20f).row();
        body.pane(list->{
            list.top();list.defaults().growX().pad(4f);
            for(UnityBlueprint recipe:library)list.table(Styles.black6,row->{
                row.top();row.add(recipe.name+" · "+recipe.modules.size+" 个模块").growX().left().pad(8f).row();
                row.table(cost->{showCost(cost,recipe);cost.row();UnityConsumption.display(cost,recipe,null);}).growX().pad(6f).row();
                row.table(actions->{
                    actions.defaults().height(44f).growX();
                    actions.button("编辑",()->{draft=recipe.copy();tab=1;refresh();});
                    actions.button("施工",Icon.hammer,()->construct(recipe));
                    actions.button(Icon.trash,()->{library.remove(recipe,true);UnityBlueprint.saveLibrary(library);refresh();}).width(44f);
                }).growX();
            }).row();
        }).grow();
    }
    private void showCost(Table table,UnityBlueprint recipe){
        table.left().top();
        table.add("核心材料（融合附加费 +"+(int)(Math.max(0,recipe.modules.size-1)*20)+"%）：").colspan(6).left().row();
        int col=0;
        for(ItemStack stack:recipe.cost()){
            table.image(stack.item.uiIcon).size(22f).pad(3f);
            table.add(Integer.toString(stack.amount)).padRight(12f).left();
            if(++col%3==0)table.row();
        }
    }
    private UnityBlueprint checked(UnityBlueprint value){
        try{return UnityBlueprint.decode(value.encode());}
        catch(Exception e){message("方案无效：请检查炮塔和弹种选择。");return null;}
    }
    private void save(){
        UnityBlueprint recipe=checked(draft);if(recipe==null)return;
        if(recipe.modules.isEmpty()){message("请先添加攻击模块。");return;}
        Seq<UnityBlueprint> library=UnityBlueprint.library();
        library.removeAll(b->b.name.equals(recipe.name));
        if(library.size>=64){message("炮塔库最多保存 64 个方案。");return;}
        library.add(recipe);UnityBlueprint.saveLibrary(library);tab=2;refresh();
    }
    private void construct(UnityBlueprint value){
        UnityBlueprint recipe=checked(value);if(recipe==null)return;
        if(!build.isValid() || build.team!=Vars.player.team()){message("无法配置这座建筑。");return;}
        if(recipe.modules.isEmpty()){message("请先添加攻击模块。");return;}
        if(!recipe.researched()){message("请先研究方案中的炮塔。");return;}
        if(!Vars.player.unit().canBuild()){message("请先操控具有建造能力的单位。");return;}
        build.configure("build:"+recipe.encode());hide();
    }
    private void message(String text){Vars.ui.showInfo(text);}
}

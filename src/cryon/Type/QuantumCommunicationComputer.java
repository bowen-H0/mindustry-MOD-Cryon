package cryon.Type;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import cryon.Constructor.ConstructorConsumer;
import cryon.Content.CryonItems;
import cryon.Story.QuantumCommunicationDialog;
import cryon.Story.QuantumConversation;
import cryon.Story.StoryProgress;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValues;

public class QuantumCommunicationComputer extends Block{
    public static final int refusePioneer = 3;
    public float constructorUse = 2f;

    public QuantumCommunicationComputer(String name){
        super(name);
        size = 5;
        health = 1800;
        solid = update = hasPower = configurable = true;
        saveConfig = false;
        consumePower(5f);
        requirements(Category.effect, ItemStack.with(CryonItems.aluminum, 300, CryonItems.nickel, 180,
                Items.silicon, 250, CryonItems.cryoAlloy, 120, Items.phaseFabric, 80));
        buildType = QuantumComputerBuild::new;
        config(Boolean.class, (QuantumComputerBuild build, Boolean agree) -> {
            if(Boolean.TRUE.equals(agree) && build.ready()) StoryProgress.set(StoryProgress.Flag.underground, true);
        });
        config(Integer.class, (QuantumComputerBuild build, Integer action) -> {
            if(!build.ready()) return;
            if(action >= 0 && action < QuantumConversation.Contact.values().length){
                StoryProgress.recordContact(QuantumConversation.Contact.values()[action]);
            }else if(action == refusePioneer){
                StoryProgress.recordPioneerRefusal();
            }else if(action == 4 || action == 5){
                StoryProgress.completeTerminal(action == 4 ? "desolate-captured" : "aravis-captured");
            }
        });
    }

    @Override public void setStats(){
        super.setStats();
        stats.add(Stat.input, StatValues.string(Core.bundle.format("stat.constructor.input", (int)(constructorUse * 60f))));
    }

    @Override public void setBars(){
        super.setBars();
        addBar("constructor", (QuantumComputerBuild build) -> new Bar(
                () -> Core.bundle.get("cryon.quantum.constructor"), () -> Pal.accent,
                () -> build.constructorSatisfaction));
    }

    public class QuantumComputerBuild extends Building implements ConstructorConsumer{
        public float constructorSatisfaction;

        public boolean ready(){
            return Vars.state.isCampaign() && !Vars.state.isEditor()
                    && isValid() && enabled && power != null && power.status >= 0.999f
                    && constructorSatisfaction >= 0.999f
                    && block.unlockedNow();
        }
        @Override public float constructorUse(){ return constructorUse; }
        @Override public void constructorStatus(float status){ constructorSatisfaction = arc.math.Mathf.clamp(status); }
        @Override public boolean constructorValid(){ return enabled && isValid(); }
        @Override public void buildConfiguration(Table table){
            if(!Vars.state.isCampaign() || Vars.state.isEditor()){
                table.add("@cryon.quantum.campaign-only").wrap().width(320f).pad(8f);
                return;
            }
            table.add("@cryon.quantum.requirements").wrap().width(320f).pad(8f).row();
            table.button("@cryon.quantum.open", () -> {
                if(ready()) new QuantumCommunicationDialog(this).show();
            }).size(320f, 60f).disabled(button -> !ready());
        }
        @Override public void draw(){
            super.draw();
            if(ready()){
                arc.graphics.g2d.Draw.color(Color.valueOf("7fe4e8"));
                arc.graphics.g2d.Draw.alpha(0.25f + arc.math.Mathf.absin(20f, 0.2f));
                arc.graphics.g2d.Lines.stroke(1f);
                arc.graphics.g2d.Lines.circle(x, y, 11f);
                arc.graphics.g2d.Draw.reset();
            }
        }
    }
}

package cryon.Type;

import arc.Core;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.*;
import cryon.Content.CryonItems;
import cryon.Features.PlanetProjectionRenderer;
import mindustry.Vars;
import mindustry.content.*;
import mindustry.gen.Building;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;

/** A powered, configurable projection of any loaded planet's actual mesh. */
public class PlanetProjector extends Block{
    public float projectionSize = 224f, hoverHeight = 128f;
    public PlanetProjector(String name){
        super(name);
        size = 4;
        health = 1200;
        solid = update = hasPower = configurable = saveConfig = copyConfig = emitLight = true;
        clipSize = 640f;
        lightRadius = 220f;
        consumePower(6f);
        requirements(Category.effect, ItemStack.with(CryonItems.ferrum, 200, CryonItems.hardSteel, 120, Items.silicon, 200, CryonItems.nickel, 80));
        buildType = PlanetProjectorBuild::new;
        config(String.class, (PlanetProjectorBuild build, String nameValue) -> {
            if(Vars.content.planet(nameValue) != null) build.planetName = nameValue;
        });
        configClear((PlanetProjectorBuild build) -> build.planetName = "cryon-aravis");
    }

    public class PlanetProjectorBuild extends Building{
        private String planetName = "cryon-aravis";
        private final TextureRegion strip = new TextureRegion();
        public Planet selectedPlanet(){
            Planet planet = Vars.content.planet(planetName);
            return planet == null ? Planets.serpulo : planet;
        }
        @Override public Object config(){return selectedPlanet().name;}
        @Override public void buildConfiguration(Table table){
            table.label(() -> selectedPlanet().localizedName).pad(8f).row();
            table.button(Core.bundle.get("cryon.projector.choose-planet"), this::choosePlanet).size(230f, 54f);
        }
        private void choosePlanet(){
            BaseDialog dialog = new BaseDialog(Core.bundle.get("cryon.projector.choose-planet"));
            dialog.name = "cryon-planet-projector-picker";
            float width = Math.min(680f, Core.scene.getWidth() - 60f);
            int columns = Math.max(1, (int)(width / 220f));
            var planets = Vars.content.planets().copy().sort((a, b) -> a.localizedName.compareTo(b.localizedName));
            dialog.cont.pane(list -> {
                int index = 0;
                for(Planet planet : planets){
                    list.button(planet.localizedName, () -> {configure(planet.name); dialog.hide();})
                        .size(width / columns, 56f).name("cryon-project-planet-" + planet.name);
                    if(++index % columns == 0) list.row();
                }
            }).width(width).maxHeight(440f);
            dialog.addCloseButton();
            dialog.show();
        }
        @Override public void drawLight(){
            if(!enabled || efficiency <= 0.001f) return;
            Drawf.light(x, y, 70f, team.color, 0.35f * efficiency);
            Drawf.light(x, y + hoverHeight, 190f, team.color, 0.25f * efficiency);
        }
        @Override public void draw(){
            super.draw();
            if(!enabled || efficiency <= 0.001f) return;
            float z = Draw.z(), stroke = Lines.getStroke();
            Blending blend = Draw.getBlend();
            try{
                Draw.z(Layer.light + 1f);
                Draw.blend(Blending.additive);
                float t = Time.time, cy = y + hoverHeight + Mathf.sin(t / 40f) * 2f;
                float alpha = efficiency * (0.78f + Mathf.absin(t, 20f, 0.12f));
                Draw.color(team.color, alpha);
                Lines.stroke(1.2f);
                for(int i = 0; i < 3; i++) Lines.arc(x, y, 12f + i * 5f, 0.28f, t * (i % 2 == 0 ? 1f : -1f) + i * 120f);
                float low = Tmp.c1.set(team.color).a(alpha * 0.20f).toFloatBits();
                float high = Tmp.c2.set(team.color).a(alpha * 0.025f).toFloatBits();
                Fill.quad(x - 7f, y, low, x + 7f, y, low, x + 50f, cy - 45f, high, x - 50f, cy - 45f, high);
                TextureRegion region = PlanetProjectionRenderer.request(selectedPlanet());
                if(region != null){
                    int count = 56;
                    float h = projectionSize / count, vh = (region.v2 - region.v) / count;
                    for(int i = 0; i < count; i++){
                        strip.set(region);
                        strip.v = region.v + vh * i;
                        strip.v2 = region.v + vh * (i + 1);
                        Draw.color(Color.white, team.color, 0.22f);
                        Draw.alpha(alpha * (i % 2 == 0 ? 0.9f : 0.66f));
                        Draw.rect(strip, x, cy + projectionSize / 2f - (i + 0.5f) * h, projectionSize, h + 0.12f);
                    }
                }else{
                    Draw.color(team.color, alpha * 0.65f);
                    Lines.circle(x, cy, 53f);
                    Lines.ellipse(x, cy, 53f, 1f, 0.3f, 0f);
                    Lines.ellipse(x, cy, 53f, 0.3f, 1f, 0f);
                }
                Draw.color(team.color, alpha * 0.65f);
                Lines.ellipse(x, cy, 76f, 1f, 0.27f, 0f);
                Lines.arc(x, cy, 80f, 0.15f, t * 0.5f);
                Lines.arc(x, cy, 80f, 0.15f, t * 0.5f + 180f);
                for(int i = 0; i < 12; i++){
                    float p = ((t * 0.35f + i * 8.33f) % 100f) / 100f;
                    Fill.square(x + Mathf.sin(i * 13f + p * 4f) * 30f * p, y + p * (hoverHeight - 45f), 0.8f, 45f);
                }
            }finally{
                Draw.blend(blend);
                Draw.reset();
                Lines.stroke(stroke);
                Draw.z(z);
            }
        }
        @Override public byte version(){return 1;}
        @Override public void write(Writes write){super.write(write);write.str(planetName);}
        @Override public void read(Reads read, byte revision){
            super.read(read, revision);
            planetName = revision >= 1 ? read.str() : "cryon-aravis";
            if(Vars.content.planet(planetName) == null) planetName = "cryon-aravis";
        }
    }
}

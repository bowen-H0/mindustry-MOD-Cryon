package example;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g3d.Camera3D;
import arc.math.geom.Vec3;
import arc.scene.Element;
import arc.scene.event.Touchable;
import arc.util.Align;
import arc.util.Tmp;

import mindustry.Vars;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.ui.Fonts;
import mindustry.ui.dialogs.PlanetDialog;

public class SectorIdDebug{

    public static boolean enabled = true;

    private static boolean installed = false;

    public static void install(){

        if(installed) return;
        installed = true;

        PlanetDialog dialog = Vars.ui.planet;

        dialog.shown(() -> {

            Element overlay = new Element(){

                @Override
                public void draw(){

                    super.draw();

                    if(!enabled) return;

                    drawSectorIds(dialog);
                }
            };

            overlay.setFillParent(true);

            overlay.touchable(() -> Touchable.disabled);

            dialog.addChild(overlay);
        });
    }

    private static void drawSectorIds(PlanetDialog dialog){

        Planet planet = dialog.state.planet;

        if(planet == null) return;

        Camera3D cam = Vars.renderer.planets.cam;

        if(cam == null) return;

        Draw.reset();

        for(Sector sec : planet.sectors){

            if(sec.tile == null) continue;

            Vec3 pos = planet.project(sec, cam, Tmp.v31);

            if(pos.z <= 0f) continue;

            Fonts.outline.getData().setScale(0.45f);
            Fonts.outline.setColor(Color.white);

            Fonts.outline.draw(
                    Integer.toString(sec.id),
                    pos.x,
                    pos.y,
                    Align.center
            );
        }

        Fonts.outline.getData().setScale(1f);
        Draw.reset();
    }
}
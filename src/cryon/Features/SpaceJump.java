package cryon.Features;

import arc.Core;
import arc.Events;
import arc.input.KeyCode;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import cryon.Type.SpaceJumpAbility;
import cryon.Fx.SpaceJumpFx;
import mindustry.Vars;
import mindustry.entities.abilities.Ability;
import mindustry.game.EventType.Trigger;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import mindustry.type.unit.MissileUnitType;
import mindustry.content.StatusEffects;

public class SpaceJump{
    public static mindustry.ai.UnitCommand jumpCommand;

    public static void loadCommands(){
        jumpCommand = new mindustry.ai.UnitCommand("space-jump", "cryon-space-jump-command", null){
            @Override
            public String localized(){ return Core.bundle.get("command.cryon-space-jump"); }
        };
        jumpCommand.switchToMove = false;
        jumpCommand.drawTarget = true;
        jumpCommand.resetTarget = true;
    }

    private static final String packet = "cryon-space-jump";
    private static Unit previewUnit;
    private static Element previewInput;
    private static boolean confirmPreview, blockClick, dismissedUntilRelease;
    private static Unit manualHeldUnit;
    private static final arc.struct.IntMap<Unit> manualUnits = new arc.struct.IntMap<>();
    private static final Vec2 previewLanding = new Vec2();

    public static boolean enabled(){
        return Vars.state != null;
    }

    public static boolean manualEnabled(){
        return enabled() && Vars.state.isCampaign();
    }

    public static boolean excluded(UnitType type){
        String name = type.name.startsWith("cryon-") ? type.name.substring(6) : type.name;
        return name.equals("vain") || name.equals("roche") || name.equals("threshold")
            || name.equals("hyperion") || name.equals("oblivion");
    }

    public static boolean allowed(Unit unit){
        return unit != null && !excluded(unit.type) && !unit.hasEffect(StatusEffects.boss);
    }

    public static void install(){
        if(!Vars.headless){
            // PlacementFragment reads Icon.icons directly rather than UnitCommand.getIcon().
            mindustry.gen.Icon.icons.put(jumpCommand.icon,
                new arc.scene.style.TextureRegionDrawable(Core.atlas.find("cryon-space-jump-command")));
        }
        Events.on(mindustry.game.EventType.ResetEvent.class, event -> {
            manualUnits.clear(); manualHeldUnit = null; dismissedUntilRelease = false; closePreview();
        });
        SpaceJumpRiftRenderer.install();
        SpaceJumpPointRenderer.install();
        SpaceJumpAudio.load();
        SpaceJumpFx.load();
        Events.run(Trigger.update, () -> {
            if(!Vars.state.isGame() || Vars.state.isPaused()) SpaceJumpAudio.stop();
        });
        Events.run(Trigger.afterGameUpdate, () -> {
            if(!enabled()) return;
            Groups.unit.each(unit -> {
                SpaceJumpAbility ability = find(unit);
                if(ability != null) ability.afterUpdate(unit);
                if(unit == previewUnit || (!Vars.headless && blockClick && Vars.player != null && unit == Vars.player.unit())){
                    unit.isShooting = false;
                }
            });
            SpaceJumpAudio.update();
        });
        for(UnitType type : Vars.content.units()){
            if(type.minfo.mod != null && type.minfo.mod.name.equals("cryon")
                && type.flying && !type.hidden && !type.internal
                && type.playerControllable && !(type instanceof MissileUnitType) && !excluded(type)){
                type.abilities.add(new SpaceJumpAbility());
                type.commands = type.commands.copy();
                type.commands.addUnique(jumpCommand);
                for(int i = 0; i < type.engines.size; i++){
                    UnitType.UnitEngine engine = type.engines.get(i);
                    if(!(engine instanceof DigitalEngine)) type.engines.set(i, new DigitalEngine(engine));
                }
                type.clipSize = Math.max(type.clipSize, (type.hitSize * 0.65f + 25f) * 8f);
            }
        }
        Vars.netServer.addPacketHandler(packet, (player, message) -> {
            if(message.equals("cancel")){ cancelManual(player); return; }
            if(!manualEnabled() || !Vars.state.isGame() || Vars.state.isPaused()) return;
            String[] coordinates = message.split(",", -1);
            if(coordinates.length != 2) return;
            try{
                manualJump(player, Float.parseFloat(coordinates[0]), Float.parseFloat(coordinates[1]));
            }catch(NumberFormatException ignored){
                // Reject malformed requests without affecting other players.
            }
        });
        Vars.netClient.addPacketHandler(packet, message -> {
            if(!enabled()) return;
            String[] values = message.split(",", -1);
            try{
                if(values.length < 2) return;
                Unit unit = Groups.unit.getByID(Integer.parseInt(values[1]));
                SpaceJumpAbility ability = find(unit);
                if(ability == null) return;
                if(values[0].equals("start") && values.length == 8){
                    ability.begin(unit, Float.parseFloat(values[2]), Float.parseFloat(values[3]),
                        Float.parseFloat(values[4]), Float.parseFloat(values[5]),
                        Float.parseFloat(values[6]), Integer.parseInt(values[7]));
                }else if(values[0].equals("finish") && values.length == 5){
                    ability.finish(unit, Float.parseFloat(values[2]), Float.parseFloat(values[3]), Float.parseFloat(values[4]));
                }else if(values[0].equals("redirect") && values.length == 4){
                    ability.redirect(Float.parseFloat(values[2]), Float.parseFloat(values[3]));
                }else if(values[0].equals("cancel") && values.length == 2){
                    ability.cancel(unit);
                }
            }catch(NumberFormatException ignored){
            }
        });
        if(!Vars.headless) Events.run(Trigger.draw, () -> {
            if(!enabled() || !Vars.state.isGame() || Vars.player == null) return;
            Groups.unit.each(unit -> {
                if(unit.dead || unit.inFogTo(Vars.player.team())) return;
                SpaceJumpAbility ability = find(unit);
                if(ability != null) ability.drawWarning(unit);
            });
            if(previewUnit != null){
                SpaceJumpAbility ability = find(previewUnit);
                if(ability != null && ability.landing(previewUnit, Core.input.mouseWorldX(), Core.input.mouseWorldY(), previewLanding) != null){
                    SpaceJumpFx.preview(previewUnit, previewLanding.x, previewLanding.y,
                        ability.ready(previewUnit) && ability.distantEnough(previewUnit, previewLanding));
                }
            }
        });
        if(!Vars.headless) Events.run(Trigger.update, SpaceJump::updatePreview);
    }

    private static void updatePreview(){
        boolean held = Core.input.keyDown(KeyCode.altLeft) || Core.input.keyDown(KeyCode.altRight);
        if(!manualEnabled() || !Vars.state.isGame() || Vars.state.isPaused() || Core.scene.hasDialog()
            || Core.scene.getKeyboardFocus() != null || Vars.player == null){
            releaseManual(); closePreview();
            dismissedUntilRelease = held;
            blockClick = false;
            if(previewInput != null) previewInput.visible = false;
            return;
        }
        if(blockClick && !Core.input.keyDown(KeyCode.mouseLeft) && !Core.input.keyDown(KeyCode.mouseRight)) blockClick = false;
        if(!held){
            releaseManual(); closePreview(); dismissedUntilRelease = false;
        }else{
            if(previewUnit != null && (previewUnit != Vars.player.unit() || find(previewUnit) == null)) closePreview();
            if(manualHeldUnit != null && manualHeldUnit != Vars.player.unit()){
                releaseManual(); dismissedUntilRelease = true;
            }
            if(previewUnit == null && !dismissedUntilRelease){
                Unit unit = Vars.player.unit();
                SpaceJumpAbility ability = find(unit);
                if(ability != null && !ability.active() && !ability.reconstructing()){
                    createPreviewInput(); previewUnit = unit;
                    previewInput.visible = true; previewInput.toFront();
                }
            }
            if(Core.input.keyTap(KeyCode.escape) || Core.input.keyTap(KeyCode.mouseRight)){
                releaseManual(); closePreview(); dismissedUntilRelease = true;
            }
            if(confirmPreview && previewUnit != null){
                SpaceJumpAbility ability = find(previewUnit);
                if(ability != null && ability.ready(previewUnit)
                    && ability.landing(previewUnit, Core.input.mouseWorldX(), Core.input.mouseWorldY(), previewLanding) != null
                    && ability.distantEnough(previewUnit, previewLanding)){
                    manualHeldUnit = previewUnit;
                    float x = Core.input.mouseWorldX(), y = Core.input.mouseWorldY();
                    if(Vars.net.client()) Call.serverPacketReliable(packet, x + "," + y);
                    else manualJump(Vars.player, x, y);
                    blockClick = true; dismissedUntilRelease = true; closePreview();
                }
            }
        }
        confirmPreview = false;
        if(previewInput != null) previewInput.visible = previewUnit != null || blockClick;
    }

    private static void releaseManual(){
        if(manualHeldUnit == null) return;
        SpaceJumpAbility ability = find(manualHeldUnit);
        if(ability != null) ability.cancelManual(manualHeldUnit);
        if(Vars.net.client()) Call.serverPacketReliable(packet, "cancel");
        else cancelManual(Vars.player);
        manualHeldUnit = null;
    }

    private static void cancelManual(Player player){
        if(player == null) return;
        Unit unit = manualUnits.remove(player.id);
        SpaceJumpAbility ability = find(unit);
        if(ability != null) ability.cancelManual(unit);
    }

    private static void closePreview(){
        previewUnit = null;
        confirmPreview = false;
    }

    private static void createPreviewInput(){
        if(previewInput != null) return;
        previewInput = new Element();
        previewInput.setFillParent(true);
        previewInput.touchable = Touchable.enabled;
        previewInput.addListener(new InputListener(){
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                if(button == KeyCode.mouseLeft && previewUnit != null) confirmPreview = true;
                if(button == KeyCode.mouseRight){ blockClick = true; dismissedUntilRelease = true; releaseManual(); closePreview(); }
                // Consume the click so selecting a fold destination cannot also shoot or build.
                return true;
            }
        });
        Core.scene.add(previewInput);
    }

    private static class DigitalEngine extends UnitType.UnitEngine{
        private final UnitType.UnitEngine original;
        DigitalEngine(UnitType.UnitEngine original){
            super(original.x, original.y, original.radius, original.rotation);
            this.original = original;
        }
        @Override
        public void draw(Unit unit){
            // Native nozzle glow is part of the ship and must enter the point capture too.
            // Only the separate digital jump trails and thrust effects are omitted.
            original.draw(unit);
        }
    }

    public static void syncStart(Unit unit, float x, float y, float ox, float oy, float rotation, int level){
        if(Vars.net.server()) Call.clientPacketReliable(packet,
            "start," + unit.id + "," + x + "," + y + "," + ox + "," + oy + "," + rotation + "," + level);
    }

    public static void sync(Unit unit, float rotation){
        if(Vars.net.server()) Call.clientPacketReliable(packet,
            "finish," + unit.id + "," + unit.x + "," + unit.y + "," + rotation);
    }

    public static void syncCancel(Unit unit){
        if(Vars.net.server()) Call.clientPacketReliable(packet, "cancel," + unit.id);
    }

    public static void syncRedirect(Unit unit, float x, float y){
        if(Vars.net.server()) Call.clientPacketReliable(packet, "redirect," + unit.id + "," + x + "," + y);
    }

    private static SpaceJumpAbility find(Unit unit){
        if(!allowed(unit) || unit.dead || !unit.isAdded()) return null;
        for(Ability ability : unit.abilities){
            if(ability instanceof SpaceJumpAbility) return (SpaceJumpAbility)ability;
        }
        return null;
    }

    private static void manualJump(Player player, float x, float y){
        if(!manualEnabled() || player == null || player.dead()) return;
        Unit unit = player.unit();
        if(unit == null || unit.getPlayer() != player || unit.team != player.team()) return;
        SpaceJumpAbility ability = find(unit);
        if(ability != null && ability.jump(unit, x, y)){
            Unit previous = manualUnits.put(player.id, unit);
            if(previous != null && previous != unit){
                SpaceJumpAbility old = find(previous);
                if(old != null) old.cancelManual(previous);
            }
        }
    }
}

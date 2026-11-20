package cryon.Type;

import cryon.Features.CryonContent;
import cryon.Type.Constructor.ConstructorCrafter;
import mindustry.type.Liquid;

/** Resolves HJSON-defined tritium after all mod content has been registered. */
public class SuperphaseSynthesizer extends ConstructorCrafter{
    public float tritiumUse = 6f / 60f;
    public SuperphaseSynthesizer(String name){ super(name); }
    @Override public void init(){
        Liquid tritium = CryonContent.liquid("tritium");
        if(tritium == null) throw new IllegalStateException("Superphase synthesizer requires tritium content");
        consumeLiquid(tritium, tritiumUse);
        super.init();
    }
}

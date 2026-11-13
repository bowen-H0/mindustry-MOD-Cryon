import arc.Application;
import arc.Core;
import arc.Settings;
import arc.audio.Music;
import arc.audio.AudioSource;
import arc.audio.Soloud;
import arc.files.Fi;
import arc.files.ZipFi;
import arc.mock.MockFiles;
import arc.util.ArcNativesLoader;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

/** Decode the actual packaged tracks with the installed game's native audio library. */
public class CampaignMusicJarCheck{
    public static void main(String[] args) throws Exception{
        Core.files = new MockFiles();
        Core.settings = new Settings();
        Core.settings.setDataDirectory(Fi.get(args[1]));
        Core.app = (Application)Proxy.newProxyInstance(Application.class.getClassLoader(),
                new Class<?>[]{Application.class}, (proxy, method, values) -> {
                    if(method.getReturnType() == boolean.class) return false;
                    if(method.getReturnType() == int.class) return 0;
                    return null;
                });
        ArcNativesLoader.load();
        Method load = Class.forName("cryon.Features.CampaignMusic")
                .getDeclaredMethod("loadTrack", Fi.class, String.class);
        load.setAccessible(true);
        Field handle = AudioSource.class.getDeclaredField("handle");
        handle.setAccessible(true);
        Method length = Soloud.class.getDeclaredMethod("streamLength", long.class);
        length.setAccessible(true);
        ZipFi root = new ZipFi(Fi.get(args[0]));
        for(String name : new String[]{"cryon-src-000.ogg", "cryon-ultimatum.ogg"}){
            Music music = (Music)load.invoke(null, root, name);
            try{
                // Inspect the native stream directly, without opening an audio device.
                double seconds = (Double)length.invoke(null, handle.getLong(music));
                if(!music.valid() || seconds < 100d){
                    throw new AssertionError("BGM did not decode: " + name);
                }
                System.out.println("BGM_JAR_DECODE_PASS: " + name + " (" + seconds + "s)");
            }finally{
                music.dispose();
            }
        }
    }
}

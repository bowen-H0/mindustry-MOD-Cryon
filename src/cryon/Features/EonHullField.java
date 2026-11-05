package cryon.Features;

import arc.graphics.Pixmap;
import arc.graphics.g2d.PixmapRegion;
import arc.math.Mathf;

/** CPU-only, exact Euclidean distance field built while createIcons owns the source pixels. */
public final class EonHullField{
    public static final int padding = 64;
    public static final float distanceRange = 96f;

    public static Pixmap create(PixmapRegion source){
        int width = source.width + padding * 2, height = source.height + padding * 2;
        boolean[] inside = new boolean[width * height];
        for(int y = 0; y < source.height; y++) for(int x = 0; x < source.width; x++)
            inside[(y + padding) * width + x + padding] = source.getA(x, y) >= 100;
        float[] toInside = distance(inside, width, height, true);
        float[] toOutside = distance(inside, width, height, false);
        Pixmap field = new Pixmap(width, height);
        for(int y = 0; y < height; y++) for(int x = 0; x < width; x++){
            int index = y * width + x;
            float signed = inside[index] ? Mathf.sqrt(toOutside[index]) - 0.5f
                : 0.5f - Mathf.sqrt(toInside[index]);
            int value = Math.round(Mathf.clamp(0.5f + signed / (distanceRange * 2f)) * 255f);
            // Opaque alpha keeps the atlas packer from trimming the exterior glow margin.
            field.set(x, y, (value << 24) | 0x000000ff);
        }
        return field;
    }

    private static float[] distance(boolean[] inside, int width, int height, boolean target){
        float[] result = new float[width * height];
        int length = Math.max(width, height);
        float[] input = new float[length], output = new float[length], breaks = new float[length + 1];
        int[] sites = new int[length];
        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++) input[x] = inside[y * width + x] == target ? 0f : 1e12f;
            transform(input, output, sites, breaks, width);
            System.arraycopy(output, 0, result, y * width, width);
        }
        for(int x = 0; x < width; x++){
            for(int y = 0; y < height; y++) input[y] = result[y * width + x];
            transform(input, output, sites, breaks, height);
            for(int y = 0; y < height; y++) result[y * width + x] = output[y];
        }
        return result;
    }

    private static void transform(float[] input, float[] output, int[] sites, float[] breaks, int size){
        int k = 0;
        sites[0] = 0;
        breaks[0] = Float.NEGATIVE_INFINITY;
        breaks[1] = Float.POSITIVE_INFINITY;
        for(int q = 1; q < size; q++){
            float split;
            do{
                int site = sites[k];
                split = (float)(((double)input[q] + (double)q * q
                    - input[site] - (double)site * site) / (2d * (q - site)));
                if(split > breaks[k]) break;
                k--;
            }while(k >= 0);
            k++;
            sites[k] = q;
            breaks[k] = split;
            breaks[k + 1] = Float.POSITIVE_INFINITY;
        }
        k = 0;
        for(int q = 0; q < size; q++){
            while(breaks[k + 1] < q) k++;
            float delta = q - sites[k];
            output[q] = delta * delta + input[sites[k]];
        }
    }
}

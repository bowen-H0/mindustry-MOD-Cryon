package cryon.Content;

import cryon.Type.TerraTurret;

public final class TerraDefense{
    public static TerraTurret terra;

    public static void load(){
        terra = new TerraTurret("terra");
    }
}

package cryon.Type;

import mindustry.Vars;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Bullet;
import mindustry.world.Tile;

/** Water-only projectile that detonates on the water side of a shoreline. */
public class TorpedoBulletType extends BasicBulletType{
    public TorpedoBulletType(float speed, float damage){
        super(speed, damage);
        underwater = true;
        collidesAir = false;
        // Handle shorelines here so the blast is placed at the edge, not on a land tile.
        collideFloor = false;
    }

    @Override
    public void update(Bullet b){
        float dx = b.x - b.lastX, dy = b.y - b.lastY;
        float size = Vars.tilesize, half = size / 2f;
        int x = (int)Math.floor((b.lastX + half) / size);
        int y = (int)Math.floor((b.lastY + half) / size);
        int sx = dx > 0f ? 1 : -1, sy = dy > 0f ? 1 : -1;
        float tx = dx == 0f ? Float.POSITIVE_INFINITY : (x * size + sx * half - b.lastX) / dx;
        float ty = dy == 0f ? Float.POSITIVE_INFINITY : (y * size + sy * half - b.lastY) / dy;
        float stepX = dx == 0f ? Float.POSITIVE_INFINITY : size / Math.abs(dx);
        float stepY = dy == 0f ? Float.POSITIVE_INFINITY : size / Math.abs(dy);
        float entered = 0f;
        // Traverse every crossed tile, including narrow shores crossed within one tick.
        while(entered <= 1f){
            Tile tile = Vars.world.tile(x, y);
            if(tile == null || !tile.floor().isLiquid){
                float length = (float)Math.sqrt(dx * dx + dy * dy);
                float waterSide = Math.max(0f, entered - (length == 0f ? 0f : 0.01f / length));
                b.set(b.lastX + dx * waterSide, b.lastY + dy * waterSide);
                b.hit = true;
                hit(b, b.x, b.y);
                b.remove();
                return;
            }
            if(tx == Float.POSITIVE_INFINITY && ty == Float.POSITIVE_INFINITY) break;
            if(tx < ty){
                entered = tx;
                tx += stepX;
                x += sx;
            }else if(ty < tx){
                entered = ty;
                ty += stepY;
                y += sy;
            }else{
                entered = tx;
                tx += stepX;
                ty += stepY;
                x += sx;
                y += sy;
            }
        }
        super.update(b);
    }
}

package ua.lewandivka.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Невеликий помічник для будування структур кодом (координати відносно origin). */
public class Builder {
    public final ServerWorld world;
    public final BlockPos origin;

    public Builder(ServerWorld world, BlockPos origin) {
        this.world = world;
        this.origin = origin.toImmutable();
    }

    public BlockPos at(int x, int y, int z) {
        return origin.add(x, y, z);
    }

    public void set(int x, int y, int z, BlockState s) {
        world.setBlockState(at(x, y, z), s, Block.NOTIFY_LISTENERS);
    }

    public BlockState get(int x, int y, int z) {
        return world.getBlockState(at(x, y, z));
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    /** Лише стіни (периметр) прямокутника. */
    public void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) {
                set(x, y, z1, s);
                set(x, y, z2, s);
            }
            for (int z = z1; z <= z2; z++) {
                set(x1, y, z, s);
                set(x2, y, z, s);
            }
        }
    }

    /** Очищає простір над y=0 і підсипає фундамент під y=0, щоб будова не висіла над водою чи прірвою. */
    public void prepare(int rx, int rz, int height, BlockState foundation) {
        for (int x = -rx; x <= rx; x++) {
            for (int z = -rz; z <= rz; z++) {
                for (int y = 1; y <= height; y++) {
                    set(x, y, z, Blocks.AIR.getDefaultState());
                }
                set(x, 0, z, foundation);
                for (int y = -1; y > -40; y--) {
                    BlockState s = get(x, y, z);
                    if (s.isAir() || !s.getFluidState().isEmpty() || s.isReplaceable()) {
                        set(x, y, z, foundation);
                    } else {
                        break;
                    }
                }
            }
        }
    }
}

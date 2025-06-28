package melonslise.locks.mixin.compat.carryon;

import java.util.function.BiFunction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import tschipp.carryon.common.carry.PickupHandler;

@Mixin(PickupHandler.class)
public class CarryOnMixin {
    @Inject(method = "tryPickUpBlock", at=@At("HEAD"), cancellable = true)
    private static void tryPickUpBlock(ServerPlayer player, BlockPos pos, Level level, BiFunction<BlockState, BlockPos, Boolean> pickupCallback, CallbackInfoReturnable<Boolean> info) {
        if (LocksUtil.locked(level, pos)){
            info.setReturnValue(false);
        }
    }
}

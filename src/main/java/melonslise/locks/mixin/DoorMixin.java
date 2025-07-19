package melonslise.locks.mixin;

import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DoorBlock.class)
public class DoorMixin {

    // Intercepta el método use() que se ejecuta ANTES del procesamiento de apertura
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void preventUseWhenLocked(BlockState pState, Level pLevel, BlockPos pPos,
                                      Player pPlayer, InteractionHand pHand, BlockHitResult pHit,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        // Si la puerta está bloqueada, cancelamos el use() completamente
        // Pero dejamos que LocksEvents maneje la lógica completa
        if (LocksUtil.locked(pLevel, pPos)) {
            cir.setReturnValue(InteractionResult.PASS); // PASS permite que LocksEvents procese
        }
    }

    // Mantén este como respaldo para casos edge (redstone, pistons, etc)
    @Inject(method = "setOpen", at = @At("HEAD"), cancellable = true)
    private void cancelOpen(Entity pEntity, Level pLevel, BlockState pState, BlockPos pPos, boolean pOpen, CallbackInfo ci) {
        if (LocksUtil.locked(pLevel, pPos)) {
            ci.cancel();
        }
    }
}
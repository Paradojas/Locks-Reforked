package melonslise.locks.mixin;


import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.spongepowered.asm.mixin.Shadow;
import org.jetbrains.annotations.Nullable;
import melonslise.locks.Locks;

@SuppressWarnings("DataFlowIssue")
@Mixin(RandomizableContainerBlockEntity.class)
public class RandomizableContainerBlockEntityMixin
{
    /*avoiding the Supplementaries error with the hourglass.
    @Shadow @Nullable protected Level level;*/

    @Inject(method = "getItem(I)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "RETURN"), cancellable = true)
    private void lockRandomizableContainerBlockEntity(int slot, CallbackInfoReturnable<ItemStack> cir) {

        //Fixing some supplementaries hourglass generation bug 
        BlockPos pos = ((BaseContainerBlockEntity) (Object) this).getBlockPos();
        Level level = ((BaseContainerBlockEntity) (Object) this).getLevel();
        if (level == null)
        {
            Locks.LOGGER.warn("Tried to access RandomizableContainerBlockEntity before level was initialized.");
            return;
        }
        if (level.isClientSide) return;
        if (LocksUtil.locked(level, pos)){
            cir.setReturnValue(Items.AIR.getDefaultInstance());
        }
    }
}

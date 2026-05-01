package melonslise.locks.mixin;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksGenContext;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings("DataFlowIssue")
@Mixin(RandomizableContainerBlockEntity.class)
public class RandomizableContainerBlockEntityMixin {

    /**
     * setLootTable in 1.20.1 Mojmap is a static utility method:
     *   setLootTable(BlockGetter world, RandomSource rng, BlockPos pos, ResourceLocation lootTable)
     *
     * Called by IglooPiece and OceanRuinPiece (and others) after placeInWorld()
     * to assign a loot table directly on the block entity.
     *
     * Since it's static we inject with a static callback. The BlockPos and
     * ResourceLocation are direct parameters so no NBT reading needed.
     */
    @Inject(
            method = "setLootTable(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Lnet/minecraft/resources/ResourceLocation;)V",
            at = @At("HEAD")
    )
    private static void onSetLootTable(BlockGetter pLevel, RandomSource pRandom, BlockPos pPos, ResourceLocation pLootTable, CallbackInfo ci) {
        if (!(pLevel instanceof ServerLevelAccessor levelAccessor)) return;

        // DEBUG PRINT 1
        Locks.LOGGER.info("[Locks-Debug] setLootTable triggered for {} at {}", pLootTable, pPos);

        boolean isGenerating = levelAccessor instanceof net.minecraft.server.level.WorldGenRegion
                || LocksGenContext.IS_PLACING_STRUCTURE.get();

        if (!isGenerating) {
            // If this prints, your structure isn't being recognized as "generating"
            // Locks.LOGGER.info("[Locks-Debug] Skipped: Not in generation context");
            return;
        }

        ServerLevel serverLevel;
        try {
            serverLevel = levelAccessor.getLevel();
        } catch (Exception e) {
            Locks.LOGGER.error("[Locks-Debug] Failed to get ServerLevel");
            return;
        }

        // DEBUG PRINT 2: Check if the block is even allowed to be locked
        net.minecraft.world.level.block.Block block = levelAccessor.getBlockState(pPos).getBlock();
        if (!melonslise.locks.common.config.LocksConfig.matchString(block)) {
            Locks.LOGGER.warn("[Locks-Debug] Block {} ignored by config regex", block);
            return;
        }

        Lockable lkb = LocksUtil.lockWhenGen(levelAccessor, serverLevel, pPos, pRandom, pLootTable);

        if (lkb == null) {
            Locks.LOGGER.warn("[Locks-Debug] lockWhenGen returned NULL (Check LootTable config!)");
            return;
        }

        try {
            lkb.bb.getContainedChunks((x, z) -> {
                ((ILockableProvider) levelAccessor.getChunk(x, z)).getLockables().add(lkb);
                Locks.LOGGER.info("[Locks-Debug] SUCCESS! Added lock to chunk {}, {}", x, z);
                return true;
            });
        } catch (Exception e) {
            Locks.LOGGER.error("[Locks-Debug] Exception adding to chunk: {}", e.getMessage());
        }
    }

    @Inject(method = "getItem(I)Lnet/minecraft/world/item/ItemStack;", at = @At(value = "RETURN"), cancellable = true)
    private void lockRandomizableContainerBlockEntity(int slot, CallbackInfoReturnable<ItemStack> cir) {
        BlockPos pos = ((BaseContainerBlockEntity)(Object)this).getBlockPos();
        Level level  = ((BaseContainerBlockEntity)(Object)this).getLevel();
        if (level == null) {
            Locks.LOGGER.warn("Tried to access RandomizableContainerBlockEntity before level was initialized.");
            return;
        }
        if (level.isClientSide) return;
        if (LocksUtil.locked(level, pos)) {
            cir.setReturnValue(Items.AIR.getDefaultInstance());
        }
    }
}
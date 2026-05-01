package melonslise.locks.mixin;

import melonslise.locks.Locks;
import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.util.ILockableProvider;
import melonslise.locks.common.util.Lockable;
import melonslise.locks.common.util.LocksUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
            at = @At("TAIL")
    )
    private static void onSetLootTable(BlockGetter world, RandomSource rng,
                                       BlockPos pos, ResourceLocation lootTable,
                                       CallbackInfo ci) {
        try {
            if (!(world instanceof ServerLevel serverLevel)) return;
            if (serverLevel.isClientSide()) return;

            // hasChunk() false = ProtoChunk/worldgen context
            if (serverLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;

            if (!LocksConfig.matchString(serverLevel.getBlockState(pos).getBlock())) return;

            // Dedup
            try {
                ChunkAccess chunk = serverLevel.getChunk(pos);
                if (((ILockableProvider) chunk).getLockables().stream()
                        .anyMatch(lkb -> lkb.bb.intersects(pos))) return;
            } catch (Exception ignored) {}

            Lockable lkb = LocksUtil.lockWhenGen(serverLevel, serverLevel, pos,
                    RandomSource.create(), lootTable);
            if (lkb == null) return;

            lkb.bb.getContainedChunks((x, z) -> {
                ((ILockableProvider) serverLevel.getChunk(x, z)).getLockables().add(lkb);
                return true;
            });
        } catch (Exception e) {
            Locks.LOGGER.error("[RCBEMixin] Exception in onSetLootTable at {}: {}", pos, e.getMessage());
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
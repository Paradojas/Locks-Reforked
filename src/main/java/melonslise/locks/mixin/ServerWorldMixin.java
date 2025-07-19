package melonslise.locks.mixin;

import melonslise.locks.common.components.interfaces.ILockableHandler;
import melonslise.locks.common.config.LocksConfig;
import melonslise.locks.common.init.LocksComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public class ServerWorldMixin {

	@Inject(at = @At("HEAD"), method = "sendBlockUpdated")
	private void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flag, CallbackInfo ci) {
		// 1. If the block type itself hasn't changed (e.g., door opening/closing), skip dropping locks
		if (oldState.getBlock() == newState.getBlock()) {
			return;
		}
		// 2. Maintain previous behavior: skip if both blocks are recognized lockable types
		if (LocksConfig.matchString(oldState.getBlock()) && LocksConfig.matchString(newState.getBlock())) {
			return;
		}

		// Proceed with dropping locks for actual block replacements or removals
		ServerLevel world = (ServerLevel) (Object) this;
		ILockableHandler handler = LocksComponents.LOCKABLE_HANDLER.get(world);
		handler.getInChunk(pos).values().stream()
				.filter(lkb -> lkb.bb.intersects(pos))
				.toList()
				.forEach(lkb -> {
					// Play sound and drop the lock item
					world.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f,
							0.8f + world.random.nextFloat() * 0.4f);
					world.addFreshEntity(new ItemEntity(world,
							pos.getX() + 0.5d,
							pos.getY() + 0.5d,
							pos.getZ() + 0.5d,
							lkb.stack));
					handler.remove(lkb.id);
				});
	}
}

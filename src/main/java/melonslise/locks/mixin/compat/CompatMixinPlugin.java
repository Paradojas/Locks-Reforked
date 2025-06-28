package melonslise.locks.mixin.compat;

import java.util.Set;
import java.util.List;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import melonslise.locks.Locks;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.util.log.LogCategory;
import net.fabricmc.loader.impl.util.log.Log;

public class CompatMixinPlugin implements IMixinConfigPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        var id = mixinClassName.substring(30);
        id = id.substring(0, id.indexOf("."));
        if (FabricLoader.getInstance().isModLoaded(id)) {
            Log.info(LogCategory.MIXIN, "Enabling "+id+" compat for Locks");
            return true; 
        }
        Log.info(LogCategory.MIXIN, "skipping "+id+" compat for Locks");
        return false;
    }

    // Boilerplate

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
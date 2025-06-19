package melonslise.locks.compat.rei;

import melonslise.locks.common.init.LocksItemTags;
import melonslise.locks.common.init.LocksItems;
import net.fabricmc.loader.api.FabricLoader;

import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.plugin.common.displays.anvil.AnvilRecipe;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.network.chat.Component;
import me.shedaniel.rei.api.client.gui.widgets.Tooltip;
import net.minecraft.world.item.crafting.Ingredient;

public class REIIntegration {
    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded("rei");
    }

    public static void registerDisplays(DisplayRegistry registry) {
        ItemStack leftStack = new ItemStack(LocksItems.NETHERITE_LOCK);
        ItemStack rightStack = new ItemStack(LocksItems.INTEGRATED_CIRCUIT);
        ItemStack outputStack = new ItemStack(LocksItems.NETHERITE_SMART_LOCK);


        // Create recipe with proper parameters
        AnvilRecipe recipe = new AnvilRecipe(
                new ResourceLocation("locks", "netherite_smart_lock_recipe"),
                Collections.singletonList(leftStack),
                Collections.singletonList(rightStack),
                Collections.singletonList(outputStack)
        );

        AnvilRecipe recipe2 = new AnvilRecipe(
                new ResourceLocation("locks", "copy_lock"),
                Arrays.asList(Ingredient.of(LocksItemTags.LOCKS).getItems()),
                Arrays.asList(Ingredient.of(LocksItemTags.LOCKS).getItems()),
                Arrays.asList(Ingredient.of(LocksItemTags.LOCKS).getItems())
        );


        registry.add(recipe);
        registry.add(recipe2);
    }
}

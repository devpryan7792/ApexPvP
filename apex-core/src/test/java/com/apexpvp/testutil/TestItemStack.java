package com.apexpvp.testutil;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

public class TestItemStack extends ItemStack {
    private Material material;
    private int amount;
    private final Map<Enchantment, Integer> enchantments = new HashMap<>();
    private ItemMeta meta;

    public TestItemStack(Material material, int amount) {
        super();
        this.material = material != null ? material : Material.AIR;
        this.amount = amount;
    }

    @Override
    public Material getType() {
        return material;
    }

    @Override
    public void setType(Material type) {
        this.material = type;
    }

    @Override
    public int getAmount() {
        return amount;
    }

    @Override
    public void setAmount(int amount) {
        this.amount = amount;
    }

    @Override
    public boolean hasItemMeta() {
        return meta != null;
    }

    @Override
    public ItemMeta getItemMeta() {
        if (meta == null) {
            if (material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) {
                PotionMeta pMeta = Mockito.mock(PotionMeta.class);
                final PotionType[] currentType = new PotionType[]{PotionType.WATER};
                Mockito.lenient().doAnswer(inv -> {
                    currentType[0] = inv.getArgument(0);
                    return null;
                }).when(pMeta).setBasePotionType(Mockito.any());
                Mockito.lenient().doAnswer(inv -> currentType[0]).when(pMeta).getBasePotionType();
                meta = pMeta;
            } else {
                meta = Mockito.mock(ItemMeta.class);
            }
        }
        return meta;
    }

    @Override
    public boolean setItemMeta(@Nullable ItemMeta itemMeta) {
        this.meta = itemMeta;
        return true;
    }

    @Override
    public void addUnsafeEnchantment(@NotNull Enchantment ench, int level) {
        enchantments.put(ench, level);
    }

    @Override
    public int getEnchantmentLevel(@NotNull Enchantment ench) {
        return enchantments.getOrDefault(ench, 0);
    }

    @Override
    public @NotNull Map<Enchantment, Integer> getEnchantments() {
        return enchantments;
    }

    @Override
    public boolean containsEnchantment(@NotNull Enchantment ench) {
        return enchantments.containsKey(ench);
    }

    @Override
    public ItemStack clone() {
        TestItemStack copy = new TestItemStack(this.material, this.amount);
        copy.enchantments.putAll(this.enchantments);
        copy.meta = this.meta;
        return copy;
    }

    @Override
    public boolean isSimilar(@Nullable ItemStack stack) {
        if (stack == null) return false;
        return stack.getType() == this.material;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemStack other)) return false;
        return other.getType() == this.material && other.getAmount() == this.amount;
    }

    @Override
    public int hashCode() {
        return material.hashCode() * 31 + amount;
    }
}

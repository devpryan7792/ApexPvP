package com.apexpvp.testutil;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

public class TestRegistryAccess implements RegistryAccess {

    @Override
    public <T extends Keyed> @NotNull Registry<T> getRegistry(@NotNull Class<T> aClass) {
        return new DummyRegistry<>(aClass, null);
    }

    @Override
    public <T extends Keyed> @NotNull Registry<T> getRegistry(@NotNull RegistryKey<T> registryKey) {
        return new DummyRegistry<>(null, registryKey.key().asString());
    }

    private static class DummyRegistry<T extends Keyed> implements Registry<T> {
        private final Class<T> clazz;
        private final String keyString;

        public DummyRegistry(Class<T> clazz, String keyString) {
            this.clazz = clazz;
            this.keyString = keyString;
        }

        private boolean isItemRegistry() {
            return (clazz != null && ItemType.class.isAssignableFrom(clazz))
                    || ("minecraft:item".equals(keyString));
        }

        private boolean isEnchantmentRegistry() {
            return (clazz != null && Enchantment.class.isAssignableFrom(clazz))
                    || ("minecraft:enchantment".equals(keyString));
        }

        private boolean isAttributeRegistry() {
            return (clazz != null && org.bukkit.attribute.Attribute.class.isAssignableFrom(clazz))
                    || ("minecraft:attribute".equals(keyString));
        }

        @Override
        @SuppressWarnings("unchecked")
        public @Nullable T get(@NotNull NamespacedKey namespacedKey) {
            if (isItemRegistry()) {
                String matName = namespacedKey.getKey().toUpperCase();
                Material mat = Material.matchMaterial(matName);
                if (mat == null) {
                    mat = Material.matchMaterial(namespacedKey.asString());
                }
                final Material finalMat = mat != null ? mat : Material.AIR;
                ItemType proxy = (ItemType) Proxy.newProxyInstance(
                        ItemType.class.getClassLoader(),
                        new Class<?>[]{ItemType.class, ItemType.Typed.class},
                        new InvocationHandler() {
                            @Override
                            public Object invoke(Object proxy, Method method, Object[] args) {
                                String mName = method.getName();
                                if ("getKey".equals(mName)) return namespacedKey;
                                if ("key".equals(mName)) return namespacedKey;
                                if ("asMaterial".equals(mName)) return finalMat;
                                if ("createItemStack".equals(mName)) {
                                    int amount = (args != null && args.length > 0 && args[0] instanceof Number)
                                            ? ((Number) args[0]).intValue() : 1;
                                    return new TestItemStack(finalMat, amount);
                                }
                                if ("toString".equals(mName)) return namespacedKey.toString();
                                if ("equals".equals(mName)) return args != null && args.length > 0 && proxy == args[0];
                                if ("hashCode".equals(mName)) return namespacedKey.hashCode();
                                return null;
                            }
                        }
                );
                return (T) proxy;
            }
            if (isEnchantmentRegistry()) {
                try {
                    Enchantment mock = Mockito.mock(Enchantment.class);
                    Mockito.lenient().when(mock.getKey()).thenReturn(namespacedKey);
                    Mockito.lenient().when(mock.getName()).thenReturn(namespacedKey.getKey().toUpperCase());
                    return (T) mock;
                } catch (Throwable ignored) {}
            }
            if (isAttributeRegistry()) {
                try {
                    org.bukkit.attribute.Attribute mock = Mockito.mock(org.bukkit.attribute.Attribute.class);
                    Mockito.lenient().when(mock.getKey()).thenReturn(namespacedKey);
                    Mockito.lenient().when(mock.key()).thenReturn(namespacedKey);
                    return (T) mock;
                } catch (Throwable ignored) {}
            }
            return null;
        }

        @Override
        public @Nullable NamespacedKey getKey(@NotNull T t) {
            return t.getKey();
        }

        @Override
        public boolean hasTag(@NotNull io.papermc.paper.registry.tag.TagKey<T> tagKey) {
            return false;
        }

        @Override
        public @Nullable io.papermc.paper.registry.tag.Tag<T> getTag(@NotNull io.papermc.paper.registry.tag.TagKey<T> tagKey) {
            return null;
        }

        @Override
        public @NotNull Collection<io.papermc.paper.registry.tag.Tag<T>> getTags() {
            return Collections.emptyList();
        }

        @Override
        public @NotNull Stream<T> stream() {
            return Stream.empty();
        }

        @Override
        public @NotNull Stream<NamespacedKey> keyStream() {
            return Stream.empty();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public @NotNull Iterator<T> iterator() {
            return Collections.emptyIterator();
        }
    }
}

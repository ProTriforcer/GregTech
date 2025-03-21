package gregtech.api.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import gregtech.api.GTValues;
import net.minecraft.util.IntIdentityHashBiMap;
import net.minecraft.util.registry.RegistrySimple;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

// this class should extend RegistryNamespaced but due to
// ForgeGradle bug (https://github.com/MinecraftForge/ForgeGradle/issues/498) it gives compile errors in CI environment
public class GTControlledRegistry<K, V> extends RegistrySimple<K, V> {

    protected boolean frozen = true;
    protected final int maxId;

    public GTControlledRegistry(int maxId) {
        this.maxId = maxId;
        this.inverseObjectRegistry = ((BiMap<K, V>) this.registryObjects).inverse();
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void freeze() {
        if (frozen) {
            throw new IllegalStateException("Registry is already frozen!");
        }

        if (!checkActiveModContainerIsGregtech()) {
            return;
        }

        this.frozen = true;
    }

    public void unfreeze() {
        if (!frozen) {
            throw new IllegalStateException("Registry is already unfrozen!");
        }

        if (!checkActiveModContainerIsGregtech()) {
            return;
        }

        this.frozen = false;
    }

    private boolean checkActiveModContainerIsGregtech() {
        ModContainer container = Loader.instance().activeModContainer();
        if (container != null && container.getModId().equals(GTValues.MODID)) {
            return true;
        }

        return false;
    }

    public void register(int id, K key, V value) {
        if (id < 0 || id >= maxId) {
            throw new IndexOutOfBoundsException("Id is out of range: " + id);
        }

        super.putObject(key, value);

        V objectWithId = getObjectById(id);
        if (objectWithId != null) {
            throw new IllegalArgumentException(String.format("Tried to reassign id %d to %s (%s), but it is already assigned to %s (%s)!",
                    id, value, key, objectWithId, getNameForObject(objectWithId)));
        }
        underlyingIntegerMap.put(value, id);
    }

    @Override
    public void putObject(@Nonnull K key, @Nonnull V value) {
        throw new UnsupportedOperationException("Use #register(int, String, T)");
    }

    public int getIdByObjectName(K key) {
        V valueWithKey = getObject(key);
        return valueWithKey == null ? 0 : getIDForObject(valueWithKey);
    }

//     =================== RegistryNamespaced stuff ===================

    protected final IntIdentityHashBiMap<V> underlyingIntegerMap = new IntIdentityHashBiMap<>(256);
    protected final Map<V, K> inverseObjectRegistry;

    @Nonnull
    @Override
    protected Map<K, V> createUnderlyingMap() {
        return HashBiMap.create();
    }

    @Nullable
    public K getNameForObject(V value) {
        return this.inverseObjectRegistry.get(value);
    }

    public int getIDForObject(@Nullable V value) {
        return this.underlyingIntegerMap.getId(value);
    }

    @Nullable
    public V getObjectById(int id) {
        return this.underlyingIntegerMap.get(id);
    }

    @Nonnull
    @Override
    public Iterator<V> iterator() {
        return this.underlyingIntegerMap.iterator();
    }
}

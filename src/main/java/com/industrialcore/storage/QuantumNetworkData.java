package com.industrialcore.storage;

import com.industrialcore.blockentity.QuantumStorageBlockEntity.FluidKey;
import com.industrialcore.blockentity.QuantumStorageBlockEntity.ItemKey;
import com.industrialcore.storage.QuantumNetLogic.QuantumChannel;
import com.industrialcore.storage.QuantumNetLogic.QuantumNetworkRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Phase 96: World-persistent {@link SavedData} storing all Quantum Storage frequency
 * channels (items, fluids, node capacity contributions, and public/private ownership) on the
 * Overworld {@code DimensionDataStorage}.
 */
public final class QuantumNetworkData extends SavedData {
    private static final String DATA_NAME = "industrialcore_quantum_network";

    private static final String TAG_NEXT_SEQ = "NextSeq";
    private static final String TAG_CHANNELS = "Channels";
    private static final String TAG_ID = "Id";
    private static final String TAG_NAME = "Name";
    private static final String TAG_OWNER = "Owner";
    private static final String TAG_PUBLIC = "Public";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_FLUIDS = "Fluids";
    private static final String TAG_NODES = "Nodes";
    private static final String TAG_NODE_ID = "NodeId";
    private static final String TAG_CORES = "Cores";
    private static final String TAG_STACK = "Stack";
    private static final String TAG_AMOUNT = "Amount";

    private final QuantumNetworkRegistry<ItemKey, FluidKey> registry = new QuantumNetworkRegistry<>();

    public static QuantumNetworkData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        ServerLevel target = overworld != null ? overworld : level;
        return target.getDataStorage().computeIfAbsent(
                new Factory<>(QuantumNetworkData::new, QuantumNetworkData::load),
                DATA_NAME);
    }

    public QuantumNetworkRegistry<ItemKey, FluidKey> registry() {
        return registry;
    }

    public long globalRevision() {
        return registry.globalRevision();
    }

    public void markMutated() {
        registry.bumpGlobalRevision();
        setDirty();
    }

    @Nullable
    public QuantumChannel<ItemKey, FluidKey> getChannel(@Nullable String channelId) {
        return registry.get(channelId);
    }

    public QuantumChannel<ItemKey, FluidKey> createChannel(String name, String ownerUuid, boolean isPublic) {
        QuantumChannel<ItemKey, FluidKey> ch = registry.createChannel(name, ownerUuid, isPublic);
        setDirty();
        return ch;
    }

    public boolean deleteChannel(String channelId, String requesterUuid) {
        boolean removed = registry.deleteChannel(channelId, requesterUuid);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public boolean togglePublic(String channelId, String requesterUuid) {
        boolean ok = registry.togglePublic(channelId, requesterUuid);
        if (ok) {
            setDirty();
        }
        return ok;
    }

    public void updateNode(String channelId, ServerLevel level, BlockPos pos, StorageCoreTier[] cores) {
        String nodeId = QuantumNetLogic.makeNodeId(
                level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        registry.removeNodeFromAll(nodeId);
        if (channelId != null && !channelId.isEmpty()) {
            registry.updateNode(channelId, nodeId, cores);
        }
        setDirty();
    }

    public void removeNode(ServerLevel level, BlockPos pos) {
        String nodeId = QuantumNetLogic.makeNodeId(
                level.dimension().location().toString(), pos.getX(), pos.getY(), pos.getZ());
        registry.removeNodeFromAll(nodeId);
        setDirty();
    }

    public List<QuantumChannel<ItemKey, FluidKey>> visibleChannelsFor(String requesterUuid) {
        return registry.visibleChannelsFor(requesterUuid);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_NEXT_SEQ, registry.nextSeq());
        ListTag chList = new ListTag();
        for (QuantumChannel<ItemKey, FluidKey> ch : registry.allChannels()) {
            CompoundTag ct = new CompoundTag();
            ct.putString(TAG_ID, ch.channelId());
            ct.putString(TAG_NAME, ch.name());
            ct.putString(TAG_OWNER, ch.ownerUuid());
            ct.putBoolean(TAG_PUBLIC, ch.isPublic());

            ListTag itemList = new ListTag();
            for (Map.Entry<ItemKey, BigInteger> e : ch.storage().itemsView().entrySet()) {
                if (e.getValue().signum() <= 0) {
                    continue;
                }
                CompoundTag it = new CompoundTag();
                it.put(TAG_STACK, e.getKey().toStack(1).saveOptional(registries));
                it.putString(TAG_AMOUNT, e.getValue().toString());
                itemList.add(it);
            }
            ct.put(TAG_ITEMS, itemList);

            ListTag fluidList = new ListTag();
            for (Map.Entry<FluidKey, BigInteger> e : ch.storage().fluidsView().entrySet()) {
                if (e.getValue().signum() <= 0) {
                    continue;
                }
                CompoundTag ft = new CompoundTag();
                ft.put(TAG_STACK, e.getKey().toStack(1).saveOptional(registries));
                ft.putString(TAG_AMOUNT, e.getValue().toString());
                fluidList.add(ft);
            }
            ct.put(TAG_FLUIDS, fluidList);

            ListTag nodeList = new ListTag();
            for (Map.Entry<String, StorageCoreTier[]> ne : ch.nodesView().entrySet()) {
                CompoundTag nt = new CompoundTag();
                nt.putString(TAG_NODE_ID, ne.getKey());
                ListTag coreIds = new ListTag();
                StorageCoreTier[] slots = ne.getValue();
                for (int i = 0; i < StorageCoreTier.CORE_SLOT_COUNT; i++) {
                    StorageCoreTier tier = (slots != null && i < slots.length) ? slots[i] : null;
                    coreIds.add(StringTag.valueOf(tier != null ? tier.id() : ""));
                }
                nt.put(TAG_CORES, coreIds);
                nodeList.add(nt);
            }
            ct.put(TAG_NODES, nodeList);
            chList.add(ct);
        }
        tag.put(TAG_CHANNELS, chList);
        return tag;
    }

    private static QuantumNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuantumNetworkData data = new QuantumNetworkData();
        data.registry.setNextSeq(Math.max(1, tag.getInt(TAG_NEXT_SEQ)));
        ListTag chList = tag.getList(TAG_CHANNELS, Tag.TAG_COMPOUND);
        for (int i = 0; i < chList.size(); i++) {
            CompoundTag ct = chList.getCompound(i);
            String id = ct.getString(TAG_ID);
            if (id.isEmpty()) {
                continue;
            }
            QuantumChannel<ItemKey, FluidKey> ch = new QuantumChannel<>(
                    id, ct.getString(TAG_NAME), ct.getString(TAG_OWNER), ct.getBoolean(TAG_PUBLIC));

            ListTag nodeList = ct.getList(TAG_NODES, Tag.TAG_COMPOUND);
            for (int n = 0; n < nodeList.size(); n++) {
                CompoundTag nt = nodeList.getCompound(n);
                String nodeId = nt.getString(TAG_NODE_ID);
                if (nodeId.isEmpty()) {
                    continue;
                }
                StorageCoreTier[] slots = new StorageCoreTier[StorageCoreTier.CORE_SLOT_COUNT];
                ListTag coreIds = nt.getList(TAG_CORES, Tag.TAG_STRING);
                for (int c = 0; c < Math.min(slots.length, coreIds.size()); c++) {
                    slots[c] = StorageCoreTier.byId(coreIds.getString(c));
                }
                ch.updateNode(nodeId, slots);
            }

            ListTag itemList = ct.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
            for (int k = 0; k < itemList.size(); k++) {
                CompoundTag it = itemList.getCompound(k);
                ItemStack proto = ItemStack.parseOptional(registries, it.getCompound(TAG_STACK));
                BigInteger amt = parseBigInt(it.getString(TAG_AMOUNT));
                if (!proto.isEmpty() && amt.signum() > 0) {
                    ch.storage().putItemUnchecked(ItemKey.of(proto), amt);
                }
            }

            ListTag fluidList = ct.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
            for (int k = 0; k < fluidList.size(); k++) {
                CompoundTag ft = fluidList.getCompound(k);
                FluidStack proto = FluidStack.parseOptional(registries, ft.getCompound(TAG_STACK));
                BigInteger amt = parseBigInt(ft.getString(TAG_AMOUNT));
                if (!proto.isEmpty() && amt.signum() > 0) {
                    ch.storage().putFluidUnchecked(FluidKey.of(proto), amt);
                }
            }

            data.registry.putChannelUnchecked(ch);
        }
        return data;
    }

    private static BigInteger parseBigInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigInteger.ZERO;
        }
        try {
            BigInteger v = new BigInteger(raw.trim());
            return v.signum() > 0 ? v : BigInteger.ZERO;
        } catch (NumberFormatException ex) {
            return BigInteger.ZERO;
        }
    }
}

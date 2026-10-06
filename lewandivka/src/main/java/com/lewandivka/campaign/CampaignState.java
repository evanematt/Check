package com.lewandivka.campaign;

import com.lewandivka.core.campaign.CampaignModel;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.PersistentState;

/** The saved campaign: world-wide progress and the progress of every player, in {@code data/lewandivka_campaign.dat}. */
public final class CampaignState extends PersistentState {

    public static final String KEY = "lewandivka_campaign";

    private CampaignModel model = new CampaignModel();

    public static CampaignState fromNbt(NbtCompound nbt) {
        CampaignState state = new CampaignState();
        state.model = CampaignModel.read(new NbtStore(nbt));
        return state;
    }

    public CampaignModel model() {
        return model;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        model.write(new NbtStore(nbt));
        return nbt;
    }
}

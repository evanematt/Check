package ua.lewandivka.logic;

import net.minecraft.advancement.Advancement;
import net.minecraft.server.network.ServerPlayerEntity;
import ua.lewandivka.Lewandivka;

public final class Advancements {
    public static void grant(ServerPlayerEntity player, String name) {
        Advancement adv = player.getServer().getAdvancementLoader().get(Lewandivka.id(name));
        if (adv != null) {
            player.getAdvancementTracker().grantCriterion(adv, "trigger");
        }
    }

    private Advancements() {
    }
}

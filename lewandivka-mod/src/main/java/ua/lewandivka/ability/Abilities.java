package ua.lewandivka.ability;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import ua.lewandivka.logic.LewState;
import ua.lewandivka.network.ModNetworking;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Постійні здібності гравця (зберігаються в LewState, а не в предметах).
 * Сервер авторитетний: клієнт лише просить «спробувати», сервер перевіряє наявність, кулдаун і стан.
 */
public final class Abilities {
    public static final String DASH = "dash";
    public static final String JUMP = "jump";
    public static final String GLIDE = "glide";

    static final int DASH_COOLDOWN = 70;
    static final int GLIDE_MAX_TICKS = 130;
    static final int GLIDE_COOLDOWN = 100;

    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    private static final Map<UUID, Integer> GLIDING = new HashMap<>();

    public static void init() {
        ServerPlayNetworking.registerGlobalReceiver(ModNetworking.ABILITY, (server, player, handler, buf, sender) -> {
            String id = buf.readString(16);
            server.execute(() -> use(player, id));
        });
        ServerTickEvents.END_SERVER_TICK.register(Abilities::tick);
        // Пружні устілки: гасять падіння до 10 блоків.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && source.isOf(DamageTypes.FALL) && amount <= 8.0f
                    && LewState.get(p.getServer()).hasAbility(p.getUuid(), JUMP)) {
                return false;
            }
            return true;
        });
    }

    public static boolean has(ServerPlayerEntity p, String ability) {
        return LewState.get(p.getServer()).hasAbility(p.getUuid(), ability);
    }

    /** Видає здібність за пройденими босами — викликається при вході, щоб офлайн-гравці нічого не втратили. */
    public static void syncOnJoin(ServerPlayerEntity p) {
        LewState st = LewState.get(p.getServer());
        if (st.has("done_garage")) {
            st.grantAbility(p.getUuid(), DASH);
        }
        if (st.has("done_aqua")) {
            st.grantAbility(p.getUuid(), JUMP);
        }
        if (st.has("done_depot")) {
            st.grantAbility(p.getUuid(), GLIDE);
        }
    }

    public static void announce(ServerPlayerEntity p, String ability) {
        String text = switch (ability) {
            case DASH -> "Нова здібність: РИВОК (клавіша V)";
            case JUMP -> "Нова здібність: ПРУЖНІ УСТІЛКИ (пружні люки, м'яке падіння)";
            default -> "Нова здібність: ПЛАНЕР (стрибок у повітрі)";
        };
        p.sendMessage(Text.literal(text).formatted(Formatting.AQUA, Formatting.BOLD), false);
    }

    static void use(ServerPlayerEntity p, String id) {
        if (!p.isAlive() || p.isSpectator() || p.hasVehicle()) {
            return;
        }
        long now = p.getServerWorld().getTime();
        if (READY_AT.getOrDefault(p.getUuid(), 0L) > now) {
            return;
        }
        if (id.equals(DASH) && has(p, DASH)) {
            Vec3d look = p.getRotationVector();
            Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
            p.setVelocity(dir.x * 1.7, p.isOnGround() ? 0.28 : 0.1, dir.z * 1.7);
            p.velocityModified = true;
            p.fallDistance = 0;
            READY_AT.put(p.getUuid(), now + DASH_COOLDOWN);
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1f, 1.6f);
        } else if (id.equals(GLIDE) && has(p, GLIDE) && !p.isOnGround() && !p.isTouchingWater() && !GLIDING.containsKey(p.getUuid())) {
            GLIDING.put(p.getUuid(), GLIDE_MAX_TICKS);
            p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.5f, 1.5f);
        } else if (id.equals(DASH) || id.equals(GLIDE)) {
            p.sendMessage(Text.literal("Ця здібність ще не відкрита.").formatted(Formatting.GRAY), true);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Integer>> it = GLIDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            PlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            int left = e.getValue() - 1;
            if (!(p instanceof ServerPlayerEntity sp) || !sp.isAlive() || sp.isOnGround() || sp.isTouchingWater() || left <= 0) {
                if (p instanceof ServerPlayerEntity sp2) {
                    READY_AT.put(sp2.getUuid(), sp2.getServerWorld().getTime() + GLIDE_COOLDOWN);
                }
                it.remove();
                continue;
            }
            e.setValue(left);
            Vec3d v = sp.getVelocity();
            Vec3d look = sp.getRotationVector();
            sp.setVelocity(v.x * 0.9 + look.x * 0.06, Math.max(v.y, -0.11), v.z * 0.9 + look.z * 0.06);
            sp.velocityModified = true;
            sp.fallDistance = 0;
        }
    }

    private Abilities() {
    }
}

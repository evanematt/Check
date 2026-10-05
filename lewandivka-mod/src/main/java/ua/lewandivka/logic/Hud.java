package ua.lewandivka.logic;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import ua.lewandivka.network.ModNetworking;

import java.util.UUID;

/** Поточне завдання для HUD у лівому верхньому куті. */
public final class Hud {
    static void update(MinecraftServer server) {
        LewState st = LewState.get(server);
        UUID sh = st.boss("shlagbaum");
        if (sh != null) {
            Entity e = server.getOverworld().getEntity(sh);
            if (e instanceof VillagerEntity v) {
                st.set("lvl" + v.getVillagerData().getLevel());
            }
        }
        String[] t = step(st);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(t[0]);
            buf.writeString(t[1]);
            ServerPlayNetworking.send(p, ModNetworking.HUD, buf);
        }
    }

    private static String[] step(LewState st) {
        if (st.has("done_tower")) {
            return new String[]{"Кінець. Котів вигуляли!", "Портал вдома веде назад у Хромандівку — її секрети нікуди не ділись."};
        }
        if (!st.arrived) {
            if (st.has("lvl5")) {
                return new String[]{"5. Хрома", "Шлагбаум дав таблетки. З'їжте Хрому всі троє протягом 5 хвилин."};
            }
            if (st.has("lvl4")) {
                return new String[]{"4. Останній трамвай", "Купи записку, прочитай її біля зупинки (дзвін). Відбий 3 хвилі, забери компостер у Ватажка і віддай Шлагбауму."};
            }
            if (st.has("lvl3")) {
                return new String[]{"3. Гараж № 13", "Посилка — у найдальшому куті лабіринту гаражів на краю району. Вона бурчить і кличе охорону."};
            }
            if (st.has("lvl2")) {
                return new String[]{"2. Боржник", "Боржник ховається біля сірої панельки. Забери чайник (3 смарагди або силою) і віднеси Шлагбауму."};
            }
            return new String[]{"1. Стати своїм", "Збери жетони з гопників біля гаражів і обміняй у Пана Шлагбаума (кіоск на вулиці)."};
        }
        switch (Travel.nextObjective(st)) {
            case "garage":
                return new String[]{"6. Веселковий гараж", "Йди за компасом. Увімкніть три підйомники одночасно — тоді ядро Короля відкрите."};
            case "shelter":
                if (!st.has("shelter_door_open")) {
                    return new String[]{"7. Притулок загублених імен", "Зайди в двері будиночка. Всередині увімкніть три важелі разом."};
                }
                if (!st.has("chinazik_found") || !st.has("metadonna_found")) {
                    return new String[]{"7. Знайти котів", "Виманіть Чіназіка рибою на килимок біля виходу. Метадонна — в найменшій коробці."};
                }
                return new String[]{"7. Колекціонер", "Переможіть Колекціонера Нашийників — і коти стануть вашими."};
            case "aqua":
                return new String[]{"8. Аквапарк сухого озера", "Клацни вівтар. Пані Вирва затоплює чверті арени — тікайте з названої."};
            case "depot":
                return new String[]{"9. Депо над небом", "Трамвайна зупинка везе на арену. Три компостери мають натиснути різні гравці."};
            default:
                return new String[]{"10. Голова району", "Чіназік відчиняє двері, Метадонна — ґрати. На даху бий Голову лише з Кольоровим зарядом."};
        }
    }

    private Hud() {
    }
}

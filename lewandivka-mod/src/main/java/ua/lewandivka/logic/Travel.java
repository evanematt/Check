package ua.lewandivka.logic;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import ua.lewandivka.registry.ModWorldgen;
import ua.lewandivka.world.Sites;

import java.util.ArrayList;
import java.util.List;

/** Таблетка «Хрома», переходи між Левандівкою і Хромандівкою. */
public final class Travel {
    public static final long PILL_WINDOW = 6000; // 5 хвилин

    public static boolean inChroma(ServerPlayerEntity p) {
        return p.getWorld().getRegistryKey().equals(ModWorldgen.CHROMA);
    }

    public static void onPillEaten(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        LewState st = LewState.get(server);
        Advancements.grant(p, "root");
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 240, 0));
        if (inChroma(p)) {
            p.sendMessage(Text.literal("Хрома розчиняється… і тебе викидає назад у двір.").formatted(Formatting.LIGHT_PURPLE), false);
            Scheduler.after(server, 40, () -> sendHome(p));
            return;
        }
        if (st.arrived) {
            p.sendMessage(Text.literal("Кольори розходяться, як завіса. Хромандівка тебе пам'ятає.").formatted(Formatting.LIGHT_PURPLE), false);
            Scheduler.after(server, 60, () -> enterChroma(p));
            return;
        }
        long now = server.getOverworld().getTime();
        st.pillEaters.put(p.getUuid(), now);
        st.pillEaters.entrySet().removeIf(e -> now - e.getValue() > PILL_WINDOW);
        List<ServerPlayerEntity> ready = new ArrayList<>();
        for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
            if (st.pillEaters.containsKey(sp.getUuid())) {
                ready.add(sp);
            }
        }
        int need = Math.min(3, server.getPlayerManager().getCurrentPlayerCount());
        int n = ready.size();
        String line = switch (n) {
            case 1 -> "Ти бачиш кольорові тріщини в повітрі…";
            case 2 -> "Музика довкола звучить інакше…";
            default -> "Будинки складаються, як картонні декорації…";
        };
        server.getPlayerManager().broadcast(Text.literal(p.getName().getString() + " ковтає Хрому. ").formatted(Formatting.LIGHT_PURPLE)
                .append(Text.literal(line).formatted(Formatting.ITALIC))
                .append(Text.literal(" (" + Math.min(n, need) + "/" + need + ")").formatted(Formatting.GRAY)), false);
        if (n < need) {
            p.sendMessage(Text.literal("Одному не працює. Треба, щоб Хрому з'їли всі " + need + " протягом 5 хвилин.").formatted(Formatting.GRAY), true);
            return;
        }
        st.pillEaters.clear();
        server.getPlayerManager().broadcast(Text.literal("…і двір падає в небо!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        for (ServerPlayerEntity sp : ready) {
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 60, 1));
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 90, 0));
            sp.getServerWorld().playSound(null, sp.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.PLAYERS, 0.4f, 1.6f);
        }
        Scheduler.after(server, 70, () -> {
            boolean first = !st.arrived;
            st.arrived = true;
            st.markDirty();
            for (ServerPlayerEntity sp : ready) {
                if (!sp.isRemoved()) {
                    enterChroma(sp);
                }
            }
            if (first) {
                Scheduler.after(server, 40, () -> arrivalStory(server));
            }
        });
    }

    private static void arrivalStory(MinecraftServer server) {
        String[] lines = {
                "Ви стоїте у своєму будинку… але це вже не Левандівка. Рожеве небо, кілька сонць, бірюзова трава.",
                "Котів у будинку немає. У скрині — два нашийники і записка: «Вони вже були тут. Вони знають дорогу.»",
                "Безбарвний Голова краде кольори цього світу. Поверніть Хромандівці колір — і знайдіть котів.",
                "Компас веде до першого данджу — Кооперативу «Веселковий гараж». Портал у будинку повертає додому."
        };
        int delay = 0;
        for (String l : lines) {
            Scheduler.after(server, delay, () -> server.getPlayerManager().broadcast(Text.literal(l).formatted(Formatting.LIGHT_PURPLE), false));
            delay += 60;
        }
    }

    public static void enterChroma(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        ServerWorld chroma = server.getWorld(ModWorldgen.CHROMA);
        if (chroma == null) {
            p.sendMessage(Text.literal("Хромандівка не знайдена — перевір, що мод стоїть і на сервері."), false);
            return;
        }
        LewState st = LewState.get(server);
        Sites.ensureBuilt(chroma, "base");
        if (!inChroma(p)) {
            st.setReturn(p.getUuid(), p.getWorld().getRegistryKey(), p.getBlockPos());
        }
        BlockPos spawn = st.point("base", "spawn");
        if (spawn == null) {
            spawn = chroma.getSpawnPos();
        }
        p.teleport(chroma, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYaw(), 0);
        p.fallDistance = 0;
        chroma.playSound(null, spawn, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2f, 0.8f);
        Advancements.grant(p, "chromandivka");
        if (st.firstVisit(p.getUuid())) {
            give(p, guideBook());
            give(p, Sites.compassTo(chroma, nextObjective(st)));
        }
    }

    /** Наступна мета за прогресом. */
    public static String nextObjective(LewState st) {
        if (!st.has("done_garage")) {
            return "garage";
        }
        if (!st.has("done_shelter")) {
            return "shelter";
        }
        if (!st.has("done_aqua")) {
            return "aqua";
        }
        if (!st.has("done_depot")) {
            return "depot";
        }
        return "tower";
    }

    public static void sendHome(ServerPlayerEntity p) {
        MinecraftServer server = p.getServer();
        LewState.ReturnPoint rp = LewState.get(server).getReturn(p.getUuid());
        ServerWorld w = rp != null ? server.getWorld(rp.world()) : null;
        BlockPos pos;
        if (w == null || w.getRegistryKey().equals(ModWorldgen.CHROMA)) {
            w = server.getOverworld();
            pos = p.getSpawnPointPosition() != null && p.getSpawnPointDimension().equals(w.getRegistryKey())
                    ? p.getSpawnPointPosition().up() : w.getSpawnPos();
        } else {
            pos = rp.pos();
        }
        p.teleport(w, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, p.getYaw(), 0);
        p.fallDistance = 0;
    }

    public static void usePortal(ServerPlayerEntity p) {
        LewState st = LewState.get(p.getServer());
        if (inChroma(p)) {
            p.sendMessage(Text.literal("Портал тягне тебе додому, на Левандівку…").formatted(Formatting.LIGHT_PURPLE), true);
            sendHome(p);
        } else if (!st.arrived) {
            p.sendMessage(Text.literal("Портал мовчить. Схоже, спершу Хрому треба з'їсти втрьох.").formatted(Formatting.GRAY), true);
        } else {
            enterChroma(p);
        }
    }

    public static void give(ServerPlayerEntity p, ItemStack stack) {
        if (!p.giveItemStack(stack)) {
            p.dropItem(stack, false);
        }
    }

    public static ItemStack guideBook() {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        nbt.putString("title", "Путівник по Хромандівці");
        nbt.putString("author", "Пан Шлагбаум");
        nbt.putBoolean("resolved", true);
        String[] pages = {
                "ХРОМАНДІВКА\n\nЦе ваш район, тільки по той бік. Все яскраве, все трохи криве.\n\nБезбарвний Голова краде кольори. Коти теж десь тут.",
                "МАРШРУТ\n\n1. Кооператив «Веселковий гараж» — Гаражний Король.\n2. Притулок загублених імен — коти.\n3. Аквапарк сухого озера — Пані Вирва.\n4. Трамвайне депо над небом — Кондуктор.\n5. Вежа — Голова району.",
                "ПОРАДИ\n\nКомпас завжди веде до наступної мети.\n\nВимикачі на аренах треба тримати увімкненими ОДНОЧАСНО — розбігайтесь утрьох.\n\nВсе, що кипить біля вівтаря, — бос.",
                "КОТИ\n\nПісля Притулку Чіназіка і Метадонну може кликати будь-хто з вас. ПКМ по коту — меню команд.\n\nЧіназік відчиняє двері. Метадонна пролазить куди завгодно.\n\nРиба — найкращий аргумент.",
                "ДОДОМУ\n\nКольоровий портал у будинку повертає на Левандівку. Ще одна Хрома — і ви знову тут.\n\nЯкщо загинете тут — прокинетесь у будинку-базі."
        };
        NbtList list = new NbtList();
        for (String page : pages) {
            list.add(NbtString.of(Text.Serializer.toJson(Text.literal(page))));
        }
        nbt.put("pages", list);
        return book;
    }

    private Travel() {
    }
}

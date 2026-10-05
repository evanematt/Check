package ua.lewandivka.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import ua.lewandivka.logic.DistrictQuests;

import java.util.List;

/** Записка від Пана Шлагбаума — ПКМ запускає районний квест. */
public class QuestNoteItem extends Item {
    public enum Quest { BORZHNYK, GARAGE, TRAM }

    private final Quest quest;

    public QuestNoteItem(Settings settings, Quest quest) {
        super(settings);
        this.quest = quest;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            if (DistrictQuests.start(quest, sp)) {
                if (!user.getAbilities().creativeMode) {
                    stack.decrement(1);
                }
            }
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        String text = switch (quest) {
            case BORZHNYK -> "«Боржник із третього під'їзду винен мені чайник. Знайди. Можна домовитись (3 смарагди) або по-іншому.»";
            case GARAGE -> "«Гараж № 13. Там моя посилка. Охорона не дуже привітна.» Лабіринт з'явиться за ~18 блоків попереду.";
            case TRAM -> "«Останній трамвай. Безквиткові знову лізуть. Поверни компостер.» Три хвилі ворогів просто тут.";
        };
        tooltip.add(Text.literal(text).formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal("ПКМ — почати квест").formatted(Formatting.YELLOW));
    }
}

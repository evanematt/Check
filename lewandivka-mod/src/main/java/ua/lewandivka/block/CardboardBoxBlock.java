package ua.lewandivka.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CardboardBoxBlock extends Block {
    private static final String[] LINES = {
            "Порожньо. Пахне котом.",
            "Тут хтось спав. Шерсть сіра.",
            "Коробка як коробка. Ідеальне ліжко.",
            "Всередині — ще менша коробка. А в ній — нічого.",
            "Шурхіт… ні, здалося."
    };

    public CardboardBoxBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!player.getStackInHand(hand).isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            player.sendMessage(Text.literal(LINES[world.random.nextInt(LINES.length)]), true);
            if (world.random.nextInt(12) == 0) {
                player.giveItemStack(new ItemStack(Items.COD));
                player.sendMessage(Text.literal("О, риба! Чіназік би оцінив."), true);
            }
        }
        return ActionResult.success(world.isClient);
    }
}

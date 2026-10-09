package com.lewandivka.core.registry;

import com.lewandivka.core.quest.QuestItems;
import com.lewandivka.core.registry.ItemSpec.Use;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Catalog of the mod's items. Ids come from {@link QuestItems} wherever the quest logic refers to them. */
public final class ModItems {

    private ModItems() {
    }

    public static final List<ItemSpec> ALL;
    private static final Map<String, ItemSpec> BY_ID = new LinkedHashMap<>();

    static {
        List<ItemSpec> l = new ArrayList<>();
        l.add(ItemSpec.of(QuestItems.NOTEBOOK, Use.NOTEBOOK).name("Районний блокнот", "District Notebook")
                .lore("Тут усе записано. Майже все.", "Everything is written down here. Almost everything.").quest());
        l.add(ItemSpec.of(QuestItems.TOKEN, Use.PLAIN).name("Жетон району", "District Token")
                .lore("Старий трамвайний жетон. Тепер це валюта.", "An old tram token. Now it is currency.").stack(16));
        l.add(ItemSpec.of(QuestItems.SEEDS, Use.FOOD).name("Семки", "Sunflower Seeds")
                .lore("Чорні. Смажені. Дворові.", "Black. Roasted. From the yard.").stack(64).food(1, 0.2f, 12));
        l.add(ItemSpec.of(QuestItems.DEBTOR_NOTE, Use.QUEST_NOTE).name("Записка «Боржник»", "Note: \"The Debtor\"")
                .lore("Клацни, щоб розпочати.", "Use to start.").quest());
        l.add(ItemSpec.of(QuestItems.GARAGE_NOTE, Use.QUEST_NOTE).name("Записка «Гараж №13»", "Note: \"Garage No. 13\"")
                .lore("Такого гаража не існує.", "Such a garage does not exist.").quest());
        l.add(ItemSpec.of(QuestItems.TRAM_NOTE, Use.QUEST_NOTE).name("Записка «Останній трамвай»", "Note: \"The Last Tram\"")
                .lore("Прийти до зупинки. Не запізнитися.", "Be at the stop. Do not be late.").quest());
        l.add(ItemSpec.of(QuestItems.KETTLE, Use.KETTLE).name("Магічний чайник", "Magic Kettle")
                .lore("Закипає не завжди.", "It does not always boil.").quest().rarity("uncommon").glint());
        l.add(ItemSpec.of(QuestItems.PACKAGE, Use.PACKAGE).name("Посилка", "Package")
                .lore("Не трусити. Не питати.", "Do not shake. Do not ask.").quest().rarity("rare"));
        l.add(ItemSpec.of(QuestItems.COMPOSTER, Use.COMPOSTER).name("Компостер", "Ticket Composter")
                .lore("Пробиває квитки і реальність.", "Punches tickets and reality.").quest().rarity("uncommon"));
        l.add(ItemSpec.of(QuestItems.TABLET, Use.CHROMA).name("Таблетка «Хрома»", "Chroma Tablet")
                .lore("Ковтати разом. Інакше нічого.", "Swallow together. Or nothing happens.").quest().rarity("epic").glint());
        l.add(ItemSpec.of(QuestItems.COMPASS, Use.COMPASS).name("Хроматичний компас", "Chromatic Compass")
                .lore("Показує не північ, а потрібне.", "Points not north, but where you are needed.").quest().rarity("uncommon"));
        l.add(ItemSpec.of(QuestItems.BATTERY, Use.PLAIN).name("Батарея підйомника", "Lift Battery")
                .lore("Важка. Тепла. Пахне гаражем.", "Heavy. Warm. Smells of garage.").quest().stack(1));
        l.add(ItemSpec.of(QuestItems.FISH, Use.PLAIN).name("Риба для кота", "Fish for the Cat")
                .lore("Для одного дуже уважного кота.", "For one very careful cat.").stack(16).quest());
        l.add(ItemSpec.of(QuestItems.COLLAR_CHINAZIK, Use.COLLAR).name("Нашийник «Чіназік»", "Collar: Chinazik")
                .lore("Чорний. Підозріло чистий.", "Black. Suspiciously clean.").quest());
        l.add(ItemSpec.of(QuestItems.COLLAR_METADONNA, Use.COLLAR).name("Нашийник «Метадонна»", "Collar: Metadonna")
                .lore("Сірий. Трохи пожований.", "Grey. A little chewed.").quest());
        l.add(ItemSpec.of("collar_decoy", Use.COLLAR).name("Чужий нашийник", "Strange Collar")
                .lore("Коти на нього не реагують.", "The cats do not react to it.").hidden());
        l.add(ItemSpec.of(QuestItems.WATER_CORE, Use.PLAIN).name("Водяне ядро", "Water Core")
                .lore("Нести обережно. Мокре зсередини.", "Carry with care. Wet from the inside.").stack(4));
        l.add(ItemSpec.of(QuestItems.TICKET_X, Use.TICKET).name("Квиток «Хрестик»", "Ticket: Cross").stack(4).quest());
        l.add(ItemSpec.of(QuestItems.TICKET_CIRCLE, Use.TICKET).name("Квиток «Кружечок»", "Ticket: Circle").stack(4).quest());
        l.add(ItemSpec.of(QuestItems.TICKET_TRIANGLE, Use.TICKET).name("Квиток «Трикутник»", "Ticket: Triangle").stack(4).quest());
        l.add(ItemSpec.of(QuestItems.TICKET_SQUARE, Use.TICKET).name("Квиток «Квадрат»", "Ticket: Square").stack(4).quest());
        l.add(ItemSpec.of(QuestItems.RING_GARAGE, Use.RING).name("Уламок кільця: Гараж", "Ring Fragment: Garage").rarity("rare").glint().quest());
        l.add(ItemSpec.of(QuestItems.RING_COLLECTOR, Use.RING).name("Уламок кільця: Нашийники", "Ring Fragment: Collars").rarity("rare").glint().quest());
        l.add(ItemSpec.of(QuestItems.RING_VORTEX, Use.RING).name("Уламок кільця: Вирва", "Ring Fragment: Vortex").rarity("rare").glint().quest());
        l.add(ItemSpec.of(QuestItems.RING_CONDUCTOR, Use.RING).name("Уламок кільця: Кондуктор", "Ring Fragment: Conductor").rarity("rare").glint().quest());
        l.add(ItemSpec.of(QuestItems.CHARGE, Use.CHARGE).name("Кольоровий заряд", "Chromatic Charge")
                .lore("Передай далі, поки не рвонуло.", "Pass it on before it blows.").rarity("epic").glint().quest().hidden());
        l.add(ItemSpec.of(QuestItems.SOCK, Use.SOCK).name("Звичайна шкарпетка", "Mundane Sock")
                .lore("Одна. Чиста. Нічого особливого.", "Just one. Clean. Nothing special.").hidden());
        ALL = List.copyOf(l);
        for (ItemSpec s : ALL) {
            if (BY_ID.put(s.id, s) != null) {
                throw new IllegalStateException("duplicate item id " + s.id);
            }
        }
    }

    public static ItemSpec byId(String id) {
        return BY_ID.get(id);
    }
}

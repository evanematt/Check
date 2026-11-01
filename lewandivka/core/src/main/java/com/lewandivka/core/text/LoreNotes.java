package com.lewandivka.core.text;

import java.util.Map;
import java.util.TreeMap;

/**
 * The text of every {@code lewandivka:lore_note}. The block carries only the note index; the text lives here so that
 * quest clues (breaker order, switch settings, ...) are written once and tested against the puzzle definitions.
 */
public final class LoreNotes {

    private LoreNotes() {
    }

    public record Note(String uk, String en) {
    }

    private static final Map<Integer, Note> NOTES = new TreeMap<>();

    private static void n(int index, String uk, String en) {
        NOTES.put(index, new Note(uk, en));
    }

    static {
        // ---- district
        n(0, "Трамвай до кінцевої не ходить із 1994-го. Але зупинка працює.", "No tram has run to the terminus since 1994. The stop still works.");
        n(1, "Оголошення: пакет від чаю на лавці — не мій. Не чіпати.", "Notice: the tea package on the bench is not mine. Do not touch.");
        n(2, "Збудовано 1961 року. Було, є, буде.", "Built 1961. Was, is, will be.");
        n(3, "МАГАЗИН", "SHOP");
        n(4, "Працює без перерви. Перерва — з 0:00 до 24:00.", "Open non-stop. Closed from 0:00 to 24:00.");
        // ---- garage cooperative
        n(5, "ГАРАЖНИЙ КООПЕРАТИВ. Стороннім вхід заборонено. Своїм — не рекомендовано.", "GARAGE COOPERATIVE. No entry for strangers. Not recommended for locals either.");
        n(6, "13-го гаража не існує.", "Garage No. 13 does not exist.");
        n(7, "Між дванадцятим і чотирнадцятим — цегла. Завжди була.", "Between twelve and fourteen there are bricks. There always were.");
        n(8, "Стрілка вказує на тринадцятий. Не вірте стрілці.", "The arrow points to thirteen. Do not trust the arrow.");
        n(9, "Світло вмикають три щитки. Усі три — не там, де шукають.", "Three panels turn the light on. All three are not where one looks.");
        n(10, "Автомати в дев'ятому. Третій вмикають першим.", "The breakers are in nine. The third one goes first.");
        n(11, "Перший іде після третього й перед четвертим.", "The first goes after the third and before the fourth.");
        n(12, "Четвертий — передостанній. Так записано.", "The fourth is second to last. It is written down.");
        n(13, "Другий — завжди останній. Не питайте, чому.", "The second is always last. Do not ask why.");
        n(14, "Коробка у третьому й коробка у двадцять другому. Тиснути разом.", "The box in three and the box in twenty-two. Press them together.");
        n(15, "Не виходить утрьох? Вийде вдвох. Вийде й одному, повільно.", "Can't manage with three? Two can. One can too, slowly.");
        n(16, "Як на плакаті: вгору, вниз, вгору.", "As on the poster: up, down, up.");
        n(17, "Важіль відчиняє тунель. Ненадовго.", "The lever opens the tunnel. Not for long.");
        // ---- base house
        n(20, "Тут мав би сидіти Чіназік.", "Chinazik should be sitting here.");
        n(21, "Тут мала б спати Метадонна.", "Metadonna should be sleeping here.");
        // ---- Rainbow Garage
        n(30, "ФАРБУВАЛЬНЯ. Змішуйте так, як вас вчили в дитинстві.", "PAINT WORKSHOP. Mix the way you were taught as a child.");
        n(31, "Червоний і жовтий — помаранчевий. Жовтий і синій — зелений. Синій і червоний — фіолетовий.", "Red and yellow make orange. Yellow and blue make green. Blue and red make purple.");
        n(32, "ПІДЙОМНА ЗАЛА. Платформи піднімаються по черзі. Стійте на них.", "LIFT HALL. The platforms rise in turn. Stand on them.");
        // ---- Shelter of Lost Names
        n(40, "УКРИТТЯ. Імена тут не потрібні.", "SHELTER. Names are not needed here.");
        n(41, "Три важелі. Тягнути разом.", "Three levers. Pull together.");
        n(42, "Кіт іде туди, куди не просять.", "The cat goes where nobody asks.");
        n(43, "Коробка, у якій тихо, — не та.", "A box that is quiet is not the one.");
        n(44, "Муркотіння гучнішає біля правильної.", "The purring grows louder near the right one.");
        n(45, "Подряпини на картоні — це підпис.", "Scratches on cardboard are a signature.");
        n(46, "Менша коробка — найважливіша.", "The smaller box is the important one.");
        // ---- Dry Lake Aquapark
        n(50, "СУХЕ ОЗЕРО. Басейни тимчасово не працюють.", "DRY LAKE. The pools are temporarily closed.");
        n(51, "Без шапочки не входити.", "No cap, no entry.");
        n(52, "Насосна: три пускачі. Починайте з крайнього.", "Pump room: three starters. Begin with the outermost.");
        n(53, "Червоний — хрестик. Синій — кружечок. Жовтий — трикутник. Зелений — квадрат.", "Red is a cross. Blue is a circle. Yellow is a triangle. Green is a square.");
        n(54, "Вода помаранчева. Так має бути.", "The water is orange. It is supposed to be.");
        // ---- sky
        n(60, "Пружина підкидає вгору й трохи вперед. Стежте за взуттям.", "The spring throws you up and a little forward. Mind your shoes.");
        n(61, "Небесна зупинка. Трамвай ходить за розкладом, якого ніхто не бачив.", "Sky stop. The tram runs on a timetable nobody has seen.");
        n(62, "ДИСПЕТЧЕРСЬКА. Стрілки — на себе.", "DISPATCHER. Switches towards you.");
        n(63, "Перша й третя стрілки — «Б».", "The first and the third switch are \"B\".");
        n(64, "Друга й четверта — «А». Хто сплутав — котиться назад. Це нормально.", "The second and the fourth are \"A\". Whoever mixed them up rolls back. That is normal.");
        n(65, "Квитки: хрестик, кружечок, трикутник, квадрат. Компостер — обов'язково.", "Tickets: cross, circle, triangle, square. The composter is mandatory.");
        // ---- tower approach
        n(70, "ПІДХІД ДО ВЕЖІ. Ривок — не вихід, а метод.", "TOWER APPROACH. The dash is not an exit but a method.");
        n(71, "ДВЕРІ №1. Відчиняються тільки на швидкості.", "DOOR 1. Opens only at speed.");
        n(72, "ДВЕРІ №2. Відчиняються тільки на швидкості.", "DOOR 2. Opens only at speed.");
        n(73, "ДВЕРІ №3. Відчиняються тільки на швидкості. Останні.", "DOOR 3. Opens only at speed. The last one.");
        n(74, "Це не стіна. Так вирішили.", "This is not a wall. It was decided so.");
        n(75, "Планер: натисніть стрибок у повітрі.", "Glider: press jump in the air.");
        // ---- postgame
        n(89, "ГАРАЖ №0. Тут стояли ті, кому не вистачило номера.", "GARAGE No. 0. Those who did not get a number parked here.");
        n(90, "Вихід — драбина. Він завжди був — просто ніхто не питав.", "The exit is the ladder. It was always there; nobody asked.");
        // ---- tower
        n(80, "Ласкаво просимо до Голови району. Орієнтовний час очікування: невизначений.", "Welcome to the Head of District. Estimated waiting time: undefined.");
        n(81, "ТЕРМІНОВІ ПИТАННЯ — вхід збоку.", "URGENT MATTERS — side entrance.");
        n(82, "Терміновим питанням черга не потрібна. Це не жарт.", "Urgent matters need no queue. This is not a joke.");
        n(83, "АРХІВ. Три читальні важелі. Підніміть усі, поки пам'ятаєте.", "ARCHIVE. Three reading levers. Raise them all while you remember.");
        n(84, "Кабінет 404 не знайдено.", "Office 404 not found.");
        n(85, "Кабінет, якого немає, має єдиний важіль.", "The office that does not exist has a single lever.");
        n(86, "ВІДДІЛ КОЛІРНИХ ПОРУШЕНЬ. Змішування без дозволу заборонено.", "DEPARTMENT OF COLOR VIOLATIONS. Mixing without a permit is forbidden.");
        n(87, "Службова шахта. Без пружин не стрибати.", "Service shaft. Do not jump without springs.");
        n(88, "Поверх обвалився. Ремонт заплановано на минулий вівторок.", "The floor has collapsed. Repairs were scheduled for last Tuesday.");
    }

    public static Map<Integer, Note> all() {
        return NOTES;
    }

    public static Note of(int index) {
        return NOTES.get(index);
    }

    public static String key(int index) {
        return "note.lewandivka." + index;
    }
}

package com.lewandivka.core.text;

import com.lewandivka.core.dialogue.DialogueScript;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every dialogue of the game: scripts (speaker, timing, choices) and their Ukrainian/English text. Lines stay short,
 * dry and deadpan; nothing is a wall of text. Scripts are plain data, the server plays them tick by tick.
 */
public final class DialogueBook {

    private DialogueBook() {
    }

    public record Line(String uk, String en) {
    }

    private static final Map<String, DialogueScript> SCRIPTS = new LinkedHashMap<>();
    /** Language key to text for lines, choices and speaker names. */
    private static final Map<String, Line> TEXT = new LinkedHashMap<>();

    private static final class B {
        private final String id;
        private final DialogueScript.Builder builder;
        private int n;

        private B(String id) {
            this.id = id;
            this.builder = DialogueScript.builder(id);
        }

        B say(String speaker, String uk, String en) {
            return say(speaker, uk, en, 50);
        }

        B say(String speaker, String uk, String en, int delay) {
            n++;
            builder.say(speaker, delay);
            TEXT.put("dialogue.lewandivka." + id + "." + n, new Line(uk, en));
            return this;
        }

        B choice(String cid, String uk, String en) {
            builder.choice(cid);
            TEXT.put("dialogue.lewandivka." + id + ".choice." + cid, new Line(uk, en));
            return this;
        }

        void done() {
            SCRIPTS.put(id, builder.build());
        }
    }

    private static B script(String id) {
        return new B(id);
    }

    private static void speaker(String id, String uk, String en) {
        TEXT.put("speaker.lewandivka." + id, new Line(uk, en));
    }

    /** Script id of the {@code n}-th (from 1) thing a citizen or a vendor says: {@code talk_citizen_kid_3}, {@code greet_vendor_baker_1}. */
    public static String talkId(String entityId, int n) {
        return (entityId.startsWith("vendor_") ? "greet_" : "talk_") + entityId + "_" + n;
    }

    /** How many different things the creature has to say (0 when it has nothing to say). */
    public static int talks(String entityId) {
        int n = 0;
        while (SCRIPTS.containsKey(talkId(entityId, n + 1))) {
            n++;
        }
        return n;
    }

    /**
     * The words of a citizen or a vendor: every script is a few short lines of {uk, en} said one after another by the speaker whose
     * name is that of the creature.
     */
    private static void chat(String entityId, String uk, String en, String[][]... scripts) {
        speaker(entityId, uk, en);
        int n = 0;
        for (String[][] lines : scripts) {
            B b = script(talkId(entityId, ++n));
            for (int i = 0; i < lines.length; i++) {
                b.say(entityId, lines[i][0], lines[i][1], i == 0 ? 10 : 55);
            }
            b.done();
        }
    }

    private static String[][] said(String... ukEn) {
        String[][] out = new String[ukEn.length / 2][];
        for (int i = 0; i < out.length; i++) {
            out[i] = new String[] {ukEn[2 * i], ukEn[2 * i + 1]};
        }
        return out;
    }

    static {
        speaker("shlahbaum", "Пан Шлагбаум", "Mr. Shlahbaum");
        speaker("gopnik", "Гопник", "Gopnik");
        speaker("senior", "Старший по двору", "Senior Yard Gopnik");
        speaker("debtor", "Боржник", "The Debtor");
        speaker("leader", "Ватажок безквиткових", "Fare Dodger Leader");
        speaker("unknown", "???", "???");
        speaker("chinazik", "Чіназік", "Chinazik");
        speaker("metadonna", "Метадонна", "Metadonna");
        speaker("king", "Гаражний Король", "Garage King");
        speaker("collector", "Колекціонер Нашийників", "Collar Collector");
        speaker("vortex", "Пані Вирва", "Lady Vortex");
        speaker("conductor", "Кондуктор", "The Conductor");
        speaker("head", "Безбарвний Голова", "The Colorless Head");
        speaker("machine", "Автомат", "The Machine");
        speaker("notebook", "Блокнот", "Notebook");
        speaker("", "", "");

        // ---------------------------------------------------------------- Act 1
        script("shlahbaum_kiosk")
                .say("shlahbaum", "О. Кіоск.", "Oh. A kiosk.", 40)
                .say("shlahbaum", "Давно тут не стояв.", "Haven't stood here in a while.", 60)
                .say("shlahbaum", "Є одна справа. Дрібна.", "There is a matter. A small one.", 70)
                .say("shlahbaum", "Тримайте. Там усе написано.", "Take this. It is all written there.", 70).done();
        script("shlahbaum_idle_1").say("shlahbaum", "Шлагбаум піднято.", "The barrier is up.", 10).done();
        script("shlahbaum_idle_2").say("shlahbaum", "Нічого не відбувається. Це добре.", "Nothing is happening. That is good.", 10).done();
        script("shlahbaum_idle_3").say("shlahbaum", "Район — не місце. Район — звичка.", "A district is not a place. It is a habit.", 10).done();
        script("shlahbaum_idle_4").say("shlahbaum", "Не стійте під стрілою.", "Do not stand under the boom.", 10).done();
        script("seeds_demand")
                .say("gopnik", "Ей. Сємки є?", "Hey. Got any seeds?", 10)
                .choice("give", "Дати сємки", "Give seeds")
                .choice("leave", "Піти геть", "Walk away")
                .choice("fight", "Відмовити кулаком", "Refuse with a fist").done();
        script("seeds_given").say("gopnik", "О. Нормальний.", "Oh. Decent.", 10).say("gopnik", "Ходи спокійно.", "Walk easy.", 50).done();
        script("seeds_left").say("gopnik", "Ну і йди.", "Then go.", 10).done();
        script("seeds_none").say("gopnik", "Нема? Ну-ну.", "None? Well, well.", 10).done();
        script("debtor_found")
                .say("debtor", "Не бий! Я все поясню!", "Do not hit me! I can explain!", 10)
                .say("debtor", "Я винен трамваю. Три смарагди.", "I owe the tram. Three emeralds.", 60)
                .say("debtor", "Або... може, домовимось?", "Or... maybe we can agree on something?", 60)
                .choice("pay", "Заплатити 3 смарагди", "Pay 3 emeralds")
                .choice("vouch", "Поручитися жетонами району", "Vouch with district tokens")
                .choice("seeds", "Принести йому сємок", "Bring him seeds").done();
        script("debtor_resolved_pay").say("debtor", "Дякую. Рахунок закрито.", "Thanks. The bill is settled.", 10)
                .say("debtor", "Візьміть. Воно все одно кипить.", "Take it. It boils anyway.", 60).done();
        script("debtor_resolved_vouch").say("debtor", "Ви свої. Я запам'ятаю.", "You are local. I will remember.", 10)
                .say("debtor", "Візьміть. Воно все одно кипить.", "Take it. It boils anyway.", 60).done();
        script("debtor_resolved_seeds").say("debtor", "Сємки! Ох, вдячний.", "Seeds! Oh, I am grateful.", 10)
                .say("debtor", "Візьміть. Воно все одно кипить.", "Take it. It boils anyway.", 60).done();
        script("debtor_taunt_1").say("debtor", "Не наздоженете!", "You will not catch me!", 5).done();
        script("debtor_taunt_2").say("debtor", "Тільки не до гаражів!", "Anything but the garages!", 5).done();
        script("debtor_taunt_3").say("debtor", "Я зараз упаду...", "I am about to fall...", 5).done();
        script("shlahbaum_kettle")
                .say("shlahbaum", "Ти це бачив?", "Did you see that?", 30)
                .say("shlahbaum", "Я теж ні.", "Neither did I.", 60)
                .say("shlahbaum", "Гараж. Тринадцятий. Його нема.", "A garage. The thirteenth. It does not exist.", 70)
                .say("shlahbaum", "Тому й шукайте.", "So go and look for it.", 70).done();
        script("shlahbaum_package")
                .say("shlahbaum", "Давайте.", "Hand it over.", 20)
                .say("shlahbaum", "Хм.", "Hm.", 60)
                .say("shlahbaum", "Показувати не буду.", "I will not show you.", 70)
                .say("shlahbaum", "Сьогодні буде останній трамвай.", "Tonight is the last tram.", 70)
                .say("shlahbaum", "Не запізнюйтесь.", "Do not be late.", 60).done();
        script("tram_start")
                .say("", "Десь далеко дзвонить трамвай.", "Somewhere far away a tram bell rings.", 20)
                .say("", "Рейки були зламані.", "The rails were broken.", 70)
                .say("leader", "Квитки!", "Tickets!", 70).done();
        script("tram_shield").say("leader", "У мене проїзний. Довічний.", "I have a pass. A lifetime one.", 10).done();
        script("shlahbaum_composter")
                .say("shlahbaum", "Компостер.", "A composter.", 30)
                .say("shlahbaum", "Пробиває квитки. І не тільки.", "Punches tickets. And not only those.", 70)
                .say("shlahbaum", "Бережіть.", "Keep it safe.", 60).done();
        script("shlahbaum_chroma")
                .say("shlahbaum", "Тепер можна.", "Now it is allowed.", 30)
                .say("shlahbaum", "Це «Хрома».", "This is Chroma.", 70)
                .say("shlahbaum", "Район колись був більший.", "The district used to be bigger.", 80)
                .say("shlahbaum", "Ковтайте разом.", "Swallow together.", 70).done();

        // ---------------------------------------------------------------- Act 2
        script("base_wake")
                .say("", "Стеля інша.", "The ceiling is different.", 30)
                .say("", "Пахне чаєм і чимось рожевим.", "It smells of tea and something pink.", 70)
                .say("", "Два порожні місця біля стіни.", "Two empty places by the wall.", 70).done();
        script("chinazik_first")
                .say("", "Чорний кіт дивиться на вас.", "A black cat stares at you.", 20)
                .say("", "Потім іде в інший бік.", "Then he walks the other way.", 70).done();
        script("chinazik_named").say("", "Його звати Чіназік.", "His name is Chinazik.", 20).done();
        script("metadonna_first").say("", "У коробці щось муркоче.", "Something purrs in the box.", 20).done();
        script("metadonna_named").say("", "Її звати Метадонна.", "Her name is Metadonna.", 20).done();
        script("cats_doors").say("", "Обоє дивляться на двері.", "Both of them look at the doors.", 30).done();
        script("garage_king_intro")
                .say("king", "Гараж зайнято.", "The garage is occupied.", 20)
                .say("king", "Чекайте своєї черги.", "Wait your turn.", 60).done();
        script("collector_intro")
                .say("collector", "Усі коти — мої.", "All cats are mine.", 20)
                .say("collector", "Нашийники пам'ятають імена.", "Collars remember names.", 60).done();
        script("vortex_intro")
                .say("vortex", "Басейн працює.", "The pool is open.", 20)
                .say("vortex", "Без шапочки — у воду.", "No cap, into the water.", 60).done();
        script("conductor_intro")
                .say("conductor", "Квитки, будь ласка.", "Tickets, please.", 20)
                .say("conductor", "Без квитка — по шпалах.", "No ticket, walk the sleepers.", 60).done();
        script("conductor_call").say("conductor", "КВИТОК!", "TICKET!", 5).done();
        script("head_message")
                .say("head", "Достатньо.", "Enough.", 30)
                .say("head", "Кольори відключено.", "The colours are disconnected.", 70)
                .say("head", "Прошу до мене.", "Please come to me.", 70).done();
        script("head_lore")
                .say("head", "Я прибирав хаос.", "I was removing chaos.", 20)
                .say("head", "Прибрав колір.", "I removed the colour.", 60)
                .say("head", "Прибрав імена.", "I removed the names.", 60)
                .say("head", "Закрив шляхи.", "I closed the paths.", 60)
                .say("head", "Розділив райони.", "I separated the districts.", 60)
                .say("", "Коти пам'ятали старі маршрути.", "The cats remembered the old routes.", 80).done();
        script("head_final").say("head", "Ось так краще.", "That is better.", 40).done();
        script("head_end").say("head", "Сіро...", "Grey...", 20).say("head", "Тихо...", "Quiet...", 80).done();
        script("tower_machine")
                .say("machine", "ВІЗЬМІТЬ ТАЛОН", "TAKE A TICKET", 10)
                .say("machine", "Ваш номер: №%s. Зараз обслуговується №003.", "Your number: #%s. Now serving #003.", 40).done();
        script("tower_urgent").say("machine", "ТЕРМІНОВІ ПИТАННЯ — вхід збоку.", "URGENT MATTERS — side entrance.", 10).done();

        // ---------------------------------------------------------------- the people of the district
        chat("citizen_babushka", "Бабуся", "Old Woman",
                said("Ти чий будеш?", "Whose are you?", "Нічий? Ну, тоді наш.", "Nobody's? Then you are ours."),
                said("Хліб на ринку ще теплий.", "The bread at the market is still warm.", "Іди, поки не розібрали.", "Go, before they sell out."),
                said("Колись тут трамвай ходив.", "A tram used to run here.", "Гарний був. Червоний.", "A nice one. Red."),
                said("Ввечері до гаражів не ходи.", "Do not go to the garages in the evening.", "Там хлопці сємки лузають.", "The boys crack seeds there.",
                        "І питають.", "And ask."),
                said("Котів годуєш? Молодець.", "Feeding the cats? Good for you.", "Вони тут усе пам'ятають.", "They remember everything here."));
        chat("citizen_grandpa", "Дідусь", "Old Man",
                said("Шлагбаум піднято. День вдався.", "The barrier is up. A good day."),
                said("Район той самий.", "Same district.", "Це ми змінилися.", "It is we who changed."),
                said("Хочеш пораду?", "Want some advice?", "Не клади цвях на рейки.", "Do not put a nail on the rails.", "Трамвай образиться.", "The tram will be offended."),
                said("Шахи? Ні. Доміно.", "Chess? No. Dominoes.", "Шахи — для тих, хто не вірить у долю.", "Chess is for those who do not believe in fate."),
                said("Погода вже не та.", "The weather is not what it was.", "Але й не гірша.", "But not worse, either."));
        chat("citizen_worker", "Робітник", "Worker",
                said("Перекур.", "Smoke break.", "Не дивіться так. Я кинув. Давно.", "Do not look like that. I quit. Long ago."),
                said("На підстанціях моргає світло.", "The lights flicker at the substations.", "Хтось у гаражах краде струм.", "Someone in the garages is stealing current."),
                said("Потрібен інструмент?", "Need tools?", "Йди до майстра на ринок.", "Go to the handyman at the market."),
                said("Тринадцятий гараж? Такого немає.", "The thirteenth garage? There is no such thing.", "Є дванадцятий і чотирнадцятий.", "There is a twelfth and a fourteenth.",
                        "Між ними — стіна.", "Between them is a wall."),
                said("Норма — вісім годин.", "The norm is eight hours.", "А відчуття — вічність.", "The feeling is eternity."));
        chat("citizen_student", "Студентка", "Student",
                said("У мене сесія. Не заважайте.", "I have exams. Do not disturb.", "Хоча... заважайте.", "Although... do disturb."),
                said("Вам не здається, що район — це квест?", "Does the district not feel like a quest to you?", "Мені теж.", "To me too."),
                said("Кафе немає. Ринок є.", "No cafes. But there is a market.", "Беру пиріг і йду вчитись.", "I take a pie and go study."),
                said("Зв'язок тут ловить лише на даху.", "The signal only works on the roof here.", "Тому всі сидять на даху.", "So everybody sits on the roof."),
                said("Бачила кота біля пісочниці.", "I saw a cat near the sandbox.", "Чорного. Дивився осудливо.", "A black one. He looked judgmental."));
        chat("citizen_kid", "Дитина", "Kid",
                said("Ви ловитимете боржника?", "Will you catch the debtor?", "Він швидкий!", "He is fast!"),
                said("А в мене є жетон!", "I have a token!", "Ні, не покажу.", "No, I will not show you."),
                said("Пісочниця — моя територія.", "The sandbox is my territory.", "Вхід — одна цукерка.", "The entrance is one candy."),
                said("Я бачила трамвай уночі.", "I saw a tram at night.", "Дорослі кажуть — наснилось.", "The adults say I dreamed it."),
                said("Мама казала не говорити з незнайомими.", "Mom said not to talk to strangers.", "Але ж ви майже свої.", "But you are almost one of us."));
        chat("citizen_teacher", "Вчителька", "Teacher",
                said("Школа сьогодні зачинена.", "The school is closed today.", "Завтра теж. І післязавтра.", "Tomorrow too. And the day after."),
                said("Діти! Не бігати коридором!", "Children! No running in the corridor!", "Ой, це я не вам.", "Oh, that was not for you."),
                said("Хочете вчитись? Усі парти вільні.", "Want to study? All the desks are free."),
                said("Домашнє завдання: знайти дванадцять котів.", "Homework: find twelve cats.", "Ні, я серйозно.", "No, I am serious."),
                said("Я викладала біологію.", "I used to teach biology.", "Тепер викладаю виживання.", "Now I teach survival."));
        chat("citizen_yard_keeper", "Двірник", "Yard Keeper",
                said("Мету. Мету. Мету.", "Sweeping. Sweeping. Sweeping.", "Листя не закінчується.", "The leaves never end."),
                said("Це моя територія. Ну, наша.", "This is my territory. Well, ours.", "Не смітіть.", "No littering."),
                said("У смітниках іноді є корисне.", "Sometimes there is something useful in the bins.", "Але я нічого не казав.", "But I said nothing."),
                said("Ліхтарі горять. Район живий.", "The lamps are burning. The district lives."),
                said("Сємки — біля лавки.", "Seeds by the bench.", "Лушпиння — в урну.", "Husks into the bin."));
        chat("citizen_neighbour", "Сусід", "Neighbour",
                said("О, нові сусіди.", "Oh, new neighbours.", "Сіль є? Ні? Я й не питав.", "Got salt? No? I did not ask."),
                said("Я тут з дев'яносто першого.", "I have been here since ninety-one.", "Лампочку на сходах досі не вкрутили.", "The bulb on the stairs is still not replaced."),
                said("Мій телевізор показує один канал.", "My TV shows one channel.", "Зате хороший.", "A good one, though."),
                said("Чуєте дзвінок? Це не мій телефон.", "Hear that ringing? It is not my phone.", "Це трамвай.", "That is the tram."),
                said("Чаю хочете? Чаю немає.", "Tea? There is none.", "Але запрошення чинне.", "But the invitation stands."));
        // the vendors say one thing before the counter opens
        chat("vendor_baker", "Пекарка", "Baker",
                said("Свіженьке! Ще тепле.", "Fresh! Still warm."),
                said("Хліб — основа району.", "Bread is the foundation of the district."),
                said("Беріть пиріг. Вдруге не запропоную.", "Take a pie. I will not offer twice."));
        chat("vendor_greengrocer", "Овочівниця", "Greengrocer",
                said("Картопля своя. Не ваша.", "The potatoes are mine. Not yours."),
                said("Яблука кислі. Зате чесні.", "The apples are sour. But honest."),
                said("Торгуємося? Ні? Тоді беріть.", "Haggle? No? Then take them."));
        chat("vendor_butcher", "М'ясник", "Butcher",
                said("М'ясо не кусається.", "The meat does not bite."),
                said("Свинина вчорашня. Це комплімент.", "The pork is from yesterday. That is a compliment."),
                said("Без черги, прошу.", "No queue, please."));
        chat("vendor_handyman", "Майстер", "Handyman",
                said("Інструмент — як друг. Поводься добре.", "A tool is like a friend. Treat it well."),
                said("Цвяхи є. Терпіння — ні.", "I have nails. Patience, no."),
                said("Лопата, сокира, кирка. Усе справжнє.", "Shovel, axe, pickaxe. All genuine."));
        chat("vendor_flea", "Барахольник", "Flea Trader",
                said("Усе вживане. Нове — тільки пил.", "All used. Only the dust is new."),
                said("Жетони? Ні. Жетони бере лише трамвай.", "Tokens? No. Only the tram takes tokens."),
                said("Ліжко, повідець, компас. Вибирайте.", "Bed, lead, compass. Choose."));
        chat("vendor_fishmonger", "Рибалка", "Fishmonger",
                said("Риба свіжа. Я її сам не бачив.", "The fish is fresh. I have not seen it myself."),
                said("Клює? Не клює? Беріть готову.", "Biting? Not biting? Take it cooked."),
                said("Річка велика. Терпіння ще більше.", "The river is big. The patience is bigger."));
        chat("vendor_gardener", "Садівник", "Gardener",
                said("Насіння — це майбутнє. Дешеве.", "Seeds are the future. A cheap one."),
                said("Посадиш — виросте. Іноді.", "Plant it and it grows. Sometimes."),
                said("Кістяне борошно — для нетерплячих.", "Bone meal is for the impatient."));

        // ---------------------------------------------------------------- epilogue and postgame
        script("epilogue_morning")
                .say("shlahbaum", "Ну?", "Well?", 40)
                .say("shlahbaum", "Були?", "Been there?", 80)
                .say("shlahbaum", "Я ж казав.", "Told you.", 80).done();
        script("epilogue_cats")
                .say("shlahbaum", "А ці звідки?", "And where are these from?", 40)
                .say("shlahbaum", "А.", "Ah.", 80)
                .say("shlahbaum", "Ці місцеві.", "These are local.", 60).done();
        script("post_cats12").say("shlahbaum", "По району ще дванадцять котів.", "Twelve more cats are around the district.", 20)
                .say("shlahbaum", "Усі з іменами.", "All of them have names.", 60).done();
        script("post_seeds").say("shlahbaum", "Сємки зникають. Щоночі.", "The seeds vanish. Every night.", 20)
                .say("shlahbaum", "Гляньте, хто їх бере.", "See who takes them.", 60).done();
        script("post_tram").say("shlahbaum", "Є ще один трамвай.", "There is one more tram.", 20)
                .say("shlahbaum", "Не той.", "The wrong one.", 60).done();
        script("post_garage0").say("shlahbaum", "Гараж нуль.", "Garage zero.", 20)
                .say("shlahbaum", "Його не було навіть у записках.", "It was not even in the notes.", 60).done();
        script("post_package")
                .say("shlahbaum", "Хочете знати, що було в посилці?", "Do you want to know what was in the package?", 20)
                .choice("yes", "Так", "Yes")
                .choice("no", "Ні", "No").done();
        script("post_package_reveal")
                .say("shlahbaum", "Шкарпетка.", "A sock.", 30)
                .say("shlahbaum", "Одна.", "Just one.", 70)
                .say("shlahbaum", "Краще б не питав.", "Better not to have asked.", 70).done();
    }

    public static DialogueScript get(String id) {
        return SCRIPTS.get(id);
    }

    public static Map<String, DialogueScript> scripts() {
        return SCRIPTS;
    }

    public static Map<String, Line> text() {
        return TEXT;
    }
}

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

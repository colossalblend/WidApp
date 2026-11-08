package app.widapp.ui.ideas

import app.widapp.core.L

data class Idea(val category: String, val size: String, val text: String)

object IdeaPrompts {
    val categories get() = listOf(
        "time" to L.t("Время", "Time"),
        "focus" to L.t("Продуктивность", "Focus"),
        "health" to L.t("Здоровье", "Health"),
        "ai" to L.t("ИИ-контент", "AI content"),
        "fun" to L.t("Для души", "Fun"),
        "info" to L.t("Данные", "Data"),
    )

    val all: List<Idea> get() = listOf(
        Idea("time", "4x2", L.t("Огромные тонкие часы, под ними день недели и дата, справа следующий будильник. Строгий швейцарский стиль.", "Huge thin clock with weekday and date below and the next alarm on the right. Strict Swiss style.")),
        Idea("time", "2x2", L.t("Аналоговое настроение: время крупно по центру внутри кольца прогресса дня.", "Time in the centre of a ring that shows how much of the day has passed.")),
        Idea("time", "4x1", L.t("Полоса прогресса года с процентом и количеством оставшихся дней.", "A year progress bar with percent and days left.")),
        Idea("time", "4x2", L.t("Три мировых часа: Москва, Дубай, Токио — с подписью «день/ночь» по часу.", "Three world clocks — London, Dubai, Tokyo — each labelled day or night.")),
        Idea("focus", "4x2", L.t("Помодоро-счётчик: сколько фокус-сессий сегодня, кнопки +1 и сброс, цель — 8, кольцо прогресса.", "Pomodoro counter: focus sessions today, +1 and reset buttons, goal of 8, progress ring.")),
        Idea("focus", "4x3", L.t("Чек-лист из 5 дел на сегодня с прогрессом выполнения и кнопкой «Новый день».", "A 5-item checklist for today with completion progress and a New day button.")),
        Idea("focus", "2x2", L.t("Переключатель «Работаю / Отдыхаю» — большая кнопка, цвет виджета меняется от состояния.", "A big Working / Resting toggle that changes the widget colour.")),
        Idea("focus", "4x2", L.t("Трекер привычки «30 дней без сахара»: счётчик дней, кнопка «+1 день», сброс.", "A 30 days without sugar habit tracker: day counter, +1 day button, reset.")),
        Idea("health", "2x2", L.t("Трекер воды: стаканы за день, кнопки + и −, цель 8 стаканов, морская палитра.", "Water tracker: glasses today, + and − buttons, goal of 8, ocean palette.")),
        Idea("health", "4x2", L.t("Настроение дня: пять кнопок-смайликов (иконки), выбранное настроение подсвечено и подписано.", "Mood of the day: five mood icon buttons, the selected one highlighted and named.")),
        Idea("health", "2x1", L.t("Напоминание о разминке: кнопка «Размялся», показывает время последней разминки.", "Stretch reminder: a Done button that shows when you last stretched.")),
        Idea("ai", "4x2", L.t("Слово дня на английском с транскрипцией и переводом, обновляется каждый день, кнопка «Ещё слово».", "Spanish word of the day with pronunciation and translation, refreshed daily, with a Next word button.")),
        Idea("ai", "4x2", L.t("Загадка дня: вопрос, а ответ скрыт и появляется по нажатию «Показать ответ».", "Riddle of the day: the answer stays hidden until you tap Show answer.")),
        Idea("ai", "4x3", L.t("Утренний брифинг: приветствие, погода, одна мотивирующая мысль от ИИ и совет на день.", "Morning briefing: greeting, weather, one motivating thought and a tip for the day from AI.")),
        Idea("ai", "2x2", L.t("Комплимент дня — тёплая короткая фраза от ИИ, кнопка «Ещё».", "Compliment of the day — a short warm line from AI with a More button.")),
        Idea("ai", "4x2", L.t("Мини-история на 2 предложения каждый час в стиле научной фантастики.", "A two-sentence sci-fi micro story, new every hour.")),
        Idea("fun", "2x2", L.t("Подбрасывание монетки: кнопка «Бросить», результат Орёл/Решка от ИИ с забавной подписью.", "Coin flip: a Flip button, heads or tails from AI with a funny caption.")),
        Idea("fun", "4x2", L.t("Дней до отпуска — 15 июля, с прогрессом ожидания и тропической палитрой.", "Days until vacation on July 15 with a waiting progress bar and a tropical palette.")),
        Idea("fun", "1x1", L.t("Крошечный счётчик кофе за день: иконка кофе и число, тап — +1.", "Tiny coffee counter: coffee icon and a number, tap for +1.")),
        Idea("info", "2x2", L.t("Батарея большим числом, состояние зарядки и цветная полоса, красная ниже 20%.", "Battery as a big number with charging state and a bar that turns red under 20%.")),
        Idea("info", "4x2", L.t("Погода: температура крупно, описание, ощущается как, влажность, ветер и шанс осадков.", "Weather: big temperature, description, feels like, humidity, wind and chance of rain.")),
        Idea("info", "4x1", L.t("Строка состояния телефона: заряд, свободная память, следующий будильник.", "Phone status line: battery, free storage, next alarm.")),
    )

    val styles: List<Pair<String, String>> get() = listOf(
        "" to L.t("Авто", "Auto"),
        "Material You: wallpaper colour roles, rounded, calm" to "Material You",
        "minimal: lots of air, thin type, monochrome" to L.t("Минимализм", "Minimal"),
        "Swiss International Style: strict grid, bold grotesque, one red accent" to L.t("Швейцарский", "Swiss"),
        "newspaper / editorial: serif headlines, off-white paper, black ink" to L.t("Газета", "Editorial"),
        "retro terminal: black background, green monospace text" to L.t("Терминал", "Terminal"),
        "brutalism: raw, high contrast, heavy condensed type, hard edges (corner 0)" to L.t("Брутализм", "Brutalist"),
        "soft pastel: gentle pastel colours, rounded shapes, friendly" to L.t("Пастель", "Pastel"),
        "night neon: deep dark background, one glowing neon accent" to L.t("Неон", "Neon"),
        "70s retro: warm orange, brown and cream, chunky rounded type" to L.t("Ретро 70-х", "70s retro"),
        "frosted glass: translucent white layers over a soft gradient" to L.t("Стекло", "Glass"),
        "playful: bold colour blocking, big friendly numbers" to L.t("Игривый", "Playful"),
    )
}

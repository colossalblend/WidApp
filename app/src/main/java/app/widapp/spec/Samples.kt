package app.widapp.spec

import java.util.Locale

/** Hand-made designs shown in the Ideas tab. They also double as renderer smoke tests. */
object Samples {
    data class Sample(val id: String, val json: String)

    private fun ru() = Locale.getDefault().language == "ru"
    private fun t(ru: String, en: String) = if (ru()) ru else en

    fun all(): List<Sample> = listOf(
        Sample("clock", """
{"name":"${t("Часы", "Clock")}","size":"4x2","background":"@primaryContainer","corner":28,"padding":{"h":22,"v":18},
 "root":{"type":"row","height":"fill","valign":"center","children":[
  {"type":"column","weight":1,"children":[
   {"type":"clock","format":"HH:mm","size":66,"fontWeight":200,"color":"@onPrimaryContainer"},
   {"type":"clock","format":"EEEE, d MMMM","size":15,"fontWeight":500,"color":"@onPrimaryContainer/70"}]},
  {"type":"column","align":"end","gap":4,"onClick":{"do":"open","target":"alarm"},"children":[
   {"type":"icon","icon":"alarm","size":20,"color":"@onPrimaryContainer/70"},
   {"type":"text","text":"{next_alarm}","size":14,"fontWeight":600,"color":"@onPrimaryContainer"}]}]}}
"""),
        Sample("weather", """
{"name":"${t("Погода", "Weather")}","size":"2x2","background":"@secondaryContainer","corner":28,"padding":16,
 "root":{"type":"column","height":"fill","onClick":{"do":"refresh"},"children":[
  {"type":"row","valign":"center","children":[
   {"type":"text","text":"{weather.city}","size":13,"fontWeight":600,"color":"@onSecondaryContainer","maxLines":1,"weight":1},
   {"type":"icon","icon":"{weather.icon}","size":26,"color":"@onSecondaryContainer"}]},
  {"type":"spacer","weight":1},
  {"type":"text","text":"{weather.temp}°","size":50,"fontWeight":300,"color":"@onSecondaryContainer"},
  {"type":"text","text":"{weather.desc}","size":13,"color":"@onSecondaryContainer/80","maxLines":1},
  {"type":"text","text":"↑ {weather.high}°   ↓ {weather.low}°","size":12,"fontWeight":500,"color":"@onSecondaryContainer/60"}]}}
"""),
        Sample("battery", """
{"name":"${t("Заряд", "Battery")}","size":"2x2","background":"@surfaceContainer","padding":14,
 "root":{"type":"box","width":"fill","height":"fill","align":"center","valign":"center","children":[
  {"type":"progress","style":"ring","value":"{battery}","max":100,"size":124,"thickness":11,
   "color":"{battery < 20 ? '@error' : '@primary'}","track":"@onSurface/10","children":[
   {"type":"column","align":"center","children":[
    {"type":"icon","icon":"{charging ? 'bolt' : 'battery_full'}","size":18,"color":"@onSurfaceVariant"},
    {"type":"text","text":"{battery}%","size":26,"fontWeight":600,"color":"@onSurface"}]}]}]}}
"""),
        Sample("water", """
{"name":"${t("Вода", "Water")}","size":"2x2","background":"#123A4A","corner":28,"padding":14,"state":{"cups":0},
 "root":{"type":"column","height":"fill","children":[
  {"type":"row","valign":"center","children":[
   {"type":"icon","icon":"water_drop","size":16,"color":"#7FD3F0"},
   {"type":"spacer","width":6},
   {"type":"text","text":"${t("Вода", "Water")}","size":13,"fontWeight":600,"color":"#E6F6FC","weight":1},
   {"type":"text","text":"{cups}/8","size":12,"font":"mono","color":"#7FD3F0"}]},
  {"type":"spacer","weight":1},
  {"type":"text","text":"{cups >= 8 ? '${t("Готово", "Done")}' : cups}","size":40,"fontWeight":300,"color":"#E6F6FC"},
  {"type":"spacer","height":6},
  {"type":"progress","value":"{cups}","max":8,"color":"#7FD3F0","track":"#7FD3F0/25","thickness":5},
  {"type":"spacer","height":10},
  {"type":"row","gap":8,"children":[
   {"type":"button","icon":"remove","style":"tonal","color":"#7FD3F0/20","contentColor":"#E6F6FC","weight":1,"onClick":{"do":"dec","bind":"cups","min":0}},
   {"type":"button","icon":"add","style":"filled","color":"#7FD3F0","contentColor":"#06212B","weight":1,"onClick":{"do":"inc","bind":"cups","max":12}}]}]}}
"""),
        Sample("habits", """
{"name":"${t("Привычки", "Habits")}","size":"4x2","background":"@surfaceContainerHigh","padding":16,
 "state":{"h1":false,"h2":false,"h3":false},
 "root":{"type":"row","height":"fill","gap":14,"children":[
  {"type":"column","width":92,"height":"fill","children":[
   {"type":"clock","format":"EEEE","size":13,"fontWeight":600,"color":"@onSurfaceVariant"},
   {"type":"clock","format":"d","size":46,"fontWeight":300,"color":"@onSurface"},
   {"type":"spacer","weight":1},
   {"type":"button","text":"${t("Сброс", "Reset")}","icon":"replay","style":"ghost","contentColor":"@onSurfaceVariant","onClick":{"do":"reset"}}]},
  {"type":"column","weight":1,"height":"fill","valign":"center","gap":2,"children":[
   {"type":"check","bind":"h1","text":"${t("Зарядка 10 минут", "10 min stretch")}","color":"@primary"},
   {"type":"check","bind":"h2","text":"${t("Без телефона до 9:00", "No phone before 9")}","color":"@primary"},
   {"type":"check","bind":"h3","text":"${t("20 страниц книги", "Read 20 pages")}","color":"@primary"}]}]}}
"""),
        Sample("fact", """
{"name":"${t("Факт дня", "Fact of the day")}","size":"4x2","background":"@tertiaryContainer","corner":28,"padding":18,
 "ai":{"fact":{"prompt":"${t("Один удивительный и достоверный научный факт. До 130 символов, без вступлений.", "One surprising, true science fact. Under 130 characters, no preamble.")}","refresh":"daily"}},
 "root":{"type":"column","height":"fill","children":[
  {"type":"row","valign":"center","children":[
   {"type":"icon","icon":"lightbulb","size":16,"color":"@onTertiaryContainer"},
   {"type":"spacer","width":6},
   {"type":"text","text":"${t("ФАКТ ДНЯ", "FACT OF THE DAY")}","size":11,"fontWeight":700,"color":"@onTertiaryContainer/70","weight":1},
   {"type":"button","text":"{ai_loading ? '…' : '${t("Ещё", "More")}'}","style":"tonal","color":"@onTertiaryContainer/12","contentColor":"@onTertiaryContainer","onClick":{"do":"ai","key":"fact"}}]},
  {"type":"spacer","weight":1},
  {"type":"text","text":"{ai.fact}","size":17,"fontWeight":500,"font":"serif","color":"@onTertiaryContainer","maxLines":4},
  {"type":"spacer","weight":1}]}}
"""),
        Sample("quotes", """
{"name":"${t("Мысли о дизайне", "Design thoughts")}","size":"4x2","background":"@inverseSurface","padding":18,"state":{"p":0},
 "root":{"type":"column","height":"fill","children":[
  {"type":"pages","bind":"p","weight":1,"children":[
   {"type":"text","text":"${t("«Совершенство достигается не тогда, когда нечего добавить, а когда нечего убрать.»", "“Perfection is achieved not when there is nothing more to add, but when there is nothing left to take away.”")}","size":16,"font":"serif","color":"@inverseOnSurface","maxLines":4},
   {"type":"text","text":"${t("«Простота — высшая степень изощрённости.»", "“Simplicity is the ultimate sophistication.”")}","size":20,"font":"serif","color":"@inverseOnSurface","maxLines":4},
   {"type":"text","text":"${t("«Хороший дизайн — это как можно меньше дизайна.»", "“Good design is as little design as possible.”")}","size":20,"font":"serif","color":"@inverseOnSurface","maxLines":4}]},
  {"type":"row","valign":"center","children":[
   {"type":"dots","bind":"p","count":3,"color":"@inverseOnSurface","track":"@inverseOnSurface/30"},
   {"type":"spacer","weight":1},
   {"type":"button","icon":"chevron_left","style":"ghost","contentColor":"@inverseOnSurface","onClick":{"do":"prev","bind":"p","count":3}},
   {"type":"spacer","width":6},
   {"type":"button","icon":"chevron_right","style":"tonal","color":"@inverseOnSurface/15","contentColor":"@inverseOnSurface","onClick":{"do":"cycle","bind":"p","count":3}}]}]}}
"""),
        Sample("newyear", """
{"name":"${t("Новый год", "New Year")}","size":"2x1","background":"@tertiary","corner":24,"padding":{"h":16,"v":8},
 "root":{"type":"row","height":"fill","valign":"center","gap":10,"children":[
  {"type":"text","text":"{days_until:01-01}","size":36,"fontWeight":700,"font":"condensed","color":"@onTertiary"},
  {"type":"column","weight":1,"children":[
   {"type":"text","text":"${t("дней до", "days until")}","size":12,"color":"@onTertiary/75"},
   {"type":"text","text":"${t("Нового года", "New Year")}","size":14,"fontWeight":600,"color":"@onTertiary","maxLines":1}]}]}}
"""),
        Sample("progress", """
{"name":"${t("Прогресс", "Progress")}","size":"4x1","background":"@surfaceContainer","padding":{"h":16,"v":10},
 "root":{"type":"row","height":"fill","valign":"center","gap":14,"children":[
  {"type":"column","weight":1,"gap":6,"children":[
   {"type":"row","children":[{"type":"text","text":"${t("День", "Day")}","size":12,"fontWeight":600,"color":"@onSurface","weight":1},{"type":"text","text":"{day_progress}%","size":11,"font":"mono","color":"@onSurfaceVariant"}]},
   {"type":"progress","value":"{day_progress}","color":"@primary","track":"@onSurface/10","thickness":6}]},
  {"type":"column","weight":1,"gap":6,"children":[
   {"type":"row","children":[{"type":"text","text":"${t("Неделя", "Week")}","size":12,"fontWeight":600,"color":"@onSurface","weight":1},{"type":"text","text":"{week_progress}%","size":11,"font":"mono","color":"@onSurfaceVariant"}]},
   {"type":"progress","value":"{week_progress}","color":"@secondary","track":"@onSurface/10","thickness":6}]},
  {"type":"column","weight":1,"gap":6,"children":[
   {"type":"row","children":[{"type":"text","text":"${t("Год", "Year")}","size":12,"fontWeight":600,"color":"@onSurface","weight":1},{"type":"text","text":"{year_progress}%","size":11,"font":"mono","color":"@onSurfaceVariant"}]},
   {"type":"progress","value":"{year_progress}","color":"@tertiary","track":"@onSurface/10","thickness":6}]}]}}
"""),
        Sample("world", """
{"name":"${t("Мировое время", "World clock")}","size":"4x1","background":"#16181D","theme":"dark","padding":{"h":18,"v":8},
 "root":{"type":"row","height":"fill","valign":"center","children":[
  {"type":"column","weight":1,"children":[{"type":"text","text":"${t("Москва", "Moscow")}","size":11,"color":"#FFFFFF/55"},{"type":"clock","format":"HH:mm","tz":"Europe/Moscow","size":24,"fontWeight":500,"color":"#FFFFFF"}]},
  {"type":"divider","color":"#FFFFFF/12"},
  {"type":"column","weight":1,"align":"center","children":[{"type":"text","text":"${t("Лондон", "London")}","size":11,"color":"#FFFFFF/55"},{"type":"clock","format":"HH:mm","tz":"Europe/London","size":24,"fontWeight":500,"color":"#FFFFFF"}]},
  {"type":"divider","color":"#FFFFFF/12"},
  {"type":"column","weight":1,"align":"end","children":[{"type":"text","text":"${t("Нью-Йорк", "New York")}","size":11,"color":"#FFFFFF/55"},{"type":"clock","format":"HH:mm","tz":"America/New_York","size":24,"fontWeight":500,"color":"#FF8F6B"}]}]}}
"""),
    )

    fun parsed(): List<Pair<String, WidgetSpec>> = all().mapNotNull { s ->
        runCatching { s.id to SpecParser.parse(s.json) }.getOrNull()
    }
}

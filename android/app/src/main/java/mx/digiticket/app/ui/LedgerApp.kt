package mx.digiticket.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.digiticket.app.LedgerViewModel
import mx.digiticket.app.domain.*
import mx.digiticket.app.ui.theme.Mint
import mx.digiticket.app.ui.theme.Pine
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val monthFormat = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", mexicanLocale)
internal val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", mexicanLocale)
internal val corner = RoundedCornerShape(16.dp)
private val tabs = listOf("Inicio", "Historial", "Pendientes", "Excel")

@Composable
fun LedgerApp(model: LedgerViewModel, capture: (CaptureSource, String) -> Unit, onExport: (Boolean) -> Unit) {
    val snacks = remember { SnackbarHostState() }
    val note = model.message
    LaunchedEffect(note) { if (note.isNotEmpty()) { snacks.showSnackbar(note); if (model.message == note) model.message = "" } }
    var leave by rememberSaveable { mutableStateOf(false) }
    var privacy by rememberSaveable { mutableStateOf(false) }
    val draft = model.draft
    val back = {
        if (!model.busy) {
            if (model.isDirty()) leave = true else model.closeEditor()
        }
    }
    BackHandler(enabled = draft != null, onBack = back)
    BackHandler(enabled = draft == null && model.tab != "Inicio") { model.navigate("Inicio") }
    if (leave) AlertDialog(onDismissRequest = { leave = false }, title = { Text("¿Salir sin guardar los cambios?") },
        text = { Text("También puedes guardar este movimiento como pendiente y terminarlo después.") },
        confirmButton = { TextButton(onClick = { leave = false; model.closeEditor() }) { Text("Salir sin guardar") } },
        dismissButton = { TextButton(onClick = { leave = false }) { Text("Seguir editando") } })
    if (privacy) AlertDialog(onDismissRequest = { privacy = false }, title = { Text("Tus datos en DigiTicket") },
        text = { Text("Los movimientos, fotos y lecturas se guardan en este dispositivo. El reconocimiento de texto se realiza en el teléfono. El escáner y el modelo de Google pueden requerir una descarga inicial. ML Kit puede enviar a Google datos técnicos de uso y rendimiento.\n\nAl compartir o guardar un Excel, tú eliges su destino. Puedes eliminar un movimiento con su foto desde el historial. Si desinstalas la app, se borran los registros locales. Los Excel exportados se conservan donde los guardaste.") },
        confirmButton = { TextButton(onClick = { privacy = false }) { Text("Entendido") } })
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snacks) },
        bottomBar = {
            if (draft == null) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(selected = model.tab == title, onClick = {
                        if (title == "Historial") { model.historyType = ""; model.historyCategory = "" }
                        model.navigate(title)
                    }, icon = { NavGlyph(index) }, label = { Text(title, fontSize = 11.sp, maxLines = 1) })
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (model.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (!model.ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (model.busy) Text("Abriendo tus movimientos…") else Button(onClick = model::reload) { Text("Reintentar") }
                }
            } else if (draft != null) {
                key(draft.id) { EntryEditor(draft, model, back, capture) }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Pine),
                            contentAlignment = Alignment.Center) { ReceiptMark() }
                        Text("DigiTicket", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { privacy = true }) { Text("Privacidad", fontSize = 12.sp) }
                }
                val records = remember(model.entries, model.month) { model.entries.filter { it.month == model.month }
                    .sortedByDescending { it.parsedDate ?: it.created } }
                when (model.tab) {
                    "Historial", "Pendientes" -> History(model, records, model.tab == "Pendientes")
                    "Excel" -> Export(model, records, onExport)
                    else -> Dashboard(model, records)
                }
            }
        }
    }
}

@Composable
private fun ReceiptMark() = Canvas(Modifier.size(18.dp)) {
    val w = size.width; val h = size.height
    drawRect(Mint, Offset(w*.17f,h*.08f), Size(w*.66f,h*.83f), style = Stroke(w*.085f))
    for (y in listOf(.32f,.50f,.68f)) drawLine(Mint, Offset(w*.32f,h*y), Offset(w*.67f,h*y), w*.07f)
}

@Composable
private fun NavGlyph(index: Int) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(21.dp)) {
        val w = size.width
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(ink, Offset(x*w,y*w),Offset(x2*w,y2*w),w*.08f)
        when (index) {
            0 -> { line(.08f,.45f,.5f,.1f); line(.5f,.1f,.92f,.45f); line(.22f,.36f,.22f,.9f); line(.22f,.9f,.78f,.9f); line(.78f,.9f,.78f,.36f) }
            1 -> for (y in listOf(.24f,.49f,.74f)) { line(.12f,y,.25f,y); line(.36f,y,.90f,y) }
            2 -> { drawCircle(ink,w*.39f,style=Stroke(w*.08f)); line(.5f,.22f,.5f,.5f); line(.5f,.5f,.68f,.62f) }
            else -> { drawRect(ink,Offset(w*.1f,w*.16f),Size(w*.8f,w*.69f),style=Stroke(w*.08f)); line(.1f,.4f,.9f,.4f); line(.44f,.4f,.44f,.85f); line(.1f,.62f,.9f,.62f) }
        }
    }
}

@Composable
internal fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

@Composable
private fun Period(model: LedgerViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { model.selectMonth(model.month.minusMonths(1)) },
            modifier = Modifier.semantics { contentDescription = "Mes anterior" }) { Text("‹", fontSize = 24.sp) }
        Text(model.month.format(monthFormat).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { model.selectMonth(model.month.plusMonths(1)) },
            modifier = Modifier.semantics { contentDescription = "Mes siguiente" }) { Text("›", fontSize = 24.sp) }
    }
}

@Composable
private fun Dashboard(model: LedgerViewModel, records: List<Entry>) {
    val summary = remember(records, model.month) { monthlySummary(records, model.month) }
    val previous = remember(model.entries, model.month) { monthlySummary(model.entries, model.month.minusMonths(1)) }
    fun history(type: String = "", category: String = "") { model.historyType = type; model.historyCategory = category; model.navigate("Historial") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading("Mi negocio")
        Period(model)
        Column(Modifier.fillMaxWidth().clip(corner).background(Pine).clickable { history() }.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Resultado estimado", color = Mint, fontWeight = FontWeight.SemiBold)
            Text(pesos(summary.resultInCents), color = Color.White, fontSize = 35.sp, fontWeight = FontWeight.Bold)
            Text("Ventas menos gastos confirmados · MXN", color = Mint, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric("Ventas", pesos(summary.salesInCents), Modifier.weight(1f)) { history(MovementType.SALE.label) }
            Metric("Gastos", pesos(summary.expensesInCents), Modifier.weight(1f)) { history(MovementType.EXPENSE.label) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.start(MovementType.SALE) }, modifier = Modifier.weight(1f)) { Text("Registrar venta") }
            OutlinedButton(onClick = { model.start(MovementType.EXPENSE) }, modifier = Modifier.weight(1f)) { Text("Registrar gasto") }
        }
        val pending = records.count { !it.confirmed }
        TextButton(onClick = { model.navigate("Pendientes") }) { Text("Pendientes de revisar ($pending)  ›") }
        if (records.isEmpty()) Info("Tu mes empieza aquí", "Registra tu primera venta o gasto. Puedes escribir el importe o tomar una foto del ticket.")
        else {
            val prev = model.entries.any { it.month == model.month.minusMonths(1) && it.confirmed }
            if (prev) {
                val diff = summary.resultInCents - previous.resultInCents
                Text("El resultado ${if (diff >= 0) "subió" else "bajó"} ${pesos(kotlin.math.abs(diff))} frente al mes anterior.")
            }
            val spending = records.filter { it.confirmed && it.type == MovementType.EXPENSE }
                .groupBy { it.category.ifBlank { "Sin categoría" } }.mapValues { (_, es) -> es.sumOf { parseCents(it.total) ?: 0L } }
            if (spending.isNotEmpty()) {
                Heading("Gastos por categoría")
                spending.entries.sortedByDescending { it.value }.forEach { (name, amount) ->
                    Row(Modifier.fillMaxWidth().clickable { history(MovementType.EXPENSE.label,name) }.padding(vertical=9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Text(pesos(amount), fontWeight=FontWeight.Bold) }
                }
            }
            Heading("Últimos movimientos")
            records.take(4).forEach { EntryRow(it, model::edit) }
            TextButton(onClick = { history() }) { Text("Ver los ${records.size} movimientos") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, click: () -> Unit) =
    Surface(modifier = modifier.clickable(onClick = click), shape = corner, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) { Text(label, fontSize = 13.sp); Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
    }

@Composable
internal fun Info(title: String, message: String) =
    Surface(shape = corner, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold); Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }

@Composable
private fun EntryRow(entry: Entry, open: (Entry) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { open(entry) }.padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.description.ifBlank { entry.merchant.ifBlank { entry.source.label } }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text("${entry.parsedDate?.format(dayFormat) ?: "Fecha por revisar"} · ${entry.type.label}", fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(parseCents(entry.total)?.let(::pesos) ?: "Sin importe", fontWeight = FontWeight.Bold)
            Text(entry.review.label, fontSize = 12.sp)
        }
    }
    HorizontalDivider()
}

@Composable
private fun History(model: LedgerViewModel, records: List<Entry>, pending: Boolean) {
    var search by rememberSaveable(pending) { mutableStateOf("") }
    val selected = records.filter { (!pending || !it.confirmed) &&
        (pending || model.historyType.isBlank() || it.type.label == model.historyType) &&
        (pending || model.historyCategory.isBlank() || it.category.ifBlank { "Sin categoría" } == model.historyCategory) &&
        (it.description.contains(search,true) || it.merchant.contains(search,true)) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp), verticalArrangement=Arrangement.spacedBy(7.dp)) {
        item { Heading(if (pending) "Por revisar" else "Historial") }
        item { Period(model) }
        item { OutlinedTextField(value=search,onValueChange={search=it},label={Text("Buscar por descripción o comercio")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
        if (!pending) item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Choice("Tipo",model.historyType,listOf("")+MovementType.entries.map { it.label },Modifier.weight(1f),"Todos") { model.historyType=it }
                Choice("Categoría",model.historyCategory,listOf("")+records.map { it.category.ifBlank { "Sin categoría" } }.distinct().sorted(),Modifier.weight(1f),"Todas") { model.historyCategory=it }
            }
        }
        if (selected.isEmpty()) item {
            Info(if (pending) "Todo al día" else "Sin movimientos para esta búsqueda",
                if (pending) "Los tickets que guardes para revisar aparecerán aquí." else "Cambia el mes o los filtros para buscar otros registros.")
        }
        items(selected, key={it.id}) { EntryRow(it,model::edit) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Export(model: LedgerViewModel, records: List<Entry>, onExport: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
        verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Heading("Exportar a Excel")
        Period(model)
        Info("Reporte del mes", "${records.count { it.confirmed }} confirmados · ${records.count { !it.confirmed }} por revisar\nEl resultado estimado incluye únicamente movimientos confirmados.")
        Text("El archivo incluye las hojas Resumen, Movimientos y Productos, con fechas e importes editables en Excel.")
        Button(onClick={onExport(false)},modifier=Modifier.fillMaxWidth()) { Text("Guardar archivo .xlsx") }
        OutlinedButton(onClick={onExport(true)},modifier=Modifier.fillMaxWidth()) { Text("Compartir Excel") }
    }
}

@Composable
internal fun Choice(label: String, selected: String, options: List<String>, modifier: Modifier = Modifier, empty: String = "Seleccionar", change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(label,fontSize=11.sp); Text(selected.ifBlank { empty } + " ▾",maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            options.forEach { value -> DropdownMenuItem(text={Text(value.ifBlank { empty })},onClick={change(value);open=false}) }
        }
    }
}

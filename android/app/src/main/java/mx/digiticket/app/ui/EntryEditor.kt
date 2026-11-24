package mx.digiticket.app.ui

import android.app.DatePickerDialog
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.digiticket.app.LedgerViewModel
import mx.digiticket.app.domain.*
import java.io.File
import java.time.LocalDate

@Composable
internal fun EntryEditor(entry: Entry, model: LedgerViewModel, back: () -> Unit, capture: (CaptureSource, String) -> Unit) {
    val context = LocalContext.current
    var advanced by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var duplicate by rememberSaveable { mutableStateOf(false) }
    var seeText by rememberSaveable { mutableStateOf(false) }
    var seePhoto by rememberSaveable { mutableStateOf(false) }
    var attaching by rememberSaveable { mutableStateOf(false) }
    var hand by rememberSaveable { mutableStateOf(entry.source == CaptureSource.HANDWRITTEN_RECEIPT) }
    val change: (Entry) -> Unit = model::change
    val photo = produceState<android.graphics.Bitmap?>(null, entry.receiptId) {
        value = withContext(Dispatchers.IO) {
            entry.receiptId?.let { id -> BitmapFactory.decodeFile(File(context.filesDir, "receipts/$id.jpg").path,
                BitmapFactory.Options().apply { inSampleSize = 2 }) }
        }
    }.value
    if (seePhoto && photo != null) Dialog(onDismissRequest = { seePhoto = false }) {
        Surface(shape=corner) { Column(Modifier.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Image(photo.asImageBitmap(),"Comprobante completo", Modifier.fillMaxWidth().heightIn(max=520.dp),contentScale=ContentScale.Fit)
            TextButton(onClick={seePhoto=false}) { Text("Cerrar foto") }
        } }
    }
    if (deleting) AlertDialog(onDismissRequest={deleting=false}, title={Text("¿Eliminar este movimiento?")},
        text={Text("También se eliminarán su foto y la lectura guardada en el teléfono.")},
        confirmButton={TextButton(onClick={deleting=false;model.delete()}){Text("Eliminar")}},
        dismissButton={TextButton(onClick={deleting=false}){Text("Cancelar")}})
    if (duplicate) AlertDialog(onDismissRequest={duplicate=false}, title={Text("Hay un movimiento parecido")},
        text={Text("Coinciden fecha, tipo, descripción e importe. Confirma solo si se trata de otra operación.")},
        confirmButton={TextButton(onClick={duplicate=false;model.save(Review.CONFIRMED)}){Text("Son distintos")}},
        dismissButton={TextButton(onClick={duplicate=false}){Text("Volver a revisar")}})
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=back,enabled=!model.busy) { Text("‹  Volver") }
        Heading(if (entry.receiptId != null) "Revisar comprobante" else if (entry.confirmed) "Editar movimiento" else "Registrar movimiento")
        Choice("Tipo de movimiento",entry.type.label,MovementType.entries.map { it.label }) { label ->
            change(entry.copy(type=MovementType.entries.first { it.label == label }, category=""))
        }
        if (entry.receiptId == null) {
            OutlinedButton(onClick={attaching=!attaching},modifier=Modifier.fillMaxWidth()) {
                Text(if (attaching) "Ocultar opciones de foto" else "Añadir foto de comprobante")
            }
            if (attaching) {
                Info("Foto del comprobante","Incluye las cuatro esquinas, acerca el ticket y evita sombras, inclinación y reflejos. El escáner permite recortar y girar.")
                Choice("Documento",if(hand) "Nota a mano" else "Ticket impreso",listOf("Ticket impreso","Nota a mano")) { hand=it=="Nota a mano" }
                val source = if(hand) CaptureSource.HANDWRITTEN_RECEIPT else CaptureSource.PRINTED_RECEIPT
                Button(onClick={capture(source,"scan")},enabled=!model.busy,modifier=Modifier.fillMaxWidth()){Text("Escanear y recortar")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={capture(source,"camera")},enabled=!model.busy,modifier=Modifier.weight(1f)){Text("Cámara")}
                    OutlinedButton(onClick={capture(source,"gallery")},enabled=!model.busy,modifier=Modifier.weight(1f)){Text("Galería")}
                }
            }
        } else {
            Info(entry.source.label,if(entry.confirmed) "Compara los campos con la foto antes de guardar tus cambios."
                else if(entry.source==CaptureSource.HANDWRITTEN_RECEIPT) "La letra a mano puede ser difícil de leer. Revisa el importe, la fecha y cada producto."
                else "Comprueba la fecha y el total. Hasta que confirmes, este ticket queda fuera del resultado mensual.")
            if (photo!=null) Image(photo.asImageBitmap(),"Abrir foto del comprobante",
                Modifier.fillMaxWidth().height(150.dp).clickable{seePhoto=true},contentScale=ContentScale.Fit)
            if (model.reading) {
                LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                TextButton(onClick=model::stopReading){Text("Continuar a mano")}
            } else if (!entry.confirmed) TextButton(onClick=model::readReceipt,enabled=!model.busy){Text("Reintentar lectura")}
            if(model.evidence.isNotBlank()){
                TextButton(onClick={seeText=!seeText}){Text(if(seeText) "Ocultar lectura original" else "Ver lectura original")}
                if(seeText) Text(model.evidence,style=MaterialTheme.typography.bodySmall)
            }
        }
        val date = entry.parsedDate ?: LocalDate.now()
        OutlinedButton(onClick={
            DatePickerDialog(context,{_,y,m,d->change(entry.copy(date=LocalDate.of(y,m+1,d).toString()))},date.year,date.monthValue-1,date.dayOfMonth).show()
        },modifier=Modifier.fillMaxWidth(),shape=corner) { Text("Fecha · ${entry.parsedDate?.format(dayFormat) ?: "Seleccionar"}") }
        Field("Descripción",entry.description,example="Ej. Venta de 3 cuadernos") { change(entry.copy(description=it)) }
        Field("Total en pesos",entry.total,number=true,example="Ej. 125.50") { change(entry.copy(total=it)) }
        Choice("Categoría",entry.category,listOf("")+categories(entry.type),empty="Sin categoría"){ change(entry.copy(category=it)) }
        TextButton(onClick={advanced=!advanced}) { Text(if(advanced) "Ocultar detalles" else "Más detalles y productos (${entry.items.size})") }
        if(advanced){
            Field("Comercio o emisor",entry.merchant){change(entry.copy(merchant=it))}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Field("Subtotal",entry.subtotal,Modifier.weight(1f),number=true){change(entry.copy(subtotal=it))}
                Field("Impuestos",entry.tax,Modifier.weight(1f),number=true){change(entry.copy(tax=it))}
            }
            Choice("Forma de pago",entry.payment,listOf("","Efectivo","Tarjeta","Transferencia","Otro"),empty="Sin especificar"){change(entry.copy(payment=it))}
            Choice("Estado del pago",entry.paymentStatus,listOf("","Pagado","Pendiente de pago"),empty="Sin especificar"){change(entry.copy(paymentStatus=it))}
            Field("Hora",entry.time,example="Ej. 14:30"){change(entry.copy(time=it))}
            Field("Notas",entry.notes,multiline=true){change(entry.copy(notes=it))}
            Text("Productos o servicios",fontWeight=FontWeight.Bold)
            entry.items.forEachIndexed { i, item ->
                fun replace(it: LineItem) = change(entry.copy(items=entry.items.toMutableList().apply { set(i,it) }))
                Field("Producto ${i+1}",item.description){replace(item.copy(description=it))}
                Field("Cantidad",item.quantity,number=true){replace(item.copy(quantity=it))}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Field("Precio unitario",item.unitPrice,Modifier.weight(1f),number=true){replace(item.copy(unitPrice=it))}
                    Field("Importe",item.amount,Modifier.weight(1f),number=true){replace(item.copy(amount=it))}
                }
                TextButton(onClick={change(entry.copy(items=entry.items.filterIndexed{j,_->j!=i}))}){Text("Quitar producto ${i+1}")}
            }
            OutlinedButton(onClick={change(entry.copy(items=entry.items+LineItem("")))},enabled=entry.items.size<80){Text("Añadir producto")}
        }
        Button(onClick={
            val error=entry.errorForConfirmation()
            if(error!=null) model.message=error
            else if(model.entries.any(entry::possibleDuplicate)) duplicate=true
            else model.save(Review.CONFIRMED)
        },enabled=!model.busy && !model.reading,modifier=Modifier.fillMaxWidth()){
            Text(if(entry.confirmed) "Guardar cambios" else "Confirmar movimiento")
        }
        if(!entry.confirmed){
            OutlinedButton(onClick={model.save(Review.PENDING)},enabled=!model.busy && !model.reading,modifier=Modifier.fillMaxWidth()){Text("Guardar como pendiente")}
            if(entry.receiptId!=null) {
                TextButton(onClick={model.save(Review.REVIEWED)},enabled=!model.busy && !model.reading){Text("Marcar revisado y confirmar después")}
                TextButton(onClick={model.save(Review.ILLEGIBLE)},enabled=!model.busy && !model.reading){Text("Marcar comprobante ilegible")}
            }
        }
        if(model.entries.any{it.id==entry.id}) TextButton(onClick={deleting=true},enabled=!model.busy && !model.reading){Text("Eliminar movimiento",color=MaterialTheme.colorScheme.error)}
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier, number: Boolean = false,
    example: String = "", multiline: Boolean = false, change: (String) -> Unit) {
    OutlinedTextField(value=value,onValueChange={change(it.take(if(multiline) 2000 else 180))},label={Text(label)},
        placeholder=if(example.isEmpty()) null else { { Text(example) } }, singleLine=!multiline,
        keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Decimal else KeyboardType.Text),modifier=modifier.fillMaxWidth())
}

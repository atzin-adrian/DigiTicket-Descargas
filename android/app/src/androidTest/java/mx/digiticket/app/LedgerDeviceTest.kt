package mx.digiticket.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import mx.digiticket.app.data.local.*
import mx.digiticket.app.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.util.UUID

class LedgerDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun recordSaleAndFindItAfterActivityRecreation() {
        val label = "Prueba " + UUID.randomUUID().toString().take(7)
        compose.onNodeWithText("Registrar venta").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("Descripción")).performScrollTo().performTextInput(label)
        compose.onNode(hasSetTextAction() and hasText("Total en pesos")).performScrollTo().performTextInput("125.50")
        compose.onNodeWithText("Confirmar movimiento").performScrollTo().performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithText("Mi negocio").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Historial").performClick()
        compose.onNodeWithText(label).assertExists()
    }
    @Test fun photoAndCorrectionSurviveReopeningAndDeleteTogether() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val temp=File.createTempFile("synthetic-",".png",ctx.cacheDir)
        val bitmap=Bitmap.createBitmap(60,120,Bitmap.Config.ARGB_8888)
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        val image=ReceiptImageStorage(ctx).save(Uri.fromFile(temp))
        val pending=Entry(id=UUID.randomUUID().toString(),source=CaptureSource.HANDWRITTEN_RECEIPT,receiptId=image.id)
        LedgerStore(ctx).use { it.save(pending) }
        LedgerStore(ctx).use { store ->
            val row=store.all().first{it.id==pending.id}
            assertFalse(row.confirmed)
            val confirmed=row.copy(review=Review.CONFIRMED,date=LocalDate.now().toString(),description="Compra de prueba",total="37.90")
            store.save(confirmed)
            assertEquals(confirmed,store.all().first{it.id==row.id})
            store.delete(confirmed)
            assertFalse(store.all().any{it.id==row.id});assertFalse(File(image.absolutePath).exists())
        }
        temp.delete()
        Unit
    }
}

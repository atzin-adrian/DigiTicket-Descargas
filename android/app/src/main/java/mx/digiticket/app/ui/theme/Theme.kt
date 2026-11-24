package mx.digiticket.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Pine = Color(0xFF124D42)
internal val Mint = Color(0xFFD6F3D8)
private val light = lightColorScheme(
    primary = Pine, onPrimary = Color.White, primaryContainer = Mint, onPrimaryContainer = Color(0xFF103E35),
    secondary = Color(0xFF546348), tertiary = Color(0xFF815200), background = Color(0xFFF7F8F2),
    surface = Color(0xFFFEFFF8), onSurface = Color(0xFF172923), onSurfaceVariant = Color(0xFF46594E),
    surfaceVariant = Color(0xFFE7EBE1), outline = Color(0xFF75847A)
)
private val dark = darkColorScheme(primary = Color(0xFF9DDBBB), primaryContainer = Pine,
    onPrimaryContainer = Mint, surface = Color(0xFF16241F), background = Color(0xFF101D18),
    onSurface = Color(0xFFE9F3E9), onSurfaceVariant = Color(0xFFC1D2C5))
@Composable
fun DigiTicketTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) dark else light, typography = Typography, content = content)
}

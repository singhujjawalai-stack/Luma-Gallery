package com.ujjaw.lumagallery.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.navigation.NavHostController
import com.ujjaw.lumagallery.data.MediaLib
import com.ujjaw.lumagallery.util.decodeScaled
import com.ujjaw.lumagallery.util.rotateBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private val FILTER_NAMES = listOf(
    "None", "Grayscale", "Sepia", "Warm", "Cool", "Vivid", "Contrast", "Invert"
)

private fun saturationValues(s: Float): FloatArray {
    val inv = 1f - s
    val r = 0.3086f
    val g = 0.6094f
    val b = 0.0820f
    return floatArrayOf(
        r * inv + s, g * inv, b * inv, 0f, 0f,
        r * inv, g * inv + s, b * inv, 0f, 0f,
        r * inv, g * inv, b * inv + s, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/** Raw 20-value color matrix per filter, or null for "None". */
private fun matrixValues(index: Int): FloatArray? {
    return when (index) {
        1 -> floatArrayOf(
            0.3086f, 0.6094f, 0.0820f, 0f, 0f,
            0.3086f, 0.6094f, 0.0820f, 0f, 0f,
            0.3086f, 0.6094f, 0.0820f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        2 -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        3 -> floatArrayOf(
            1.12f, 0f, 0f, 0f, 0f,
            0f, 1.02f, 0f, 0f, 0f,
            0f, 0f, 0.88f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        4 -> floatArrayOf(
            0.92f, 0f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 1.12f, 0f, 12f,
            0f, 0f, 0f, 1f, 0f
        )
        5 -> saturationValues(1.55f)
        6 -> {
            val c = 1.35f
            val t = (0.5f - 0.5f * c) * 255f
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )
        }
        7 -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
        else -> null
    }
}

/** Applies `a` first, then `b` — returns the combined 20-value color matrix. */
private fun concatColor(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (i in 0 until 4) {
        for (j in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += b[i * 5 + k] * a[k * 5 + j]
            out[i * 5 + j] = s
        }
        var t = 0f
        for (k in 0 until 4) t += b[i * 5 + k] * a[k * 5 + 4]
        out[i * 5 + 4] = t + b[i * 5 + 4]
    }
    return out
}

/** Contrast (1.0 = normal) + brightness offset (-127..127) in one matrix. */
private fun contrastBrightness(contrast: Float, brightness: Float): FloatArray {
    val c = contrast.coerceIn(0f, 3f)
    val t = (0.5f - 0.5f * c) * 255f
    return floatArrayOf(
        c, 0f, 0f, 0f, t + brightness,
        0f, c, 0f, 0f, t + brightness,
        0f, 0f, c, 0f, t + brightness,
        0f, 0f, 0f, 1f, 0f
    )
}

private val IDENTITY_MATRIX = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

private fun isIdentity(m: FloatArray?): Boolean {
    if (m == null) return true
    val identity = IDENTITY_MATRIX
    for (i in 0 until 20) {
        if (kotlin.math.abs(m[i] - identity[i]) > 0.001f) return false
    }
    return true
}

private class DrawStroke(val color: Color, val points: MutableList<Offset> = mutableListOf())

private class FitInfo(val scale: Float, val ox: Float, val oy: Float)

private val DRAW_COLORS = listOf(
    Color(0xFFFF1744), Color.White, Color(0xFFFFEA00),
    Color(0xFF00E676), Color(0xFF00E5FF), Color.Black
)

@Composable
fun EditorScreen(nav: NavHostController, mediaId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val item = remember(mediaId) {
        MediaLib.items.filter { !it.isVideo }.firstOrNull { it.id == mediaId }
    }
    var loadFailed by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item?.uri) {
        if (item == null) {
            loadFailed = true
            return@LaunchedEffect
        }
        val decoded = withContext(Dispatchers.IO) {
            decodeScaled(context, android.net.Uri.parse(item.uri), 2048)
        }
        bitmap = decoded?.let { if (it.isMutable) it else it.copy(Bitmap.Config.ARGB_8888, true) }
        loadFailed = decoded == null
    }
    val bmp = bitmap
    if (bmp == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (loadFailed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't open this photo for editing", color = Color.White, fontSize = 15.sp)
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text("Go back", color = Color(0xFF82B1FF))
                    }
                }
            } else {
                CircularProgressIndicator(color = Color(0xFF82B1FF))
            }
        }
        return
    }

    var filterIdx by remember { mutableIntStateOf(0) }
    var drawMode by remember { mutableStateOf(false) }
    var cropMode by remember { mutableStateOf(false) }
    var adjustMode by remember { mutableStateOf(false) }
    var brightnessPct by remember { mutableIntStateOf(0) } // -100..100
    var contrastPct by remember { mutableIntStateOf(0) }   // -100..100
    var saturationPct by remember { mutableIntStateOf(0) } // -100..100
    var aspectIdx by remember { mutableIntStateOf(0) }     // 0 = free
    var drawColor by remember { mutableStateOf(DRAW_COLORS.first()) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var cropRect by remember { mutableStateOf<Rect?>(null) }
    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var liveStroke by remember { mutableStateOf<DrawStroke?>(null) }
    var saving by remember { mutableStateOf(false) }

    val aspects: List<Pair<String, Float?>> = listOf(
        "Free" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f,
        "16:9" to 16f / 9f, "9:16" to 9f / 16f
    )

    fun combinedMatrixValues(): FloatArray? {
        var m: FloatArray? = null
        matrixValues(filterIdx)?.let { f -> m = f }
        if (contrastPct != 0 || brightnessPct != 0) {
            val cb = contrastBrightness(
                1f + contrastPct / 100f,
                brightnessPct * 1.275f
            )
            m = if (m == null) cb else concatColor(m!!, cb)
        }
        if (saturationPct != 0) {
            val sv = saturationValues(1f + saturationPct / 100f)
            m = if (m == null) sv else concatColor(m!!, sv)
        }
        return if (m == null || isIdentity(m)) null else m
    }

    fun fit(): FitInfo {
        val w = containerSize.width.toFloat().coerceAtLeast(1f)
        val h = containerSize.height.toFloat().coerceAtLeast(1f)
        val s = min(w / bmp.width, h / bmp.height)
        return FitInfo(s, (w - bmp.width * s) / 2f, (h - bmp.height * s) / 2f)
    }

    fun drawStrokeInto(target: Bitmap, stroke: DrawStroke) {
        if (stroke.points.size < 2) return
        val f = fit()
        val canvas = AndroidCanvas(target)
        val paint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            strokeWidth = (6f * f.scale).coerceAtLeast(4f)
            color = android.graphics.Color.argb(
                (stroke.color.alpha * 255).toInt(),
                (stroke.color.red * 255).toInt(),
                (stroke.color.green * 255).toInt(),
                (stroke.color.blue * 255).toInt()
            )
        }
        var prev: Offset? = null
        for (p in stroke.points) {
            val bx = ((p.x - f.ox) / f.scale).coerceIn(0f, target.width - 1f)
            val by = ((p.y - f.oy) / f.scale).coerceIn(0f, target.height - 1f)
            val cur = Offset(bx, by)
            prev?.let { canvas.drawLine(it.x, it.y, cur.x, cur.y, paint) }
            prev = cur
        }
    }

    fun commitStrokes() {
        for (s in strokes) drawStrokeInto(bmp, s)
        strokes.clear()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.Filled.Close, "Close", tint = Color.White)
            }
            Text("Edit", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (saving) CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF82B1FF))
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    commitStrokes()
                    val src = bitmap
                    val values = combinedMatrixValues()
                    scope.launch(Dispatchers.IO) {
                        if (src != null) {
                            val result = if (values == null) src else {
                                val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                                val c = AndroidCanvas(out)
                                val p = AndroidPaint().apply {
                                    colorFilter = android.graphics.ColorMatrixColorFilter(values)
                                }
                                c.drawBitmap(src, 0f, 0f, p)
                                out
                            }
                            val ok = MediaLib.saveEditedImage(context, result)
                            withContext(Dispatchers.Main) {
                                saving = false
                                Toast.makeText(
                                    context,
                                    if (ok) "Saved to Pictures/Luma Gallery" else "Could not save",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) {
                                    MediaLib.refresh(context)
                                    nav.popBackStack()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) { saving = false }
                        }
                    }
                }
            ) { Text("Save", color = if (saving) Color.Gray else Color(0xFF82B1FF)) }
        }

        // Preview area
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { containerSize = it }
        ) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = combinedMatrixValues()?.let {
                    ColorFilter.colorMatrix(
                        androidx.compose.ui.graphics.ColorMatrix(it)
                    )
                },
                modifier = Modifier.fillMaxSize()
            )

            // Draw strokes overlay
            Canvas(Modifier.fillMaxSize()) {
                val all = if (liveStroke != null) strokes + listOf(liveStroke!!) else strokes
                for (s in all) {
                    if (s.points.size < 2) continue
                    val path = Path().apply {
                        moveTo(s.points.first().x, s.points.first().y)
                        s.points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, color = s.color, style = Stroke(width = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
            }

            // Draw gesture handler
            if (drawMode) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(drawColor) {
                            detectDragGestures(
                                onDragStart = { liveStroke = DrawStroke(drawColor, mutableListOf(it)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    liveStroke?.points?.add(change.position)
                                },
                                onDragEnd = {
                                    liveStroke?.let { s ->
                                        if (s.points.size > 1) {
                                            strokes.add(s)
                                            drawStrokeInto(bmp, s)
                                            strokes.clear()
                                        }
                                    }
                                    liveStroke = null
                                },
                                onDragCancel = { liveStroke = null }
                            )
                        }
                )
            }

            // Crop overlay
            if (cropMode) {
                val r = cropRect ?: Rect(
                    Offset(containerSize.width * 0.1f, containerSize.height * 0.1f),
                    Size(containerSize.width * 0.8f, containerSize.height * 0.8f)
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                ) {
                    drawRect(Color.Black.copy(alpha = 0.55f))
                    drawRect(
                        color = Color.Transparent,
                        topLeft = r.topLeft,
                        size = r.size,
                        blendMode = BlendMode.Clear
                    )
                    drawRect(color = Color.White, topLeft = r.topLeft, size = r.size, style = Stroke(2f))
                    listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach {
                        drawCircle(Color.White, 10f, it)
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(cropMode) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val cur = cropRect ?: return@detectDragGestures
                                val corners = listOf(
                                    0 to cur.topLeft, 1 to cur.topRight,
                                    2 to cur.bottomLeft, 3 to cur.bottomRight
                                )
                                val (idx, _) = corners.minByOrNull { (_, p) ->
                                    val dx = p.x - change.position.x
                                    val dy = p.y - change.position.y
                                    dx * dx + dy * dy
                                } ?: return@detectDragGestures
                                val w = containerSize.width.toFloat()
                                val h = containerSize.height.toFloat()
                                val x = change.position.x.coerceIn(0f, w)
                                val y = change.position.y.coerceIn(0f, h)
                                val newRect = when (idx) {
                                    0 -> Rect(x, y, cur.right.coerceAtLeast(x + 60f), cur.bottom.coerceAtLeast(y + 60f))
                                    1 -> Rect(cur.left.coerceAtMost(x - 60f), y, x, cur.bottom.coerceAtLeast(y + 60f))
                                    2 -> Rect(x, cur.top.coerceAtMost(y - 60f), cur.right.coerceAtLeast(x + 60f), y)
                                    else -> Rect(cur.left.coerceAtMost(x - 60f), cur.top.coerceAtMost(y - 60f), x, y)
                                }
                                cropRect = newRect
                            }
                        }
                )
            }
        }

        // Filter chips
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(FILTER_NAMES) { i, name ->
                Text(
                    name,
                    color = if (filterIdx == i) Color(0xFF82B1FF) else Color(0xFF999999),
                    fontWeight = if (filterIdx == i) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable {
                            filterIdx = i
                            cropMode = false
                            drawMode = false
                        }
                        .padding(vertical = 10.dp)
                )
            }
        }

        // Adjustments (brightness / contrast / saturation)
        if (adjustMode) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Adjust", color = Color(0xFF999999), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (brightnessPct != 0 || contrastPct != 0 || saturationPct != 0) {
                        TextButton(onClick = {
                            brightnessPct = 0
                            contrastPct = 0
                            saturationPct = 0
                        }) { Text("Reset", color = Color(0xFF82B1FF), fontSize = 12.sp) }
                    }
                }
                listOf(
                    Triple("Brightness", brightnessPct, { v: Int -> brightnessPct = v }),
                    Triple("Contrast", contrastPct, { v: Int -> contrastPct = v }),
                    Triple("Saturation", saturationPct, { v: Int -> saturationPct = v })
                ).forEach { (label, value, setter) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        @OptIn(ExperimentalMaterial3Api::class)
                        androidx.compose.material3.Slider(
                            value = (value + 100) / 200f,
                            onValueChange = { setter((it * 200f).roundToInt() - 100) },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${value}",
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        // Crop aspect ratio chips
        if (cropMode) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(aspects) { i, (label, ratio) ->
                    Text(
                        label,
                        color = if (aspectIdx == i) Color.Black else Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (aspectIdx == i) Color(0xFF82B1FF) else Color(0xFF1B1B24))
                            .clickable {
                                aspectIdx = i
                                if (ratio != null && containerSize != IntSize.Zero) {
                                    val cw = containerSize.width.toFloat() * 0.8f
                                    var rw = cw
                                    var rh = rw / ratio
                                    val chMax = containerSize.height.toFloat() * 0.8f
                                    if (rh > chMax) {
                                        rh = chMax
                                        rw = rh * ratio
                                    }
                                    val cx = containerSize.width / 2f
                                    val cy = containerSize.height / 2f
                                    cropRect = Rect(
                                        Offset(cx - rw / 2, cy - rh / 2),
                                        Size(rw, rh)
                                    )
                                } else if (ratio == null) {
                                    cropRect = Rect(
                                        Offset(containerSize.width * 0.1f, containerSize.height * 0.1f),
                                        Size(containerSize.width * 0.8f, containerSize.height * 0.8f)
                                    )
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Tool row
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .background(Color(0xFF16161E), CircleShape)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                adjustMode = !adjustMode
                cropMode = false
                drawMode = false
            }) { Icon(Icons.Filled.Tune, "Adjust", tint = if (adjustMode) Color(0xFF82B1FF) else Color.White) }

            IconButton(onClick = {
                commitStrokes()
                bitmap = rotateBitmap(bmp, 90f)
                cropRect = null
            }) { Icon(Icons.Filled.RotateRight, "Rotate 90°", tint = Color.White) }

            IconButton(onClick = {
                commitStrokes()
                val m = android.graphics.Matrix().apply {
                    postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
                }
                val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                bitmap = if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true)
                cropRect = null
            }) { Icon(Icons.Filled.Flip, "Flip horizontal", tint = Color.White) }

            IconButton(onClick = {
                commitStrokes()
                val m = android.graphics.Matrix().apply {
                    postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
                }
                val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                bitmap = if (out.isMutable) out else out.copy(Bitmap.Config.ARGB_8888, true)
                cropRect = null
            }) {
                Icon(
                    Icons.Filled.Flip,
                    "Flip vertical",
                    tint = Color.White,
                    modifier = Modifier.rotate(90f)
                )
            }

            IconButton(onClick = {
                cropMode = !cropMode
                drawMode = false
                adjustMode = false
                if (!cropMode) aspectIdx = 0
                if (cropMode && cropRect == null && containerSize != IntSize.Zero) {
                    cropRect = Rect(
                        Offset(containerSize.width * 0.1f, containerSize.height * 0.1f),
                        Size(containerSize.width * 0.8f, containerSize.height * 0.8f)
                    )
                }
            }) { Icon(Icons.Filled.Crop, "Crop", tint = if (cropMode) Color(0xFF82B1FF) else Color.White) }

            IconButton(onClick = {
                drawMode = !drawMode
                cropMode = false
            }) { Icon(Icons.Filled.Draw, "Draw", tint = if (drawMode) Color(0xFF82B1FF) else Color.White) }

            if (drawMode) {
                DRAW_COLORS.forEach { c ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .border(
                                width = if (c == drawColor) 2.dp else 1.dp,
                                color = if (c == drawColor) Color.White else Color(0xFF666666),
                                shape = CircleShape
                            )
                            .background(c, CircleShape)
                            .clickable { drawColor = c }
                    )
                }
            }

            if (cropMode) {
                Button(onClick = {
                    var r = cropRect ?: return@Button
                    val ratio = aspects.getOrNull(aspectIdx)?.second
                    if (ratio != null) {
                        // Snap crop rect to the chosen aspect around its center
                        var w = r.width
                        var h = r.height
                        val byW = w / ratio
                        if (byW <= h) h = byW else w = h * ratio
                        val cx = r.center.x
                        val cy = r.center.y
                        r = Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
                    }
                    val f = fit()
                    val x0 = ((r.left - f.ox) / f.scale).roundToInt().coerceIn(0, bmp.width - 2)
                    val y0 = ((r.top - f.oy) / f.scale).roundToInt().coerceIn(0, bmp.height - 2)
                    val cw = (((r.right - r.left) / f.scale).roundToInt()).coerceIn(2, bmp.width - x0)
                    val ch = (((r.bottom - r.top) / f.scale).roundToInt()).coerceIn(2, bmp.height - y0)
                    commitStrokes()
                    bitmap = Bitmap.createBitmap(bmp, x0, y0, cw, ch)
                    cropRect = null
                    cropMode = false
                }) {
                    Icon(Icons.Filled.Check, null, tint = Color.Black)
                    Text("Crop", color = Color.Black)
                }
            }
        }
        Spacer(Modifier.padding(bottom = 2.dp))
    }
}

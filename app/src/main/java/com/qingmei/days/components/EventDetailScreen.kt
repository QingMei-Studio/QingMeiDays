package com.qingmei.days.components

import android.annotation.SuppressLint
import android.graphics.Picture
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
// 🌟 不引用系统 PushPin，使用你外部定义的
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.qingmei.days.model.LifeEvent
import com.qingmei.days.utils.ImageSaver
import com.qingmei.days.utils.ImageUtils
import com.qingmei.days.utils.calculateDays
import com.qingmei.days.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

// 下载图标
val DownloadIconVector: ImageVector = ImageVector.Builder(
    name = "Download", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color.Black)) { moveTo(19f, 9f); horizontalLineTo(15f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineTo(9f); horizontalLineTo(5f); lineTo(12f, 16f); lineTo(19f, 9f); close() }
    path(fill = SolidColor(Color.Black)) { moveTo(5f, 18f); verticalLineTo(20f); horizontalLineTo(19f); verticalLineTo(18f); horizontalLineTo(5f); close() }
}.build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    event: LifeEvent,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onUpdateImage: (String) -> Unit,
    onToggleTop: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 录制控制器
    val picture = remember { Picture() }

    // 状态管理
    var isFullScreen by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    // 🌟 核心修复1：状态提升。把图片加载提到这里，解决"第一张保存没图"的问题
    var eventBitmap by remember(event.imageUri) { mutableStateOf<ImageBitmap?>(null) }

    // 异步加载图片到 eventBitmap
    LaunchedEffect(event.imageUri) {
        if (event.imageUri != null) {
            withContext(Dispatchers.IO) {
                val bitmap = try {
                    val uriStr = event.imageUri
                    val options = android.graphics.BitmapFactory.Options()

                    // 1. 先只读尺寸
                    options.inJustDecodeBounds = true
                    if (!uriStr.contains("://")) {
                        android.graphics.BitmapFactory.decodeFile(uriStr, options)
                    } else {
                        context.contentResolver.openInputStream(uriStr.toUri())?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, options)
                        }
                    }

                    // 2. 计算缩放 (限制在 1080px 以内，防止 OOM)
                    // Widget 限制很死(200-300px)，App 可以宽容很多
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > 1080 || options.outHeight / sampleSize > 1080) {
                        sampleSize *= 2
                    }
                    options.inSampleSize = sampleSize
                    options.inJustDecodeBounds = false // 准备真读

                    // 3. 真正解码
                    if (!uriStr.contains("://")) {
                        android.graphics.BitmapFactory.decodeFile(uriStr, options)
                    } else {
                        context.contentResolver.openInputStream(uriStr.toUri())?.use {
                            android.graphics.BitmapFactory.decodeStream(it, null, options)
                        }
                    }?.asImageBitmap()

                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
                withContext(Dispatchers.Main) {
                    eventBitmap = bitmap
                }
            }
        } else {
            eventBitmap = null
        }
    }

    val daysLeft = calculateDays(event.date)
    val themeColor = Color(event.color)

    // 🌟 核心修复2：选图器只负责打开裁剪，不直接保存
    // 🌟 终极方案：强行唤起手机自带的完整相册 App
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { originalUri ->
                // 1. 暂存 Uri
                tempImageUri = originalUri
                // 2. 打开裁剪弹窗
                showCropDialog = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- 1. 底层：详情页 ---
        Scaffold(
            containerColor = Color.White,
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.Black) }
                    },
                    actions = {
                        // 🌟 使用你外部定义的 PushPinIcon
                        IconButton(onClick = onToggleTop) {
                            Icon(imageVector = PushPinIcon, contentDescription = "置顶",
                                tint = if (event.isTop) themeColor else Color.LightGray,
                                modifier = Modifier.padding(end = 8.dp))
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                // 🌟 这里的 delay 现在安全了，因为 picture 是实时同步的
                                delay(50)
                                val bitmap = ImageSaver.createBitmapFromPicture(picture)
                                val success = ImageSaver.saveBitmapToGallery(context, bitmap, "轻梅_${event.title}")
                                if (success) context.showToast("✅ 已保存精美卡片！")
                            }
                        }) {
                            Icon(DownloadIconVector, "保存图片", tint = themeColor)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "删除", tint = Color.Red.copy(alpha = 0.6f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onEdit, containerColor = themeColor, contentColor = Color.White,
                    icon = { Icon(Icons.Default.Edit, "编辑") }, text = { Text("修改信息") }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // 🌟 核心修复3：强制关联 eventBitmap
                    .drawWithCache {
                        val width = size.width.toInt()
                        val height = size.height.toInt()

                        // 这一行非常关键：只要图片加载完了，drawWithCache 就会重新执行
                        val trigger = eventBitmap

                        onDrawWithContent {
                            drawContent()
                            val pictureCanvas = picture.beginRecording(width, height)
                            drawIntoCanvas { canvas ->
                                androidx.compose.ui.graphics.Canvas(pictureCanvas).let { nativeCanvas ->
                                    this.draw(this, this.layoutDirection, nativeCanvas, this.size) {
                                        this@onDrawWithContent.drawContent()
                                    }
                                }
                            }
                            picture.endRecording()
                        }
                    }
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = if (daysLeft >= 0) "还有" else "已累计", fontSize = 16.sp, color = Color.Gray)
                Text(text = "${abs(daysLeft)}", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = themeColor, lineHeight = 96.sp)
                Text(text = "DAYS", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = themeColor.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(32.dp))
                Text(text = event.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = themeColor)
                Text(text = "目标日：${event.date}", fontSize = 16.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                Spacer(modifier = Modifier.height(32.dp))

                // 图片点击区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f).aspectRatio(1f)
                        .border(5.dp, themeColor.copy(0.2f), RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp)).background(Color(0xFFF5F5F5))
                        .clickable {
                            if (eventBitmap != null) {
                                isFullScreen = true // 有图：只看大图
                            } else {
                                // 无图：选图
                                val intent = android.content.Intent(android.content.Intent.ACTION_PICK)
                                intent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                                photoPickerLauncher.launch(intent)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 🌟 核心修复4：直接使用父组件加载好的 Bitmap，不再用 ShowUriImage
                    if (eventBitmap != null) {
                        Image(
                            bitmap = eventBitmap!!,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Face, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            Text("点击添加封面图", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("关于这个日子", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = event.description.ifEmpty { "暂时没有描述..." }, color = Color(0xFF333333), lineHeight = 24.sp)
                }

                // 防止 FAB 遮挡
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // --- 2. 全屏大图预览层 ---
        if (isFullScreen && eventBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { isFullScreen = false }, // 点击背景退出全屏
                contentAlignment = Alignment.Center
            ) {
                // 显示大图 (复用 eventBitmap)
                Image(
                    bitmap = eventBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit // 自适应完整显示
                )

                // 底部提供更换入口
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // 🌟 核心修改：增加一个半透明黑色的胶囊背景 Surface
                    Surface(
                        shape = RoundedCornerShape(50), // 圆角胶囊形状
                        color = Color.Black.copy(alpha = 0.5f), // 半透明黑色背景
                        // 加一点外边距让它不要太贴底
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        TextButton(
                            onClick = {
                                isFullScreen = false
                                // 全屏模式下也能换图
                                val intent = android.content.Intent(android.content.Intent.ACTION_PICK)
                                intent.setDataAndType(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                                photoPickerLauncher.launch(intent)
                            },
                            // 确保按钮点击涟漪和文字颜色都是白色
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                            // 稍微调整一下内部 padding 让胶囊看起来更紧凑
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("更换封面图", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // --- 3. 裁剪弹窗 ---
        if (showCropDialog && tempImageUri != null) {
            ImageCropDialog(
                imageUri = tempImageUri!!,
                onDismiss = { showCropDialog = false },
                onConfirm = { croppedBitmap ->
                    showCropDialog = false

                    coroutineScope.launch(Dispatchers.IO) {
                        // 1. 先保存新图片
                        val newPath = ImageUtils.saveBitmapToInternalStorage(context, croppedBitmap)

                        if (newPath != null) {
                            // 🌟 2. 关键点：新图保存成功后，把旧图删掉！
                            // event.imageUri 此时还存着旧路径
                            // 我们判断一下，只有当旧路径也是本地文件时才删（防止删错系统相册的文件）
                            if (event.imageUri != null && !event.imageUri.contains("://")) {
                                ImageUtils.deleteImage(event.imageUri)
                            }

                            // 3. 通知界面更新为新路径
                            withContext(Dispatchers.Main) {
                                onUpdateImage(newPath)
                            }
                        }
                    }
                }
            )
        }

        // 删除确认弹窗
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("确认删除") },
                text = { Text("你确定要彻底忘记 \"${event.title}\" 吗？\n删除后无法恢复。") },
                confirmButton = { TextButton(onClick = { showDeleteDialog = false; onDelete() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) { Text("狠心删除") } },
                dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("我再想想") } },
                containerColor = Color.White
            )
        }
    }
}
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.clipqr.app

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// ---------- Root ----------

@Composable
fun AppRoot(vm: ClipViewModel) {
    var detailId by remember { mutableStateOf<Long?>(null) }
    val tabs = listOf(
        Triple("Library", Icons.Filled.Bookmarks, 0),
        Triple("Scan", Icons.Filled.QrCodeScanner, 1),
        Triple("Create", Icons.Filled.QrCode, 2),
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = vm.tab == index,
                        onClick = { vm.tab = index },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (vm.tab) {
                0 -> LibraryScreen(vm, onOpen = { detailId = it })
                1 -> ScanScreen(vm, onOpen = { detailId = it })
                else -> CreateScreen(vm)
            }
        }
    }
    detailId?.let { id -> ItemDialog(id, vm, onDismiss = { detailId = null }) }
}

// ---------- Shared pieces ----------

fun Category.icon(): ImageVector = when (this) {
    Category.LINK -> Icons.Filled.Link
    Category.EMAIL -> Icons.Filled.Email
    Category.PHONE -> Icons.Filled.Phone
    Category.CODE -> Icons.Filled.Password
    Category.WIFI -> Icons.Filled.Wifi
    Category.CONTACT -> Icons.Filled.Person
    Category.TEXT -> Icons.Filled.TextFields
}

fun Category.color(): Color = when (this) {
    Category.LINK -> Color(0xFF1E88E5)
    Category.EMAIL -> Color(0xFF8E24AA)
    Category.PHONE -> Color(0xFF43A047)
    Category.CODE -> Color(0xFFF4511E)
    Category.WIFI -> Color(0xFF00897B)
    Category.CONTACT -> Color(0xFFD81B60)
    Category.TEXT -> Color(0xFF757575)
}

fun previewText(item: ClipItem): String = when (item.category) {
    Category.WIFI -> QrFormats.parseWifi(item.text)?.let { "Wi-Fi: ${it.ssid}" } ?: item.text
    Category.CONTACT -> "Contact: " + (QrFormats.contactName(item.text) ?: "unnamed")
    else -> item.text.replace('\n', ' ')
}

@Composable
fun CategoryBadge(category: Category) {
    Surface(shape = CircleShape, color = category.color().copy(alpha = 0.15f)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(category.icon(), contentDescription = category.label, tint = category.color())
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
fun QrImage(bitmap: android.graphics.Bitmap, size: Int = 240) {
    Box(
        Modifier
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.size(size.dp))
    }
}

@Composable
fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize)) },
    )
}

@Composable
fun ClipCard(item: ClipItem, vm: ClipViewModel, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    Card(onClick = { onOpen(item.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(item.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(previewText(item), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${item.category.label} · ${item.source.label} · ${DateUtils.getRelativeTimeSpanString(item.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = {
                Actions.copy(ctx, item.text)
                vm.rememberClip(item.text)
            }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy") }
            IconButton(onClick = { vm.togglePin(item.id) }) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = if (item.pinned) "Unpin" else "Pin",
                    tint = if (item.pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ---------- Library ----------

@Composable
fun LibraryScreen(vm: ClipViewModel, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = vm.items
        .filter { item ->
            val matchesFilter = when (filter) {
                "ALL" -> true
                "PINNED" -> item.pinned
                else -> item.category.name == filter
            }
            matchesFilter && (query.isBlank() || item.text.contains(query, ignoreCase = true))
        }
        .sortedByDescending { it.pinned }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ClipQR", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                if (vm.captureClipboard(force = true) == null) {
                    Toast.makeText(ctx, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                }
            }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Save clipboard now") }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Auto-save copied text") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        trailingIcon = { Checkbox(checked = vm.autoCapture, onCheckedChange = null) },
                        onClick = { vm.updateAutoCapture(!vm.autoCapture) },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear unpinned") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; confirmClear = true },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text("Search your clipboard") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
        )

        val filters = listOf("ALL" to "All", "PINNED" to "Pinned") + Category.entries.map { it.name to it.label }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filters) { (key, label) ->
                val count = when (key) {
                    "ALL" -> vm.items.size
                    "PINNED" -> vm.items.count { it.pinned }
                    else -> vm.items.count { it.category.name == key }
                }
                if (key == "ALL" || key == filter || count > 0) {
                    FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text("$label  $count") })
                }
            }
        }

        if (shown.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (vm.items.isEmpty()) "Nothing saved yet" else "No matches", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Copy something in any app, then open ClipQR. It's saved and sorted automatically. " +
                        "You can also use Share → ClipQR from other apps.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { ClipCard(it, vm, onOpen) }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear unpinned items?") },
            text = { Text("Pinned items are kept. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.clearUnpinned(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// ---------- Item detail ----------

@Composable
fun ItemDialog(id: Long, vm: ClipViewModel, onDismiss: () -> Unit) {
    val item = vm.find(id)
    if (item == null) {
        LaunchedEffect(id) { onDismiss() }
        return
    }
    val ctx = LocalContext.current
    val qr = remember(item.text) { Qr.encode(item.text, 600) }
    val warnings = remember(item.text) {
        if (item.category == Category.LINK) LinkSafety.warnings(item.text) else emptyList()
    }
    val wifi = remember(item.text) { QrFormats.parseWifi(item.text) }
    var confirmUrl by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(item.category)
                    Spacer(Modifier.width(10.dp))
                    Text(item.category.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                }
                Spacer(Modifier.height(12.dp))

                if (wifi != null) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Network: ${wifi.ssid}", style = MaterialTheme.typography.titleSmall)
                        if (wifi.password.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Password: " + if (showPassword) wifi.password else "••••••••", modifier = Modifier.weight(1f))
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Show password",
                                    )
                                }
                            }
                        }
                        Text("Security: ${wifi.security}", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    SelectionContainer(Modifier.fillMaxWidth()) {
                        Text(item.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (item.category == Category.LINK) {
                    Spacer(Modifier.height(12.dp))
                    if (warnings.isNotEmpty()) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Be careful with this link", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                                warnings.forEach {
                                    Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                        Text(
                            "No obvious warning signs. Still check the address before signing in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (qr != null) {
                    Spacer(Modifier.height(16.dp))
                    QrImage(qr, 220)
                    Text(
                        "Show this to another phone to send it, no internet needed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    when (item.category) {
                        Category.LINK -> ActionChip("Open", Icons.Filled.Link) {
                            val url = LinkSafety.normalize(item.text)
                            if (warnings.isEmpty()) {
                                Actions.openUrl(ctx, url)
                            } else {
                                confirmUrl = url
                            }
                        }
                        Category.EMAIL -> ActionChip("Send email", Icons.Filled.Email) { Actions.email(ctx, item.text) }
                        Category.PHONE -> ActionChip("Call", Icons.Filled.Phone) { Actions.dial(ctx, item.text) }
                        Category.WIFI -> {
                            if (wifi != null && wifi.password.isNotEmpty()) {
                                ActionChip("Copy password", Icons.Filled.Password) {
                                    Actions.copy(ctx, wifi.password)
                                    vm.rememberClip(wifi.password)
                                }
                            }
                            ActionChip("Wi-Fi settings", Icons.Filled.Wifi) { Actions.wifiSettings(ctx) }
                        }
                        Category.CONTACT -> if (item.text.startsWith("BEGIN:VCARD", true)) {
                            ActionChip("Add to contacts", Icons.Filled.PersonAdd) { Actions.addContact(ctx, item.text) }
                        }
                        else -> {}
                    }
                    ActionChip("Copy", Icons.Filled.ContentCopy) {
                        Actions.copy(ctx, item.text)
                        vm.rememberClip(item.text)
                    }
                    ActionChip("Share", Icons.Filled.Share) { Actions.shareText(ctx, item.text) }
                    if (qr != null) ActionChip("Share QR", Icons.Filled.QrCode) { Actions.shareBitmap(ctx, qr) }
                    ActionChip(if (item.pinned) "Unpin" else "Pin", Icons.Filled.PushPin) { vm.togglePin(item.id) }
                    ActionChip("Delete", Icons.Filled.Delete) {
                        vm.delete(item.id)
                        onDismiss()
                    }
                }
            }
        }
    }

    confirmUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { confirmUrl = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Open this link?") },
            text = {
                Column {
                    Text(url, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    warnings.forEach { Text("• $it") }
                }
            },
            confirmButton = { TextButton(onClick = { Actions.openUrl(ctx, url); confirmUrl = null }) { Text("Open anyway") } },
            dismissButton = { TextButton(onClick = { confirmUrl = null }) { Text("Cancel") } },
        )
    }
}

// ---------- Scan ----------

@Composable
fun ScanScreen(vm: ClipViewModel, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (text != null) {
            error = null
            vm.add(text, Source.SCAN)?.let { onOpen(it.id) }
        }
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val text = Qr.loadBitmap(ctx, uri)?.let { Qr.decode(it) }
            if (text == null) {
                error = "No QR code found in that photo. Try a clearer, closer picture."
            } else {
                error = null
                vm.add(text, Source.SCAN)?.let { onOpen(it.id) }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenTitle("Scan")
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scan any QR code. Works without internet, and every result is saved to your library.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scanLauncher.launch(
                            ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt("Point the camera at a QR code")
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan with camera")
                }
                OutlinedButton(
                    onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan from a photo or screenshot")
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        val recent = vm.items.filter { it.source == Source.SCAN }.take(10)
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Recent scans", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            recent.forEach {
                ClipCard(it, vm, onOpen)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ---------- Create ----------

@Composable
fun CreateScreen(vm: ClipViewModel) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var security by rememberSaveable { mutableStateOf("WPA") }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var org by rememberSaveable { mutableStateOf("") }
    var picked by rememberSaveable { mutableStateOf("") }

    val content = when (mode) {
        0 -> text.trim()
        1 -> if (ssid.isBlank()) "" else QrFormats.wifi(ssid, password, security, hidden)
        2 -> if (name.isBlank() && phone.isBlank() && email.isBlank()) "" else QrFormats.vcard(name, phone, email, org)
        else -> picked
    }
    val bitmap = remember(content) { if (content.isEmpty()) null else Qr.encode(content, 700) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ScreenTitle("Create QR")
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Text / Link", "Wi-Fi", "Contact", "From library").forEachIndexed { i, label ->
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(12.dp))

        when (mode) {
            0 -> OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text, link, IBAN, anything") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Network name (SSID)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (security != "nopass") {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Show password")
                            }
                        },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WPA" to "WPA/WPA2", "WEP" to "WEP", "nopass" to "No password").forEach { (key, label) ->
                        FilterChip(selected = security == key, onClick = { security = key }, label = { Text(label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hidden = !hidden }) {
                    Checkbox(checked = hidden, onCheckedChange = { hidden = it })
                    Text("Hidden network")
                }
            }
            2 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(org, { org = it }, label = { Text("Company (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                val choices = vm.items.sortedByDescending { it.pinned }.take(30)
                if (choices.isEmpty()) {
                    Text("Your library is empty. Copy or scan something first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                choices.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { picked = item.text }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked == item.text, onClick = { picked = item.text })
                        CategoryBadge(item.category)
                        Spacer(Modifier.width(10.dp))
                        Text(previewText(item), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        if (bitmap != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    QrImage(bitmap, 240)
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("Share image", Icons.Filled.Share) { Actions.shareBitmap(ctx, bitmap) }
                        if (mode != 3) {
                            ActionChip("Save to library", Icons.Filled.Save) {
                                vm.add(content, Source.CREATED)
                                Toast.makeText(ctx, "Saved to library", Toast.LENGTH_SHORT).show()
                            }
                        }
                        ActionChip("Copy text", Icons.Filled.ContentCopy) {
                            Actions.copy(ctx, content)
                            vm.rememberClip(content)
                        }
                    }
                }
            }
        } else {
            Text(
                "Fill in the fields above and your QR code appears here instantly.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

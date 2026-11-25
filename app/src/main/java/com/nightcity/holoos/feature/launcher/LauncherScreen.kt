package com.nightcity.holoos.feature.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nightcity.holoos.MainActivity
import com.nightcity.holoos.R
import com.nightcity.holoos.core.designsystem.component.GlitchText
import com.nightcity.holoos.core.designsystem.component.HudButton
import com.nightcity.holoos.core.designsystem.component.HudButtonStyle
import com.nightcity.holoos.core.designsystem.component.HudDivider
import com.nightcity.holoos.core.designsystem.component.HudPanel
import com.nightcity.holoos.core.designsystem.component.ScanlineOverlay
import com.nightcity.holoos.core.designsystem.component.SegmentedBar
import com.nightcity.holoos.core.designsystem.component.StatusTag
import com.nightcity.holoos.core.designsystem.component.glitch
import com.nightcity.holoos.core.designsystem.component.neonBorder
import com.nightcity.holoos.core.designsystem.component.rememberAmbientGlitch
import com.nightcity.holoos.core.designsystem.theme.ChamferShape
import com.nightcity.holoos.core.designsystem.theme.HexagonShape
import com.nightcity.holoos.core.designsystem.theme.HoloColors
import com.nightcity.holoos.core.resources.labelRes
import com.nightcity.holoos.domain.model.AppEntry
import com.nightcity.holoos.domain.model.ContactsResult
import com.nightcity.holoos.domain.model.HoloContact
import com.nightcity.holoos.domain.model.IconFrame
import com.nightcity.holoos.domain.model.IconTint
import com.nightcity.holoos.domain.model.SystemVitals
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun LauncherRoute(viewModel: LauncherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onResume()
    }

    fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.hud_launch_failed, Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, R.string.hud_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(enabled = state.drawerOpen) { viewModel.closeDrawer() }

    val tint = state.iconStyle.tint
    Box(Modifier.fillMaxSize()) {
        HomeHud(
            state = state,
            onOpenDrawer = viewModel::openDrawer,
            onOpenSystem = { start(Intent(context, MainActivity::class.java)) },
            onGrantContacts = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
            onDial = { contact ->
                val number = contact.phoneNumber ?: return@HomeHud
                start(Intent(Intent.ACTION_DIAL, "tel:${number.filterNot(Char::isWhitespace)}".toUri()))
            },
            onOpenContact = { contact ->
                start(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)))
            },
            onDock = { slot -> start(viewModel.dockIntent(slot)) },
            loadPackageIcon = { pkg -> viewModel.packageIcon(pkg, tint) },
            loadContactPhoto = { c -> viewModel.contactPhoto(c, tint) },
        )

        AnimatedVisibility(
            visible = state.drawerOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            AppDrawer(
                state = state,
                onQuery = viewModel::setQuery,
                onClose = { viewModel.closeDrawer() },
                onLaunch = { entry ->
                    if (!viewModel.launch(entry)) Toast.makeText(context, R.string.hud_launch_failed, Toast.LENGTH_SHORT).show()
                },
                onInfo = viewModel::openAppInfo,
                onUninstall = viewModel::uninstall,
                onStyle = viewModel::setIconStyle,
                loadIcon = { entry -> viewModel.appIcon(entry, tint) },
            )
        }
        ScanlineOverlay(lineAlpha = 0.025f)
    }
}

// ---------------------------------------------------------------------------------------
// Home HUD
// ---------------------------------------------------------------------------------------

@Composable
private fun HomeHud(
    state: LauncherUiState,
    onOpenDrawer: () -> Unit,
    onOpenSystem: () -> Unit,
    onGrantContacts: () -> Unit,
    onDial: (HoloContact) -> Unit,
    onOpenContact: (HoloContact) -> Unit,
    onDock: (com.nightcity.holoos.domain.model.DockSlot) -> Unit,
    loadPackageIcon: suspend (String) -> Bitmap?,
    loadContactPhoto: suspend (HoloContact) -> Bitmap?,
) {
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Swipe up anywhere outside the scrolling Holocall list opens the drawer.
                detectVerticalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onVerticalDrag = { _, dy -> dragAccum += dy },
                    onDragEnd = { if (dragAccum < -threshold) onOpenDrawer() },
                )
            }
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        HudTopStrip(onOpenSystem)
        Spacer(Modifier.height(8.dp))
        ChronoPanel(state.vitals)
        Spacer(Modifier.height(10.dp))
        HolocallPanel(
            contacts = state.contacts,
            onGrant = onGrantContacts,
            onDial = onDial,
            onOpen = onOpenContact,
            loadPhoto = loadContactPhoto,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.height(8.dp))
        DrawerHandle(onOpenDrawer)
        Spacer(Modifier.height(6.dp))
        Dock(state.dock, state.iconStyle, onDock, loadPackageIcon)
    }
}

@Composable
private fun HudTopStrip(onOpenSystem: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(HoloColors.CyberYellow))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.hud_brand).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = HoloColors.NeonCyan,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSystem) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.app_name), tint = HoloColors.NeonCyan)
        }
    }
}

@Composable
private fun ChronoPanel(vitals: SystemVitals?) {
    val context = LocalContext.current
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    val is24h = remember { DateFormat.is24HourFormat(context) }
    val timeFmt = remember(is24h) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "hh:mm", Locale.getDefault()) }
    val dayFmt = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    val dateFmt = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()) }

    val battery = vitals?.battery
    val low = battery != null && battery.percent < 15 && !battery.charging
    val accent = if (low) HoloColors.CrimsonRed else HoloColors.NeonCyan

    HudPanel(
        title = stringResource(R.string.hud_chrono),
        accent = accent,
        pulse = low,
        trailing = {
            when {
                low -> StatusTag(stringResource(R.string.hud_relic), HoloColors.CrimsonRed, filled = true)
                battery?.charging == true -> StatusTag(stringResource(R.string.hud_charging), HoloColors.CyberYellow)
                else -> Text("NC-STD", style = MaterialTheme.typography.labelSmall, color = HoloColors.TextDim)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            GlitchText(
                text = now.format(timeFmt),
                style = MaterialTheme.typography.displayLarge,
                color = HoloColors.TextPrimary,
                glitch = rememberAmbientGlitch(minDelayMs = 6_000, maxDelayMs = 14_000),
            )
            Text(
                text = ":%02d".format(now.second),
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                Text(now.format(dayFmt).uppercase(), style = MaterialTheme.typography.headlineSmall, color = HoloColors.CyberYellow)
                Text(now.format(dateFmt), style = MaterialTheme.typography.labelLarge, color = HoloColors.TextDim)
            }
        }
        Spacer(Modifier.height(6.dp))
        HudDivider()
        Spacer(Modifier.height(8.dp))
        if (vitals != null) {
            TelemetryRow(
                label = stringResource(R.string.hud_cell),
                fraction = vitals.battery.percent / 100f,
                value = "${vitals.battery.percent}%",
                detail = "%.2fV  %.1f°C".format(vitals.battery.voltageMv / 1000f, vitals.battery.temperatureC),
                color = when {
                    low -> HoloColors.CrimsonRed
                    vitals.battery.charging -> HoloColors.CyberYellow
                    else -> HoloColors.NeonCyan
                },
            )
            Spacer(Modifier.height(6.dp))
            val mem = vitals.memory
            TelemetryRow(
                label = stringResource(R.string.hud_neural_load),
                fraction = mem.load,
                value = "${(mem.load * 100).toInt()}%",
                detail = "%.1f/%.1f GB".format((mem.totalBytes - mem.availableBytes) / GB, mem.totalBytes / GB),
                color = if (mem.load > 0.85f) HoloColors.CrimsonRed else HoloColors.CyberYellow,
            )
        }
    }
}

private const val GB = 1024f * 1024f * 1024f

@Composable
private fun TelemetryRow(label: String, fraction: Float, value: String, detail: String, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = HoloColors.TextDim, modifier = Modifier.width(96.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, color = color)
            Spacer(Modifier.weight(1f))
            Text(detail, style = MaterialTheme.typography.labelSmall, color = HoloColors.TextDim)
        }
        Spacer(Modifier.height(3.dp))
        SegmentedBar(fraction, color = color, segments = 24, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun HolocallPanel(
    contacts: ContactsResult,
    onGrant: () -> Unit,
    onDial: (HoloContact) -> Unit,
    onOpen: (HoloContact) -> Unit,
    loadPhoto: suspend (HoloContact) -> Bitmap?,
    modifier: Modifier = Modifier,
) {
    HudPanel(
        modifier = modifier.fillMaxWidth(),
        title = stringResource(R.string.hud_holocall),
        accent = HoloColors.CyberYellow,
        backgroundAlpha = 0.82f,
        trailing = {
            if (contacts is ContactsResult.Loaded) {
                Text(
                    pluralStringResource(R.plurals.hud_holocall_count, contacts.contacts.size, contacts.contacts.size).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HoloColors.TextDim,
                )
            }
        },
    ) {
        when (contacts) {
            ContactsResult.PermissionRequired -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(stringResource(R.string.hud_holocall_permission), style = MaterialTheme.typography.bodyMedium, color = HoloColors.TextDim)
                Spacer(Modifier.height(10.dp))
                HudButton(stringResource(R.string.hud_grant), onGrant, accent = HoloColors.CyberYellow, compact = true)
            }
            is ContactsResult.Loaded -> if (contacts.contacts.isEmpty()) {
                Text(stringResource(R.string.hud_holocall_empty), style = MaterialTheme.typography.bodyMedium, color = HoloColors.TextDim)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(contacts.contacts, key = { it.id }) { contact ->
                        ContactRow(contact, onDial, onOpen, loadPhoto)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: HoloContact,
    onDial: (HoloContact) -> Unit,
    onOpen: (HoloContact) -> Unit,
    loadPhoto: suspend (HoloContact) -> Bitmap?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(contact) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlitchAvatar(contact, loadPhoto)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = HoloColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(contact.phoneNumber, if (contact.starred) "// FIXER" else null).joinToString("  "),
                style = MaterialTheme.typography.labelSmall,
                color = if (contact.starred) HoloColors.CyberYellow else HoloColors.TextDim,
                maxLines = 1,
            )
        }
        if (contact.phoneNumber != null) {
            IconButton(onClick = { onDial(contact) }) {
                Icon(Icons.Filled.Call, contentDescription = null, tint = HoloColors.NeonCyan)
            }
        }
    }
    HudDivider(color = HoloColors.GridLine)
}

/** Contact avatar: duotone photo or initials in a chamfered frame, with ambient glitch. */
@Composable
private fun GlitchAvatar(contact: HoloContact, loadPhoto: suspend (HoloContact) -> Bitmap?) {
    val shape = remember { ChamferShape(8.dp) }
    val photo by produceState<ImageBitmap?>(null, contact.photoUri) { value = loadPhoto(contact)?.asImageBitmap() }
    val glitch = rememberAmbientGlitch(minDelayMs = 3_000, maxDelayMs = 11_000, burstMs = 150)
    Box(
        Modifier
            .size(44.dp)
            .neonBorder(if (contact.starred) HoloColors.CyberYellow else HoloColors.NeonCyan, shape, width = 1.dp, glow = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(HoloColors.CyanDim, HoloColors.PitchBlack), Offset.Zero, Offset(120f, 120f)))
            .glitch(glitch, maxShift = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                contact.name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                color = HoloColors.NeonCyan,
            )
        }
    }
}

@Composable
private fun DrawerHandle(onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = HoloColors.NeonCyan)
        Text(stringResource(R.string.hud_net_access).uppercase(), style = MaterialTheme.typography.labelSmall, color = HoloColors.NeonCyan)
    }
}

@Composable
private fun Dock(
    items: List<DockItem>,
    style: IconStyle,
    onDock: (com.nightcity.holoos.domain.model.DockSlot) -> Unit,
    loadIcon: suspend (String) -> Bitmap?,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            val shape = remember { ChamferShape(10.dp) }
            val icon by produceState<ImageBitmap?>(null, item.packageName, style.tint) {
                value = item.packageName?.let { loadIcon(it) }?.asImageBitmap()
            }
            Column(
                Modifier
                    .weight(1f)
                    .neonBorder(HoloColors.NeonCyan, shape, width = 1.dp)
                    .clip(shape)
                    .background(HoloColors.PitchBlack.copy(alpha = 0.85f))
                    .clickable { onDock(item.slot) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    val bitmap = icon
                    if (bitmap != null) {
                        Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize().clip(ChamferShape(6.dp)))
                    } else {
                        Text(item.slot.name.take(2), style = MaterialTheme.typography.headlineSmall, color = HoloColors.NeonCyan)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(item.slot.labelRes).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = HoloColors.NeonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------
// App drawer
// ---------------------------------------------------------------------------------------

@Composable
private fun AppDrawer(
    state: LauncherUiState,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onStyle: (IconStyle) -> Unit,
    loadIcon: suspend (AppEntry) -> Bitmap?,
) {
    val density = LocalDensity.current
    val closePull = remember(onClose) {
        val threshold = with(density) { 120.dp.toPx() }
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Over-scrolling down past the top of the grid closes the drawer.
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > threshold) {
                        pulled = 0f
                        onClose()
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(HoloColors.PitchBlack.copy(alpha = 0.95f))
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlitchText(
                pluralStringResource(R.plurals.hud_drawer_title, state.totalApps, state.totalApps).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = null, tint = HoloColors.CrimsonRed) }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = HoloColors.NeonCyan) },
            placeholder = { Text(stringResource(R.string.search_apps).uppercase() + "_", style = MaterialTheme.typography.labelLarge) },
            textStyle = MaterialTheme.typography.labelLarge,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = com.nightcity.holoos.feature.audio.hudTextFieldColors(),
        )
        Spacer(Modifier.height(8.dp))
        IconStyleBar(state.iconStyle, onStyle)
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(78.dp),
            modifier = Modifier.weight(1f).nestedScroll(closePull),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.apps, key = { it.key }) { entry ->
                AppTile(entry, state.iconStyle, onLaunch, onInfo, onUninstall, loadIcon)
            }
        }
    }
}

@Composable
private fun IconStyleBar(style: IconStyle, onStyle: (IconStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            IconTint.CYAN to R.string.icon_tint_cyan,
            IconTint.YELLOW to R.string.icon_tint_yellow,
            IconTint.DUOTONE to R.string.icon_tint_duotone,
        ).forEach { (tint, label) ->
            HudButton(
                stringResource(label),
                { onStyle(style.copy(tint = tint)) },
                compact = true,
                style = if (style.tint == tint) HudButtonStyle.Filled else HudButtonStyle.Ghost,
                accent = if (tint == IconTint.YELLOW) HoloColors.CyberYellow else HoloColors.NeonCyan,
            )
        }
        Spacer(Modifier.weight(1f))
        listOf(IconFrame.CHAMFER to R.string.icon_frame_chamfer, IconFrame.HEX to R.string.icon_frame_hex).forEach { (frame, label) ->
            HudButton(
                stringResource(label),
                { onStyle(style.copy(frame = frame)) },
                compact = true,
                style = if (style.frame == frame) HudButtonStyle.Outline else HudButtonStyle.Ghost,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(
    entry: AppEntry,
    style: IconStyle,
    onLaunch: (AppEntry) -> Unit,
    onInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    loadIcon: suspend (AppEntry) -> Bitmap?,
) {
    var menu by remember { mutableStateOf(false) }
    val icon by produceState<ImageBitmap?>(null, entry.key, style.tint) { value = loadIcon(entry)?.asImageBitmap() }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { onLaunch(entry) }, onLongClick = { menu = true })
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HudAppIcon(icon, style)
            Spacer(Modifier.height(6.dp))
            Text(
                entry.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.isWorkProfile) HoloColors.CyberYellow else HoloColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = HoloColors.DarkSlate) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hud_app_info).uppercase(), style = MaterialTheme.typography.labelLarge, color = HoloColors.NeonCyan) },
                onClick = { menu = false; onInfo(entry) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hud_uninstall).uppercase(), style = MaterialTheme.typography.labelLarge, color = HoloColors.CrimsonRed) },
                onClick = { menu = false; onUninstall(entry) },
            )
        }
    }
}

/** A stylized (duotone) icon inside a chamfered or hexagonal neon frame. */
@Composable
fun HudAppIcon(bitmap: ImageBitmap?, style: IconStyle, size: Dp = 56.dp) {
    val shape = if (style.frame == IconFrame.HEX) HexagonShape else remember { ChamferShape(11.dp) }
    val accent = if (style.tint == IconTint.YELLOW) HoloColors.CyberYellow else HoloColors.NeonCyan
    Box(
        Modifier
            .size(size)
            .neonBorder(accent, shape, width = 1.dp)
            .clip(shape)
            .background(HoloColors.DarkSlate),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(if (style.frame == IconFrame.HEX) 9.dp else 4.dp),
            )
        }
    }
}

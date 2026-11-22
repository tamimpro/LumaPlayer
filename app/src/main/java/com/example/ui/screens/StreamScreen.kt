package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.StreamBookmarkEntity
import com.example.data.model.FormatUtils
import com.example.data.model.StreamProtocol
import com.example.ui.theme.LumaMonoStyle
import com.example.ui.theme.LumaMotion
import com.example.ui.theme.lumaPressScale

private data class HeaderEntry(val key: String, val value: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StreamScreen(
    bookmarks: List<StreamBookmarkEntity>,
    parseHeaders: (String) -> Map<String, String>,
    onPlayStream: (title: String, url: String, protocol: StreamProtocol, headers: Map<String, String>, pin: Boolean) -> Unit,
    onPlayBookmark: (StreamBookmarkEntity) -> Unit,
    onTogglePinBookmark: (StreamBookmarkEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    var streamUrl by remember { mutableStateOf("") }
    var streamTitle by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf(StreamProtocol.AUTO) }
    var showHeadersSection by remember { mutableStateOf(false) }

    var authorizationHeader by remember { mutableStateOf("") }
    var cookieHeader by remember { mutableStateOf("") }
    var refererHeader by remember { mutableStateOf("") }
    var userAgentHeader by remember { mutableStateOf("") }
    val customHeaders = remember { mutableStateListOf<HeaderEntry>() }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun collectHeaders(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        if (authorizationHeader.isNotBlank()) map["Authorization"] = authorizationHeader.trim()
        if (cookieHeader.isNotBlank()) map["Cookie"] = cookieHeader.trim()
        if (refererHeader.isNotBlank()) map["Referer"] = refererHeader.trim()
        if (userAgentHeader.isNotBlank()) map["User-Agent"] = userAgentHeader.trim()
        customHeaders.forEach { entry ->
            if (entry.key.isNotBlank() && entry.value.isNotBlank()) {
                map[entry.key.trim()] = entry.value.trim()
            }
        }
        return map
    }

    fun submit(pin: Boolean) {
        val trimmed = streamUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            validationError = "Enter a valid HTTP or HTTPS video / HLS (.m3u8) / DASH (.mpd) URL."
            return
        }
        validationError = null
        onPlayStream(streamTitle, trimmed, protocol, collectHeaders(), pin)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Http,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Network Stream Engine", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Direct MP4/MKV, HLS (.m3u8), MPEG-DASH (.mpd) · Max quality HDR",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = streamUrl,
                        onValueChange = {
                            streamUrl = it
                            validationError = null
                        },
                        label = { Text("Video or Manifest URL") },
                        placeholder = { Text("https://example.com/master.m3u8") },
                        isError = validationError != null,
                        supportingText = validationError?.let { msg -> { Text(msg) } },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_url_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        label = { Text("Display Title (Optional)") },
                        placeholder = { Text("My 4K HDR Stream") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Stream Protocol", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StreamProtocol.entries.forEach { item ->
                            FilterChip(
                                selected = protocol == item,
                                onClick = { protocol = item },
                                label = { Text(item.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Expandable Authorized Headers & Cookies Editor
                    Surface(
                        onClick = { showHeadersSection = !showHeadersSection },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_headers_toggle")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.VpnKey,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Authorized Headers & Cookies",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            Icon(
                                if (showHeadersSection) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (showHeadersSection) "Collapse headers" else "Expand headers"
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showHeadersSection,
                        enter = LumaMotion.accordionEnter(),
                        exit = LumaMotion.accordionExit()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = authorizationHeader,
                                onValueChange = { authorizationHeader = it },
                                label = { Text("Authorization") },
                                placeholder = { Text("Bearer <token>") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = cookieHeader,
                                onValueChange = { cookieHeader = it },
                                label = { Text("Cookie") },
                                placeholder = { Text("session=abc; token=xyz") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = refererHeader,
                                onValueChange = { refererHeader = it },
                                label = { Text("Referer") },
                                placeholder = { Text("https://origin.example.com/") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = userAgentHeader,
                                onValueChange = { userAgentHeader = it },
                                label = { Text("User-Agent") },
                                placeholder = { Text("Custom User-Agent string") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            customHeaders.forEachIndexed { idx, entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = entry.key,
                                        onValueChange = { customHeaders[idx] = entry.copy(key = it) },
                                        label = { Text("Header") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.45f)
                                    )
                                    OutlinedTextField(
                                        value = entry.value,
                                        onValueChange = { customHeaders[idx] = entry.copy(value = it) },
                                        label = { Text("Value") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.55f)
                                    )
                                    IconButton(onClick = { customHeaders.removeAt(idx) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove custom header")
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { customHeaders.add(HeaderEntry("", "")) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Custom Header")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { submit(pin = false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("stream_play_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play Stream")
                        }
                        OutlinedButton(
                            onClick = { submit(pin = true) },
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("stream_save_play_button")
                        ) {
                            Icon(Icons.Rounded.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Saved & Recent Streams (${bookmarks.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (bookmarks.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("No Saved Streams Yet", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Paste any HTTP/HTTPS, HLS (.m3u8), or MPEG-DASH (.mpd) link above. Custom headers and resume positions are stored strictly on-device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(bookmarks, key = { it.id }) { bookmark ->
                val headersCount = remember(bookmark.headersJson) { parseHeaders(bookmark.headersJson).size }
                Surface(
                    onClick = { onPlayBookmark(bookmark) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        1.dp,
                        if (bookmark.isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .lumaPressScale(0.985f)
                        .testTag("stream_bookmark_${bookmark.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bookmark.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = bookmark.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    bookmark.protocol,
                                    style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.primary)
                                )
                                if (headersCount > 0) {
                                    Text(
                                        "$headersCount header(s)",
                                        style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.secondary)
                                    )
                                }
                                if (bookmark.lastPositionMs > 2_000L) {
                                    Text(
                                        "Resume ${FormatUtils.formatDuration(bookmark.lastPositionMs)}",
                                        style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onTogglePinBookmark(bookmark) }) {
                            Icon(
                                if (bookmark.isPinned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = "Pin stream",
                                tint = if (bookmark.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete stream")
                        }
                    }
                }
            }
        }
    }
}

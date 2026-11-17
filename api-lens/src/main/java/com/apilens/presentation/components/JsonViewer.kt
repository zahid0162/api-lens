package com.apilens.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apilens.presentation.theme.ApiLensColors
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors
import com.apilens.utils.JsonFormatter

enum class JsonDisplayMode(val label: String) {
    TREE("Tree"),
    PRETTY("Pretty"),
    RAW("Raw")
}

@Composable
fun JsonPayloadViewer(
    rawBody: String,
    contentType: String?,
    displayMode: JsonDisplayMode,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    val isJson = remember(rawBody, contentType) {
        JsonFormatter.isLikelyJson(rawBody, contentType)
    }
    val prettyText = remember(rawBody, isJson) {
        if (isJson) JsonFormatter.prettyPrint(rawBody) else rawBody
    }
    val jsonTree = remember(rawBody, isJson, displayMode) {
        if (isJson && displayMode == JsonDisplayMode.TREE) JsonFormatter.parseJsonTree(rawBody) else null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.canvas)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        if (displayMode == JsonDisplayMode.TREE && jsonTree != null) {
            val expandedMap = remember(rawBody) { mutableStateMapOf<String, Boolean>() }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                JsonTreeNodeRow(
                    node = jsonTree,
                    path = "root",
                    expandedMap = expandedMap,
                    searchQuery = searchQuery,
                    colors = colors
                )
            }
        } else {
            val textToDisplay = if (displayMode == JsonDisplayMode.RAW) rawBody else prettyText
            val highlighted = remember(textToDisplay, searchQuery, colors, isJson, displayMode) {
                if (isJson && displayMode != JsonDisplayMode.RAW) {
                    highlightJsonText(textToDisplay, searchQuery, colors)
                } else {
                    highlightPlainSearch(textToDisplay, searchQuery, colors)
                }
            }
            SelectionContainer {
                Text(
                    text = highlighted,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun JsonTreeNodeRow(
    node: JsonFormatter.JsonTreeNode,
    path: String,
    expandedMap: MutableMap<String, Boolean>,
    searchQuery: String,
    colors: ApiLensColors
) {
    val defaultExpanded = node.depth < 2
    val isExpanded = expandedMap[path] ?: defaultExpanded
    val indent = (node.depth * 14).dp

    when (node) {
        is JsonFormatter.JsonTreeNode.ObjectNode -> {
            val count = node.entries.size
            Row(
                modifier = Modifier
                    .clickable { expandedMap[path] = !isExpanded }
                    .padding(start = indent, top = 2.dp, bottom = 2.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = if (isExpanded) "Collapse object" else "Expand object",
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildKeyPrefix(node.key, searchQuery, colors) + AnnotatedString(
                        if (isExpanded) "{" else "{ ... $count keys }",
                        spanStyle = SpanStyle(color = colors.textSecondary)
                    ),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp
                )
            }
            if (isExpanded) {
                node.entries.forEachIndexed { index, child ->
                    JsonTreeNodeRow(
                        node = child,
                        path = "$path/${child.key ?: index}",
                        expandedMap = expandedMap,
                        searchQuery = searchQuery,
                        colors = colors
                    )
                }
                Text(
                    text = "}",
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = indent + 18.dp)
                )
            }
        }

        is JsonFormatter.JsonTreeNode.ArrayNode -> {
            val count = node.items.size
            Row(
                modifier = Modifier
                    .clickable { expandedMap[path] = !isExpanded }
                    .padding(start = indent, top = 2.dp, bottom = 2.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = if (isExpanded) "Collapse array" else "Expand array",
                    tint = colors.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = buildKeyPrefix(node.key, searchQuery, colors) + AnnotatedString(
                        if (isExpanded) "[" else "[ ... $count items ]",
                        spanStyle = SpanStyle(color = colors.textSecondary)
                    ),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp
                )
            }
            if (isExpanded) {
                node.items.forEachIndexed { index, child ->
                    JsonTreeNodeRow(
                        node = child,
                        path = "$path[$index]",
                        expandedMap = expandedMap,
                        searchQuery = searchQuery,
                        colors = colors
                    )
                }
                Text(
                    text = "]",
                    color = colors.textSecondary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = indent + 18.dp)
                )
            }
        }

        is JsonFormatter.JsonTreeNode.PrimitiveNode -> {
            val valColor = when (node.type) {
                JsonFormatter.PrimitiveType.STRING -> colors.jsonString
                JsonFormatter.PrimitiveType.NUMBER -> colors.jsonNumber
                JsonFormatter.PrimitiveType.BOOLEAN -> colors.jsonBoolean
                JsonFormatter.PrimitiveType.NULL -> colors.jsonNull
            }
            SelectionContainer {
                Text(
                    text = buildKeyPrefix(node.key, searchQuery, colors) +
                        highlightToken(node.displayValue, valColor, searchQuery, colors),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = indent + 18.dp, top = 1.dp, bottom = 1.dp)
                )
            }
        }
    }
}

private fun buildKeyPrefix(
    key: String?,
    searchQuery: String,
    colors: ApiLensColors
): AnnotatedString {
    if (key == null) return AnnotatedString("")
    return buildAnnotatedString {
        if (key.startsWith("[") && key.endsWith("]")) {
            append(highlightToken(key, colors.textMuted, searchQuery, colors))
        } else {
            append(highlightToken("\"$key\"", colors.jsonKey, searchQuery, colors))
        }
        withStyle(SpanStyle(color = colors.textSecondary)) {
            append(": ")
        }
    }
}

private fun highlightToken(
    text: String,
    baseColor: Color,
    query: String,
    colors: ApiLensColors
): AnnotatedString {
    if (query.isBlank()) {
        return AnnotatedString(text, SpanStyle(color = baseColor))
    }
    return buildAnnotatedString {
        var cursor = 0
        val lowerText = text.lowercase()
        val lowerQuery = query.lowercase()
        while (cursor < text.length) {
            val match = lowerText.indexOf(lowerQuery, cursor)
            if (match < 0) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(cursor))
                }
                break
            }
            if (match > cursor) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(cursor, match))
                }
            }
            withStyle(
                SpanStyle(
                    color = colors.canvas,
                    background = colors.slowWarning,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(match, match + query.length))
            }
            cursor = match + query.length
        }
    }
}

private fun highlightPlainSearch(
    text: String,
    query: String,
    colors: ApiLensColors
): AnnotatedString = highlightToken(text, colors.textPrimary, query, colors)

private fun highlightJsonText(
    prettyJson: String,
    query: String,
    colors: ApiLensColors
): AnnotatedString {
    // Line-by-line syntax coloring for keys, strings, numbers, booleans, and null
    val base = buildAnnotatedString {
        val lines = prettyJson.split('\n')
        lines.forEachIndexed { lineIdx, line ->
            val colonIdx = findKeyValueSeparator(line)
            if (colonIdx > 0) {
                val keyPart = line.substring(0, colonIdx)
                val rest = line.substring(colonIdx)
                withStyle(SpanStyle(color = colors.jsonKey)) {
                    append(keyPart)
                }
                appendValuePart(rest, colors)
            } else {
                appendValuePart(line, colors)
            }
            if (lineIdx < lines.lastIndex) append('\n')
        }
    }
    if (query.isBlank()) return base

    // Overlay search highlights cleanly on top of syntax colors
    val builder = AnnotatedString.Builder(base)
    val lower = prettyJson.lowercase()
    val q = query.lowercase()
    var idx = 0
    while (idx < lower.length) {
        val match = lower.indexOf(q, idx)
        if (match < 0) break
        builder.addStyle(
            style = SpanStyle(
                color = colors.canvas,
                background = colors.slowWarning,
                fontWeight = FontWeight.Bold
            ),
            start = match,
            end = match + q.length
        )
        idx = match + q.length
    }
    return builder.toAnnotatedString()
}

private fun findKeyValueSeparator(line: String): Int {
    var inString = false
    var escaped = false
    for (i in line.indices) {
        val c = line[i]
        if (escaped) {
            escaped = false
            continue
        }
        if (c == '\\') {
            escaped = true
            continue
        }
        if (c == '"') {
            inString = !inString
            continue
        }
        if (!inString && c == ':') {
            return i
        }
    }
    return -1
}

private fun AnnotatedString.Builder.appendValuePart(part: String, colors: ApiLensColors) {
    val trimmed = part.trim()
    val color = when {
        trimmed.startsWith(": \"") || trimmed.startsWith("\"") -> colors.jsonString
        trimmed.contains("true") || trimmed.contains("false") -> colors.jsonBoolean
        trimmed.contains("null") -> colors.jsonNull
        trimmed.any { it.isDigit() } && !trimmed.contains("\"") -> colors.jsonNumber
        else -> colors.textSecondary
    }
    withStyle(SpanStyle(color = color)) {
        append(part)
    }
}

fun countJsonMatches(text: String, query: String): Int {
    if (query.isBlank() || text.isBlank()) return 0
    val lower = text.lowercase()
    val q = query.lowercase()
    var count = 0
    var idx = 0
    while (idx < lower.length) {
        val match = lower.indexOf(q, idx)
        if (match < 0) break
        count++
        idx = match + q.length
    }
    return count
}

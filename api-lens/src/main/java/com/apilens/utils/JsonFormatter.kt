package com.apilens.utils

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure-Android-SDK JSON formatter, body redactor, and expandable tree parser.
 * Avoids external reflection/serialization dependencies to prevent version conflicts in host apps.
 */
object JsonFormatter {

    private const val REDACTED_VALUE = "•••••••• (REDACTED)"

    enum class PrimitiveType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL
    }

    sealed class JsonTreeNode {
        abstract val key: String?
        abstract val depth: Int

        data class ObjectNode(
            override val key: String?,
            val entries: List<JsonTreeNode>,
            override val depth: Int
        ) : JsonTreeNode()

        data class ArrayNode(
            override val key: String?,
            val items: List<JsonTreeNode>,
            override val depth: Int
        ) : JsonTreeNode()

        data class PrimitiveNode(
            override val key: String?,
            val displayValue: String,
            val type: PrimitiveType,
            override val depth: Int
        ) : JsonTreeNode()
    }

    fun isLikelyJson(body: String?, contentType: String?): Boolean {
        if (body.isNullOrBlank()) return false
        val ct = contentType?.lowercase().orEmpty()
        if (ct.contains("json")) return true
        val trimmed = body.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    fun isFormUrlEncoded(contentType: String?): Boolean {
        return contentType?.lowercase()?.contains("application/x-www-form-urlencoded") == true
    }

    fun prettyPrint(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        return try {
            when (val parsed = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> parsed.toString(2)
                is JSONArray -> parsed.toString(2)
                else -> raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    fun redactBody(
        raw: String,
        contentType: String?,
        sensitiveKeys: Set<String>
    ): String {
        if (raw.isBlank() || sensitiveKeys.isEmpty()) return raw
        val normalizedKeys = sensitiveKeys.map { it.trim().lowercase() }.toSet()

        if (isFormUrlEncoded(contentType)) {
            return redactFormUrlEncoded(raw, normalizedKeys)
        }

        if (isLikelyJson(raw, contentType)) {
            val redactedJson = redactJson(raw, normalizedKeys)
            if (redactedJson != null) return redactedJson
        }
        return raw
    }

    fun redactJson(raw: String, normalizedSensitiveKeys: Set<String>): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return raw
        return try {
            when (val parsed = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> {
                    redactJsonObject(parsed, normalizedSensitiveKeys)
                    parsed.toString(2)
                }
                is JSONArray -> {
                    redactJsonArray(parsed, normalizedSensitiveKeys)
                    parsed.toString(2)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun redactJsonObject(obj: JSONObject, sensitiveKeys: Set<String>) {
        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val normalizedKey = key.trim().lowercase()
            if (normalizedKey in sensitiveKeys) {
                obj.put(key, REDACTED_VALUE)
            } else {
                when (val child = obj.opt(key)) {
                    is JSONObject -> redactJsonObject(child, sensitiveKeys)
                    is JSONArray -> redactJsonArray(child, sensitiveKeys)
                }
            }
        }
    }

    private fun redactJsonArray(array: JSONArray, sensitiveKeys: Set<String>) {
        for (i in 0 until array.length()) {
            when (val child = array.opt(i)) {
                is JSONObject -> redactJsonObject(child, sensitiveKeys)
                is JSONArray -> redactJsonArray(child, sensitiveKeys)
            }
        }
    }

    private fun redactFormUrlEncoded(raw: String, sensitiveKeys: Set<String>): String {
        return raw.split("&").joinToString("&") { pair ->
            val eqIdx = pair.indexOf('=')
            if (eqIdx <= 0) {
                pair
            } else {
                val rawKey = pair.substring(0, eqIdx)
                val decodedKey = runCatching {
                    URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
                }.getOrDefault(rawKey)
                if (decodedKey.trim().lowercase() in sensitiveKeys) {
                    "$rawKey=REDACTED"
                } else {
                    pair
                }
            }
        }
    }

    fun parseFormUrlEncoded(raw: String): List<Pair<String, String>> {
        if (raw.isBlank()) return emptyList()
        return raw.split("&").mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val eqIdx = pair.indexOf('=')
            if (eqIdx < 0) {
                decodeFormPart(pair) to ""
            } else {
                decodeFormPart(pair.substring(0, eqIdx)) to decodeFormPart(pair.substring(eqIdx + 1))
            }
        }
    }

    private fun decodeFormPart(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }

    fun parseJsonTree(raw: String): JsonTreeNode? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val parsed = JSONTokener(trimmed).nextValue()
            buildTreeNode(key = null, value = parsed, depth = 0)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildTreeNode(key: String?, value: Any?, depth: Int): JsonTreeNode {
        return when (value) {
            is JSONObject -> {
                val children = buildList {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        add(buildTreeNode(key = k, value = value.opt(k), depth = depth + 1))
                    }
                }
                JsonTreeNode.ObjectNode(key = key, entries = children, depth = depth)
            }
            is JSONArray -> {
                val children = (0 until value.length()).map { index ->
                    buildTreeNode(key = "[$index]", value = value.opt(index), depth = depth + 1)
                }
                JsonTreeNode.ArrayNode(key = key, items = children, depth = depth)
            }
            null, JSONObject.NULL -> JsonTreeNode.PrimitiveNode(
                key = key,
                displayValue = "null",
                type = PrimitiveType.NULL,
                depth = depth
            )
            is Boolean -> JsonTreeNode.PrimitiveNode(
                key = key,
                displayValue = value.toString(),
                type = PrimitiveType.BOOLEAN,
                depth = depth
            )
            is Number -> JsonTreeNode.PrimitiveNode(
                key = key,
                displayValue = value.toString(),
                type = PrimitiveType.NUMBER,
                depth = depth
            )
            else -> JsonTreeNode.PrimitiveNode(
                key = key,
                displayValue = "\"${value.toString()}\"",
                type = PrimitiveType.STRING,
                depth = depth
            )
        }
    }
}

package dev.xenoncolt.jellyflix.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decodes a list of names that the API may express either way:
 *
 *   "keywords": ["heist", "1970s"]
 *   "keywords": [{ "id": 1, "name": "heist" }]
 *
 * The handoff documents these fields by name but not by shape, so rather than
 * guess and crash a whole detail screen on a mismatch, we accept both and drop
 * anything unrecognisable.
 */
object NameListSerializer : KSerializer<List<String>> {

    private val delegate = ListSerializer(String.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = input.decodeJsonElement()
        val array = element as? JsonArray ?: return emptyList()

        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNullSafe()
                is JsonObject -> (item["name"] ?: item["title"] ?: item["english_name"])
                    ?.jsonPrimitive?.contentOrNullSafe()
                else -> null
            }
        }.filter { it.isNotBlank() }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        delegate.serialize(encoder, value)
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? =
        if (this is JsonPrimitive && !isString && content == "null") null else content
}

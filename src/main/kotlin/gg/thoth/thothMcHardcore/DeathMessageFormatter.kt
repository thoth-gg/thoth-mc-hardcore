package gg.thoth.thothMcHardcore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DeathMessageFormatter(
    private val languageMap: LanguageMap,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun format(componentJson: String): String? {
        val component = json.parseToJsonElement(componentJson) as? JsonObject ?: return null
        val translationKey = component["translate"]?.jsonPrimitive?.contentOrNull ?: return null
        if (!translationKey.startsWith("death.")) {
            return null
        }

        return renderComponent(component)
    }

    private fun renderComponent(value: JsonElement): String {
        return when (value) {
            is JsonObject -> renderObject(value)
            is JsonArray -> value.joinToString("") { renderComponent(it) }
            is JsonPrimitive -> value.contentOrNull.orEmpty()
            JsonNull -> ""
        }
    }

    private fun renderObject(value: JsonObject): String {
        val base = when {
            value["text"] != null -> value["text"]!!.jsonPrimitive.contentOrNull.orEmpty()
            value["translate"] != null -> {
                val key = value["translate"]!!.jsonPrimitive.contentOrNull.orEmpty()
                val args = value["with"]
                    ?.jsonArray
                    ?.map { renderComponent(it) }
                    .orEmpty()
                languageMap.format(key, *args.toTypedArray())
            }
            value["selector"] != null -> value["selector"]!!.jsonPrimitive.contentOrNull.orEmpty()
            value["score"] != null -> value["score"]!!.jsonObject["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
            value["keybind"] != null -> value["keybind"]!!.jsonPrimitive.contentOrNull.orEmpty()
            else -> ""
        }

        val extra = value["extra"]
            ?.jsonArray
            ?.joinToString("") { renderComponent(it) }
            .orEmpty()

        return base + extra
    }
}

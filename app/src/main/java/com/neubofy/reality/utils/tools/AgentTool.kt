package com.neubofy.reality.utils.tools

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class ToolCategory {
    DATA,
    UTILITY,
    ACTION
}

interface AgentTool {
    val id: String
    val name: String
    val shortDesc: String
    val category: ToolCategory
    val defaultEnabled: Boolean
        get() = true

    fun getSchema(): JSONObject
    suspend fun execute(context: Context, args: JSONObject): String

    fun createSchema(
        name: String,
        description: String,
        params: Map<String, String>,
        required: List<String> = emptyList()
    ): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        params.forEach { (key, desc) ->
                            put(key, JSONObject().apply {
                                put("type", "string")
                                put("description", desc)
                            })
                        }
                    })
                    if (required.isNotEmpty()) {
                        put("required", JSONArray(required))
                    }
                })
            })
        }
    }
}

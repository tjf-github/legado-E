package io.legado.app.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.JsonSyntaxException
import com.google.gson.Strictness
import com.google.gson.ToNumberPolicy
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonWriter
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.ceil

val INITIAL_GSON: Gson by lazy {
    GsonBuilder()
        .registerTypeAdapter(
            object : TypeToken<Map<String?, Any?>?>() {}.type,
            MapDeserializerDoubleAsIntFix()
        )
        .registerTypeAdapter(Int::class.java, IntJsonDeserializer())
        .registerTypeAdapter(String::class.java, StringJsonDeserializer())
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter())
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter())
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
}

val GSON: Gson by lazy {
    INITIAL_GSON.newBuilder()
        .registerTypeAdapter(ExploreRule::class.java, ExploreRule.jsonDeserializer)
        .registerTypeAdapter(SearchRule::class.java, SearchRule.jsonDeserializer)
        .registerTypeAdapter(BookInfoRule::class.java, BookInfoRule.jsonDeserializer)
        .registerTypeAdapter(TocRule::class.java, TocRule.jsonDeserializer)
        .registerTypeAdapter(ContentRule::class.java, ContentRule.jsonDeserializer)
        .registerTypeAdapter(ReviewRule::class.java, ReviewRule.jsonDeserializer)
        .create()
}

val GSONStrict: Gson by lazy {
    GSON.newBuilder()
        .setStrictness(Strictness.STRICT)
        .create()
}

inline fun <reified T> genericType(): Type = object : TypeToken<T>() {}.type

inline fun <reified T> Gson.fromJsonObject(json: String?): Result<T> {
    return kotlin.runCatching {
        if (json == null) {
            throw JsonSyntaxException("解析字符串为空")
        }
        fromJson(json, genericType<T>()) as T
    }
}

inline fun <reified T> Gson.fromJsonArray(json: String?): Result<List<T>> {
    return kotlin.runCatching {
        if (json == null) {
            throw JsonSyntaxException("解析字符串为空")
        }
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        val list = fromJson(json, type) as List<T?>
        if (list.contains(null)) {
            throw JsonSyntaxException(
                "列表不能存在null元素，可能是json格式错误，通常为列表存在多余的逗号所致"
            )
        }
        @Suppress("UNCHECKED_CAST")
        list as List<T>
    }
}

inline fun <reified T> Gson.fromJsonObject(inputStream: InputStream?): Result<T> {
    return kotlin.runCatching {
        if (inputStream == null) {
            throw JsonSyntaxException("解析流为空")
        }
        val reader = InputStreamReader(inputStream)
        fromJson(reader, genericType<T>()) as T
    }
}

inline fun <reified T> Gson.fromJsonArray(inputStream: InputStream?): Result<List<T>> {
    return kotlin.runCatching {
        if (inputStream == null) {
            throw JsonSyntaxException("解析流为空")
        }
        val reader = InputStreamReader(inputStream)
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        val list = fromJson(reader, type) as List<T?>
        if (list.contains(null)) {
            throw JsonSyntaxException(
                "列表不能存在null元素，可能是json格式错误，通常为列表存在多余的逗号所致"
            )
        }
        @Suppress("UNCHECKED_CAST")
        list as List<T>
    }
}

fun Gson.writeToOutputStream(out: OutputStream, any: Any) {
    val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
    writer.setIndent("  ")
    if (any is List<*>) {
        writer.beginArray()
        any.forEach {
            it?.let {
                toJson(it, it::class.java, writer)
            }
        }
        writer.endArray()
    } else {
        toJson(any, any::class.java, writer)
    }
    writer.close()
}

/**
 *
 */
class StringJsonDeserializer : JsonDeserializer<String?> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext?
    ): String? {
        return when {
            json.isJsonPrimitive -> json.asString
            json.isJsonNull -> null
            else -> json.toString()
        }
    }

}

/**
 * int类型转化失败时跳过
 */
class IntJsonDeserializer : JsonDeserializer<Int?> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Int? {
        return when {
            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                if (prim.isNumber) {
                    prim.asNumber.toInt()
                } else {
                    null
                }
            }

            else -> null
        }
    }

}

/**
 * 修复Int变为Double的问题
 */
class MapDeserializerDoubleAsIntFix :
    JsonDeserializer<Map<String, Any?>?> {

    @Throws(JsonParseException::class)
    override fun deserialize(
        jsonElement: JsonElement,
        type: Type,
        jsonDeserializationContext: JsonDeserializationContext
    ): Map<String, Any?>? {
        @Suppress("unchecked_cast")
        return read(jsonElement) as? Map<String, Any?>
    }

    fun read(json: JsonElement): Any? {
        when {
            json.isJsonArray -> {
                val list: MutableList<Any?> = ArrayList()
                val arr = json.asJsonArray
                for (anArr in arr) {
                    list.add(read(anArr))
                }
                return list
            }

            json.isJsonObject -> {
                val map: MutableMap<String, Any?> =
                    LinkedTreeMap()
                val obj = json.asJsonObject
                val entitySet =
                    obj.entrySet()
                for ((key, value) in entitySet) {
                    map[key] = read(value)
                }
                return map
            }

            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                when {
                    prim.isBoolean -> {
                        return prim.asBoolean
                    }

                    prim.isString -> {
                        return prim.asString
                    }

                    prim.isNumber -> {
                        val num: Number = prim.asNumber
                        // here you can handle double int/long values
                        // and return any type you want
                        // this solution will transform 3.0 float to long values
                        return if (ceil(num.toDouble()) == num.toLong().toDouble()) {
                            num.toLong()
                        } else {
                            num.toDouble()
                        }
                    }
                }
            }
        }
        return null
    }

}

/**
 * java.time 适配器：序列化为 ISO 字符串，反序列化兼容旧数据（Gson 反射对象格式）与新数据。
 * 避免反射 java.time 私有字段（Java 17+ 模块系统下会被拦截，Android 上也不依赖碰巧可用）。
 */
class LocalDateAdapter : JsonSerializer<LocalDate>, JsonDeserializer<LocalDate?> {

    override fun serialize(
        src: LocalDate,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement = JsonPrimitive(src.toString())

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): LocalDate? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive -> LocalDate.parse(json.asString)
            json.isJsonObject -> {
                val obj = json.asJsonObject
                LocalDate.of(
                    obj.get("year").asInt,
                    obj.get("month").asInt,
                    obj.get("day").asInt
                )
            }
            else -> throw JsonParseException("无法解析 LocalDate: $json")
        }
    }
}

class LocalTimeAdapter : JsonSerializer<LocalTime>, JsonDeserializer<LocalTime?> {

    override fun serialize(
        src: LocalTime,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement = JsonPrimitive(src.toString())

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): LocalTime? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive -> LocalTime.parse(json.asString)
            json.isJsonObject -> {
                val obj = json.asJsonObject
                LocalTime.of(
                    obj.get("hour").asInt,
                    obj.get("minute").asInt,
                    obj.get("second")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                    obj.get("nano")?.takeUnless { it.isJsonNull }?.asInt ?: 0
                )
            }
            else -> throw JsonParseException("无法解析 LocalTime: $json")
        }
    }
}

class LocalDateTimeAdapter : JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime?> {

    override fun serialize(
        src: LocalDateTime,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement = JsonPrimitive(src.toString())

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): LocalDateTime? {
        return when {
            json.isJsonNull -> null
            json.isJsonPrimitive -> LocalDateTime.parse(json.asString.replace(' ', 'T'))
            json.isJsonObject -> {
                val obj = json.asJsonObject
                val date = obj.get("date")?.let {
                    if (it.isJsonPrimitive) {
                        LocalDate.parse(it.asString)
                    } else {
                        LocalDate.of(
                            it.asJsonObject.get("year").asInt,
                            it.asJsonObject.get("month").asInt,
                            it.asJsonObject.get("day").asInt
                        )
                    }
                } ?: throw JsonParseException("LocalDateTime 缺少 date 字段: $json")
                val time = obj.get("time")?.let {
                    if (it.isJsonPrimitive) {
                        LocalTime.parse(it.asString)
                    } else {
                        LocalTime.of(
                            it.asJsonObject.get("hour").asInt,
                            it.asJsonObject.get("minute").asInt,
                            it.asJsonObject.get("second")?.takeUnless { it.isJsonNull }?.asInt ?: 0,
                            it.asJsonObject.get("nano")?.takeUnless { it.isJsonNull }?.asInt ?: 0
                        )
                    }
                } ?: throw JsonParseException("LocalDateTime 缺少 time 字段: $json")
                LocalDateTime.of(date, time)
            }
            else -> throw JsonParseException("无法解析 LocalDateTime: $json")
        }
    }
}

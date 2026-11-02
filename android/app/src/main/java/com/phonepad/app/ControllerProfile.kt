package com.phonepad.app

import android.content.Context
import org.json.JSONObject

/** Perfis de layout: posição (fração da tela) + escala de cada bloco, salvos por nome de jogo. */
object ControllerProfileStore {
    private const val PREF = "phonepad_profiles"
    private const val KEY_LAST = "last_profile"

    data class Block(val fx: Float, val fy: Float, val s: Float)
    /** Perfil = posições + botões ocultos (entry names, ex: "btnUp", "joyRight") + skin
     *  + modo dos analógicos (fixo vs área flutuante) + macros (chave "m1".."m4"). */
    data class Profile(
        val blocks: Map<String, Block>,
        val hidden: Set<String> = emptySet(),
        val skin: String = "xbox",
        val floatL: Boolean = false,
        val floatR: Boolean = false,
        val macros: Map<String, MacroDef> = emptyMap()
    )

    fun names(ctx: Context): List<String> =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).all.keys
            .filter { it.startsWith("profile:") }
            .map { it.removePrefix("profile:") }
            .sorted()

    fun save(ctx: Context, name: String, profile: Profile) {
        val blocks = JSONObject()
        for ((id, b) in profile.blocks) {
            blocks.put(id, JSONObject().put("x", b.fx.toDouble()).put("y", b.fy.toDouble()).put("s", b.s.toDouble()))
        }
        val o = JSONObject().put("blocks", blocks)
            .put("hidden", org.json.JSONArray(profile.hidden.toList()))
            .put("skin", profile.skin)
            .put("floatL", profile.floatL)
            .put("floatR", profile.floatR)
        val macros = JSONObject()
        for ((key, m) in profile.macros) macros.put(key, m.toJson())
        o.put("macros", macros)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("profile:$name", o.toString())
            .putString(KEY_LAST, name)
            .apply()
    }

    fun load(ctx: Context, name: String): Profile? {
        return try {
            val s = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("profile:$name", null)
                ?: return null
            val o = JSONObject(s)
            // Compat: perfis antigos guardavam só os blocos direto na raiz
            val blocksObj = if (o.has("blocks")) o.getJSONObject("blocks") else o
            val blocks = buildMap {
                for (id in blocksObj.keys()) {
                    val b = blocksObj.getJSONObject(id)
                    put(id, Block(b.optDouble("x").toFloat(), b.optDouble("y").toFloat(), b.optDouble("s", 1.0).toFloat()))
                }
            }
            val hidden = buildSet {
                val arr = o.optJSONArray("hidden") ?: return@buildSet
                for (i in 0 until arr.length()) add(arr.optString(i))
            }
            val macros = mutableMapOf<String, MacroDef>()
            o.optJSONObject("macros")?.let { mo ->
                for (key in mo.keys()) {
                    try { macros[key] = MacroDef.fromJson(mo.getJSONObject(key)) } catch (_: Exception) { }
                }
            }
            Profile(blocks, hidden, o.optString("skin", "xbox"),
                o.optBoolean("floatL", false), o.optBoolean("floatR", false), macros)
        } catch (_: Exception) { null }
    }

    fun delete(ctx: Context, name: String) {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit().remove("profile:$name").apply()
        if (prefs.getString(KEY_LAST, null) == name) prefs.edit().remove(KEY_LAST).apply()
    }

    fun last(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_LAST, null)

    fun setLast(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_LAST, name).apply()
    }

    /** Exporta todos os perfis p/ um JSON só (backup/compartilhar). */
    fun exportAll(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val profiles = JSONObject()
        for (name in names(ctx)) {
            prefs.getString("profile:$name", null)?.let { profiles.put(name, JSONObject(it)) }
        }
        return JSONObject().put("app", "phonepad").put("v", 1)
            .put("last", last(ctx) ?: JSONObject.NULL)
            .put("profiles", profiles).toString(2)
    }

    /** Importa JSON de exportAll. Perfis com mesmo nome são sobrescritos. Retorna qtd importada. */
    fun importAll(ctx: Context, json: String): Int {
        val o = JSONObject(json)
        if (o.optString("app") != "phonepad") throw IllegalArgumentException("Not a DroidPad backup file")
        val profiles = o.getJSONObject("profiles")
        var n = 0
        val edit = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
        for (name in profiles.keys()) {
            edit.putString("profile:$name", profiles.getJSONObject(name).toString())
            n++
        }
        val lastName = o.optString("last", "")
        if (lastName.isNotEmpty()) edit.putString(KEY_LAST, lastName)
        edit.apply()
        return n
    }
}

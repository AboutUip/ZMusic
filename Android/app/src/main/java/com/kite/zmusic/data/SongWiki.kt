package com.kite.zmusic.data

import org.json.JSONArray
import org.json.JSONObject

internal data class SongWikiFact(
    val label: String,
    val value: String,
)

internal data class SongWikiCoverItem(
    val id: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String?,
    val playCount: Long = 0L,
)

internal data class SongWikiPage(
    val facts: List<SongWikiFact> = emptyList(),
    val chips: List<String> = emptyList(),
    val notes: List<SongWikiFact> = emptyList(),
    val paragraphs: List<String> = emptyList(),
    val similar: List<SongWikiCoverItem> = emptyList(),
    val playlists: List<SongWikiCoverItem> = emptyList(),
) {
    val isEmpty: Boolean
        get() = facts.isEmpty() &&
            chips.isEmpty() &&
            notes.isEmpty() &&
            paragraphs.isEmpty() &&
            similar.isEmpty() &&
            playlists.isEmpty()

    val hasInfo: Boolean
        get() = facts.isNotEmpty() || chips.isNotEmpty() || notes.isNotEmpty() || paragraphs.isNotEmpty()
}

/**
 * `/song/wiki/summary` 的 block 按 `code` 分流；创作信息来自 `/song/creators`（RN 百科页）。
 * 回忆坐标不收录。
 */
internal object SongWikiParse {

    fun merge(
        summary: JSONObject?,
        ugc: JSONObject?,
        creators: JSONObject?,
        wikiInfo: JSONObject?,
    ): SongWikiPage {
        val facts = ArrayList<SongWikiFact>()
        val chips = ArrayList<String>()
        val notes = ArrayList<SongWikiFact>()
        val paragraphs = ArrayList<String>()
        val similar = ArrayList<SongWikiCoverItem>()
        val playlists = ArrayList<SongWikiCoverItem>()

        facts += fromCreators(creators)
        if (summary != null) {
            fromSummary(summary, facts, chips, notes, similar, playlists)
        }
        fromUgc(ugc)?.let { extra ->
            extra.facts.forEach { fact ->
                if (facts.none { it.label == fact.label }) facts += fact
            }
            extra.chips.forEach { chip ->
                if (chip !in chips) chips += chip
            }
            extra.notes.forEach { note ->
                if (notes.none { it.value == note.value }) notes += note
            }
        }
        fromWikiInfo(wikiInfo).forEach { text ->
            if (text !in paragraphs && notes.none { it.value == text }) paragraphs += text
        }
        return SongWikiPage(
            facts = facts.distinctBy { "${it.label}\u0000${it.value}" },
            chips = chips.distinct(),
            notes = notes.distinctBy { it.value },
            paragraphs = paragraphs.distinct(),
            similar = similar.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
        )
    }

    private fun fromSummary(
        json: JSONObject,
        facts: MutableList<SongWikiFact>,
        chips: MutableList<String>,
        notes: MutableList<SongWikiFact>,
        similar: MutableList<SongWikiCoverItem>,
        playlists: MutableList<SongWikiCoverItem>,
    ) {
        val data = unwrapData(json)
        val blocks = data.optJSONArray("blocks") ?: return
        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: continue
            val code = blockCode(block)
            if (shouldSkipBlock(code)) continue
            when {
                code.contains("SIMILAR_SONG") ->
                    collectCovers(block, "SONG", similar)
                code.contains("RELATED_PLAYLIST") ->
                    collectCovers(block, "PLAYLIST", playlists)
                code.contains("SONG_BASIC") || code.contains("BASIC") ->
                    parseBasic(block, facts, chips, notes)
                else -> {
                    collectCovers(block, "SONG", similar)
                    collectCovers(block, "PLAYLIST", playlists)
                }
            }
        }
    }

    private fun parseBasic(
        block: JSONObject,
        facts: MutableList<SongWikiFact>,
        chips: MutableList<String>,
        notes: MutableList<SongWikiFact>,
    ) {
        val creatives = block.optJSONArray("creatives") ?: return
        for (i in 0 until creatives.length()) {
            val creative = creatives.optJSONObject(i) ?: continue
            val type = creative.optString("creativeType", "").trim()
            val ui = creative.optJSONObject("uiElement")
            val label = titleOf(ui, "mainTitle") ?: titleOf(ui, "title")
            when (type) {
                "songTag", "songBizTag" -> {
                    resources(creative).forEach { res ->
                        titleOf(res.optJSONObject("uiElement"), "mainTitle")?.let { chips += it }
                    }
                }
                "language", "bpm" -> {
                    val value = firstTextLink(ui)
                    if (!label.isNullOrBlank() && !value.isNullOrBlank()) {
                        facts += SongWikiFact(label, value)
                    }
                }
                "entertainment", "songAward" -> {
                    resources(creative).forEach { res ->
                        val rui = res.optJSONObject("uiElement")
                        val title = titleOf(rui, "mainTitle") ?: return@forEach
                        val sub = firstSubTitle(rui)
                        val text = if (sub.isNullOrBlank()) title else "$title · $sub"
                        facts += SongWikiFact(label ?: if (type == "songAward") "奖项" else "影视", text)
                    }
                }
                "songComment" -> {
                    resources(creative).forEach { res ->
                        val rui = res.optJSONObject("uiElement")
                        val body = firstDescription(rui) ?: return@forEach
                        val who = titleOf(rui, "mainTitle")?.removePrefix("乐评来自")?.trim()
                        notes += SongWikiFact(
                            if (who.isNullOrBlank()) "乐评" else "乐评 · $who",
                            body,
                        )
                    }
                }
                "sheet" -> Unit
                else -> {
                    val link = firstTextLink(ui)
                    if (!label.isNullOrBlank() && !link.isNullOrBlank()) {
                        facts += SongWikiFact(label, link)
                    }
                }
            }
        }
    }

    private fun collectCovers(
        block: JSONObject,
        type: String,
        out: MutableList<SongWikiCoverItem>,
    ) {
        val want = type.uppercase()
        val creatives = block.optJSONArray("creatives") ?: return
        for (i in 0 until creatives.length()) {
            val creative = creatives.optJSONObject(i) ?: continue
            resources(creative).forEach { res ->
                val rt = res.optString("resourceType", "").uppercase()
                if (rt != want && rt != type) return@forEach
                val id = jsonLong(res, "resourceId")
                if (id <= 0L) return@forEach
                val ui = res.optJSONObject("uiElement")
                val title = titleOf(ui, "mainTitle") ?: return@forEach
                val subtitle = firstSubTitle(ui).orEmpty()
                val cover = firstImage(ui)
                val play = playCountOf(res)
                out += SongWikiCoverItem(id, title, subtitle, cover, play)
            }
        }
    }

    private fun fromCreators(json: JSONObject?): List<SongWikiFact> {
        if (json == null) return emptyList()
        val data = unwrapData(json)
        val roles = data.optJSONArray("songCreatorsRoleVos") ?: return emptyList()
        val out = ArrayList<SongWikiFact>()
        for (i in 0 until roles.length()) {
            val role = roles.optJSONObject(i) ?: continue
            val label = cleanText(role.opt("roleName")) ?: continue
            val names = ArrayList<String>()
            val metas = role.optJSONArray("creatorMetaVOS") ?: JSONArray()
            for (m in 0 until metas.length()) {
                val meta = metas.optJSONObject(m) ?: continue
                cleanText(meta.opt("artistName"))?.let { names += it }
            }
            val value = names.distinct().joinToString("、")
            if (value.isNotBlank()) out += SongWikiFact(label, value)
        }
        return out
    }

    private fun fromUgc(json: JSONObject?): SongWikiPage? {
        if (json == null) return null
        val data = unwrapData(json)
        if (data.length() == 0) return null
        val facts = ArrayList<SongWikiFact>()
        val chips = ArrayList<String>()
        val notes = ArrayList<SongWikiFact>()
        fun takeFact(label: String, keys: List<String>) {
            val value = firstText(data, keys) ?: return
            facts += SongWikiFact(label, value)
        }
        takeFact("语种", listOf("language", "lang", "songLanguage"))
        takeFact("风格", listOf("genre", "style", "songGenre"))
        takeFact("BPM", listOf("bpm", "BPM"))
        takeFact("曲调", listOf("tone", "key", "songKey"))
        takeFact("作词", listOf("lyricist", "lyricists", "lyricWriter"))
        takeFact("作曲", listOf("composer", "composers"))
        takeFact("编曲", listOf("arranger", "arrangement"))
        takeFact("出品", listOf("company", "publishCompany", "label"))
        takeFact("发行", listOf("publishTime", "publishDate", "pubTime"))
        firstText(data, listOf("alias", "transName", "transNames", "alia"))?.let {
            chips += it.split(Regex("[,，/、|]")).map { part -> part.trim() }.filter { it.isNotEmpty() }
        }
        stringList(data.opt("tags")).forEach { chips += it }
        listOf("briefDesc", "description", "desc", "introduction", "intro", "wiki", "content", "summary")
            .mapNotNull { key -> cleanText(data.opt(key)) }
            .distinct()
            .forEach { notes += SongWikiFact("介绍", it) }
        return SongWikiPage(facts = facts, chips = chips, notes = notes).takeUnless { it.isEmpty }
    }

    private fun fromWikiInfo(json: JSONObject?): List<String> {
        if (json == null) return emptyList()
        val out = ArrayList<String>()
        walkWikiInfo(json, out, 0)
        return out.distinct().filter { it.length >= 24 }
    }

    private fun walkWikiInfo(node: Any?, out: MutableList<String>, depth: Int) {
        if (node == null || node === JSONObject.NULL || depth > 8) return
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) walkWikiInfo(node.opt(i), out, depth + 1)
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val kl = key.lowercase()
                    if (kl.contains("memory") || kl.contains("listen") || kl.contains("cursor")) continue
                    val v = node.opt(key)
                    if (v is String) {
                        val t = cleanText(v)
                        if (t != null && t.length >= 24 &&
                            (kl.contains("desc") || kl.contains("content") || kl.contains("text") ||
                                kl.contains("intro") || kl.contains("html") || kl.contains("body"))
                        ) {
                            out += t
                        }
                    } else {
                        walkWikiInfo(v, out, depth + 1)
                    }
                }
            }
        }
    }

    private fun unwrapData(json: JSONObject): JSONObject {
        val d1 = json.optJSONObject("data") ?: return json
        val d2 = d1.optJSONObject("data")
        return when {
            d2 != null && (d2.has("blocks") || d2.has("songCreatorsRoleVos")) -> d2
            else -> d1
        }
    }

    private fun blockCode(block: JSONObject): String {
        val raw = block.optString("code", "").ifBlank { block.optString("blockCode", "") }
        return raw.uppercase()
    }

    private fun shouldSkipBlock(code: String): Boolean {
        if (code.isBlank()) return false
        return code.contains("MUSIC_MEMORY") ||
            code.contains("ADVERT") ||
            code.contains("BANNER") ||
            code.contains("_AD") ||
            code.contains("VIP_ENTRY") ||
            code.contains("RCMD_AD") ||
            code.contains("SONG_GRADE")
    }

    private fun resources(creative: JSONObject): List<JSONObject> {
        val arr = creative.optJSONArray("resources") ?: creative.optJSONArray("resource")
            ?: return emptyList()
        val out = ArrayList<JSONObject>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out += it }
        }
        return out
    }

    private fun titleOf(ui: JSONObject?, key: String): String? {
        if (ui == null) return null
        val raw = ui.opt(key) ?: return null
        return when (raw) {
            is JSONObject -> cleanText(raw.opt("title")) ?: cleanText(raw.opt("text"))
            else -> cleanText(raw)
        }
    }

    private fun firstSubTitle(ui: JSONObject?): String? {
        if (ui == null) return null
        titleOf(ui, "subTitle")?.let { return it }
        val arr = ui.optJSONArray("subTitles") ?: return null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            cleanText(item?.opt("title"))?.let { return it }
        }
        return null
    }

    private fun firstTextLink(ui: JSONObject?): String? {
        if (ui == null) return null
        val arr = ui.optJSONArray("textLinks") ?: return null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            cleanText(item.opt("text"))?.let { return it }
        }
        return null
    }

    private fun firstDescription(ui: JSONObject?): String? {
        if (ui == null) return null
        val arr = ui.optJSONArray("descriptions") ?: return null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            cleanText(item.opt("description"))?.let { return it }
        }
        return null
    }

    private fun firstImage(ui: JSONObject?): String? {
        if (ui == null) return null
        val arr = ui.optJSONArray("images") ?: return null
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            NcmLibraryParse.ncmHttpsImage(item.optString("imageUrl", ""))?.let { return it }
        }
        return null
    }

    private fun playCountOf(res: JSONObject): Long {
        val ext = res.opt("resourceExt")
        val obj = when (ext) {
            is JSONObject -> ext
            is String -> runCatching { JSONObject(ext) }.getOrNull()
            else -> null
        } ?: return 0L
        return jsonLong(obj, "playCount")
    }

    private fun jsonLong(obj: JSONObject, key: String): Long {
        if (!obj.has(key) || obj.isNull(key)) return 0L
        return when (val v = obj.opt(key)) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull() ?: 0L
            else -> 0L
        }
    }

    private fun firstText(obj: JSONObject?, keys: List<String>): String? {
        if (obj == null) return null
        for (key in keys) {
            cleanText(obj.opt(key))?.let { return it }
        }
        return null
    }

    private fun cleanText(raw: Any?): String? {
        if (raw == null || raw === JSONObject.NULL) return null
        when (raw) {
            is JSONArray -> {
                val parts = stringList(raw)
                return parts.joinToString("、").takeIf { it.isNotBlank() }
            }
            is JSONObject -> {
                return cleanText(raw.opt("title"))
                    ?: cleanText(raw.opt("text"))
                    ?: cleanText(raw.opt("name"))
                    ?: cleanText(raw.opt("value"))
            }
        }
        var s = raw.toString().trim()
        if (s.isEmpty() || s.equals("null", true) || s.equals("undefined", true)) return null
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("orpheus://")) return null
        if (s.startsWith("{") || s.startsWith("[")) return null
        if (s.contains('<') && s.contains('>')) {
            s = s.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
        }
        if (s.length > 1600) return s.take(1600).trimEnd() + "…"
        return s.takeIf { it.isNotBlank() }
    }

    private fun stringList(raw: Any?): List<String> {
        if (raw == null || raw === JSONObject.NULL) return emptyList()
        if (raw is JSONArray) {
            val out = ArrayList<String>(raw.length())
            for (i in 0 until raw.length()) {
                cleanText(raw.opt(i))?.let { out += it }
            }
            return out
        }
        return cleanText(raw)?.let { listOf(it) } ?: emptyList()
    }
}

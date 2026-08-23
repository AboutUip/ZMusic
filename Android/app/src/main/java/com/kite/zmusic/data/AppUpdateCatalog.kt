package com.kite.zmusic.data

import java.net.URLEncoder

interface AppUpdateCatalogSource {
    suspend fun latest(): LatestRelease?
    suspend fun exact(version: String): AppUpdateOffer?
    suspend fun rangePage(): List<AppUpdateOffer>
}

class AppUpdateCatalog(
    private val client: CommunityXaiopClient,
) : AppUpdateCatalogSource {

    override suspend fun latest(): LatestRelease? {
        val snapshot = client.getQuiet(client.httpUrl(LatestPath)) ?: return null
        return AppUpdateParse.parseLatest(snapshot)
    }

    override suspend fun exact(version: String): AppUpdateOffer? {
        val q = ChangelogRoster.normalizeVersion(version)
        if (q.isEmpty()) return null
        val encoded = URLEncoder.encode(q, Charsets.UTF_8.name())
        val snapshot = client.getQuiet(
            client.httpUrl(SearchPath, "q=$encoded&limit=20"),
        ) ?: return null
        val offers = AppUpdateParse.parseOfferPage(snapshot).map { resolve(it) }
        return AppUpdateLogic.pickExact(offers, q)
    }

    override suspend fun rangePage(): List<AppUpdateOffer> {
        val snapshot = client.getQuiet(
            client.httpUrl(RangePath, "page=1&per_page=20"),
        ) ?: return emptyList()
        return AppUpdateParse.parseOfferPage(snapshot).map { resolve(it) }
    }

    private fun resolve(offer: AppUpdateOffer): AppUpdateOffer =
        offer.copy(apk = offer.apk.copy(url = client.resolveUrl(offer.apk.url)))

    companion object {
        private const val LatestPath = "/api/v1/communities/zmusic/releases/latest"
        private const val SearchPath = "/api/v1/communities/zmusic/releases/search"
        private const val RangePath = "/api/v1/communities/zmusic/releases"
    }
}

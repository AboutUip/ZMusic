package com.kite.zmusic.data

class PartnerRepository(
    client: CommunityXaiopClient,
) : PagedCommunityCatalog<PartnerEntry>(
    client = client,
    rangePath = "/api/v1/communities/zmusic/vendors",
    searchPath = "/api/v1/communities/zmusic/vendors/search",
    perPage = 50,
    searchLimit = 50,
    parse = { snapshot ->
        val page = PartnerRoster.parseRemote(snapshot)
        page.copy(
            entries = page.entries.map { entry ->
                entry.copy(logo = client.resolveUrl(entry.logo))
            },
        )
    },
    listKey = { it.listKey },
    normalizeQuery = ::normalizeCatalogQuery,
)

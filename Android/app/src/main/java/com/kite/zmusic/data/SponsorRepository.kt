package com.kite.zmusic.data

class SponsorRepository(
    client: CommunityXaiopClient,
) : PagedCommunityCatalog<SponsorEntry>(
    client = client,
    rangePath = "/api/v1/communities/zmusic/sponsors",
    searchPath = "/api/v1/communities/zmusic/sponsors/search",
    perPage = 50,
    searchLimit = 50,
    parse = SponsorRoster::parseRemote,
    listKey = { it.listKey },
    normalizeQuery = ::normalizeCatalogQuery,
)

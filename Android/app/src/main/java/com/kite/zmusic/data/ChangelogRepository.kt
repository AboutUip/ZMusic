package com.kite.zmusic.data

class ChangelogRepository(
    client: CommunityXaiopClient,
) : PagedCommunityCatalog<ChangelogEntry>(
    client = client,
    rangePath = "/api/v1/communities/zmusic/releases",
    searchPath = "/api/v1/communities/zmusic/releases/search",
    perPage = 20,
    searchLimit = 50,
    parse = ChangelogRoster::parseRemote,
    listKey = { it.listKey },
    normalizeQuery = ChangelogRoster::normalizeQuery,
)

package com.kite.zmusic.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NcmTrackFeeTest {
    @Test
    fun songDetailFeeMarksVipOnlyWhenFeeIsOne() {
        val json = JSONObject(
            """
            {"code":200,"songs":[
              {"id":1,"name":"Free","dt":1000,"fee":0,"ar":[{"name":"A"}]},
              {"id":2,"name":"Vip","dt":1000,"fee":1,"ar":[{"name":"B"}]},
              {"id":3,"name":"Album","dt":1000,"fee":4,"ar":[{"name":"C"}]},
              {"id":4,"name":"LowFree","dt":1000,"fee":8,"ar":[{"name":"D"}]}
            ]}
            """.trimIndent(),
        )
        val tracks = NcmLibraryParse.tracksFromSongDetail(json)
        assertEquals(listOf(false, true, false, false), tracks.map { it.isVipSong })
        assertEquals(listOf(0, 1, 4, 8), tracks.map { it.fee })
    }

    @Test
    fun privilegeArrayFillsFeeWhenSongOmitsIt() {
        val json = JSONObject(
            """
            {"code":200,"songs":[
              {"id":9,"name":"OnlyPriv","dt":1,"ar":[{"name":"E"}]}
            ],"privileges":[{"id":9,"fee":1}]}
            """.trimIndent(),
        )
        val track = NcmLibraryParse.tracksFromSongDetail(json).single()
        assertTrue(track.isVipSong)
        assertEquals(1, track.fee)
    }

    @Test
    fun nestedPrivilegeFeeAndPlaylistTracks() {
        val song = JSONObject(
            """
            {"id":7,"name":"Nested","dt":1,"ar":[{"name":"F"}],"privilege":{"fee":1}}
            """.trimIndent(),
        )
        assertTrue(NcmLibraryParse.trackFromSongObject(song)!!.isVipSong)

        val playlist = JSONObject(
            """
            {"code":200,"playlist":{"tracks":[
              {"id":8,"name":"InList","dt":1,"fee":1,"ar":[{"name":"G"}]}
            ]}}
            """.trimIndent(),
        )
        assertTrue(NcmLibraryParse.tracksFromPlaylistDetail(playlist).single().isVipSong)
    }

    @Test
    fun cacheRoundTripKeepsVipFee() {
        val track = TrackRow(
            id = 2L,
            name = "Vip",
            artists = "B",
            album = null,
            durationMs = 1000L,
            fee = 1,
        )
        val restored = NcmLibraryParse.trackFromCacheJson(NcmLibraryParse.trackToCacheJson(track))
        assertEquals(1, restored?.fee)
        assertTrue(restored!!.isVipSong)

        val free = track.copy(fee = 0)
        val restoredFree = NcmLibraryParse.trackFromCacheJson(NcmLibraryParse.trackToCacheJson(free))
        assertEquals(0, restoredFree?.fee)
        assertFalse(restoredFree!!.isVipSong)
    }

    @Test
    fun oldPlaylistCacheWithoutFeeRevisionIsUntrusted() {
        assertFalse(NcmLibraryParse.trackFeeCacheTrusted(JSONObject()))
        assertFalse(NcmLibraryParse.trackFeeCacheTrusted(JSONObject().put("feeRevision", 0)))
        assertTrue(
            NcmLibraryParse.trackFeeCacheTrusted(
                JSONObject().put("feeRevision", NcmLibraryParse.TRACK_FEE_CACHE_REVISION),
            ),
        )
    }

    @Test
    fun refreshingFeeKeepsLocalAudioOnAPreviouslyCachedSong() {
        val cached = TrackRow(
            id = 2L,
            name = "Vip",
            artists = "B",
            album = null,
            durationMs = 1000L,
            fee = 0,
            localAudioUri = "content://song",
        )
        val fresh = cached.copy(fee = 1, localAudioUri = null)
        val merged = NcmLibraryParse.preferExistingCover(cached, fresh)
        assertTrue(merged.isVipSong)
        assertEquals("content://song", merged.localAudioUri)
    }
}

package com.mue.core.artist

import com.mue.core.database.FollowedArtistDao
import com.mue.core.database.FollowedArtistEntity
import com.mue.core.network.ArtistApiClient
import com.mue.core.network.ArtistDetailDto
import com.mue.core.network.ArtistVideoDto
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistService @Inject constructor(
    private val artistApiClient: ArtistApiClient,
    private val followedArtistDao: FollowedArtistDao
) {
    suspend fun getArtistDetails(artistId: String): Result<ArtistDetailDto> {
        return artistApiClient.getArtistDetails(artistId)
    }

    suspend fun getArtistVideos(artistId: String): Result<List<ArtistVideoDto>> {
        return artistApiClient.getArtistVideos(artistId)
    }

    fun isFollowing(artistId: String): Flow<Boolean> {
        return followedArtistDao.isFollowing(artistId)
    }

    fun getFollowedArtists(): Flow<List<FollowedArtistEntity>> {
        return followedArtistDao.getAll()
    }

    suspend fun toggleFollow(artistId: String, artistName: String, thumbnailUrl: String? = null): Boolean {
        val currentlyFollowing = followedArtistDao.isFollowingSync(artistId)
        if (currentlyFollowing) {
            followedArtistDao.delete(artistId)
            artistApiClient.unfollowArtist(artistId)
            return false
        } else {
            followedArtistDao.insert(
                FollowedArtistEntity(
                    artistId = artistId,
                    name = artistName,
                    thumbnailUrl = thumbnailUrl,
                    followedAt = System.currentTimeMillis()
                )
            )
            artistApiClient.followArtist(artistId, artistName, thumbnailUrl)
            return true
        }
    }
}


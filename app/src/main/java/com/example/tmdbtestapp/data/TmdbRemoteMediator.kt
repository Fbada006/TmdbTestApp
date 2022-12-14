package com.example.tmdbtestapp.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.example.tmdbtestapp.data.models.DbMovie
import com.example.tmdbtestapp.data.models.DbRemoteKeys
import com.example.tmdbtestapp.mappers.toDbMovie
import com.example.tmdbtestapp.network.TmdbService
import com.example.tmdbtestapp.utils.TMDB_STARTING_PAGE_INDEX
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@OptIn(ExperimentalPagingApi::class)
class TmdbRemoteMediator @Inject constructor(
    private val service: TmdbService,
    private val tmdbMovieDatabase: TmdbMovieDatabase
) : RemoteMediator<Int, DbMovie>() {

    override suspend fun load(loadType: LoadType, state: PagingState<Int, DbMovie>): MediatorResult {
        val page = when (loadType) {
            LoadType.REFRESH -> {
                val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                remoteKeys?.nextKey?.minus(1) ?: TMDB_STARTING_PAGE_INDEX
            }
            LoadType.PREPEND -> {
                val remoteKeys = getRemoteKeyForFirstItem(state)
                val prevKey = remoteKeys?.prevKey ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                prevKey
            }
            LoadType.APPEND -> {
                val remoteKeys = getRemoteKeyForLastItem(state)
                val nextKey = remoteKeys?.nextKey ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                nextKey
            }
        }

        return try {
            val movieResult = service.getMovies(page = page)

            val movies = movieResult.movieList
            val endOfPaginationReached = movies.isEmpty()

            tmdbMovieDatabase.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    // clear all tables in the database because we are getting fresh data
                    tmdbMovieDatabase.remoteKeysDao().clearRemoteKeys()
                    tmdbMovieDatabase.movieDao().clearMovies()
                }

                val prevKey = if (page == TMDB_STARTING_PAGE_INDEX) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                val keys = movies.map {
                    DbRemoteKeys(movieId = it.id, prevKey = prevKey, nextKey = nextKey)
                }

                tmdbMovieDatabase.remoteKeysDao().insertAll(keys)
                tmdbMovieDatabase.movieDao().insertAll(movies.map { it.toDbMovie() })
            }

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: IOException) {
            MediatorResult.Error(exception)
        } catch (exception: HttpException) {
            MediatorResult.Error(exception)
        } catch (exception: Exception) {
            MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, DbMovie>): DbRemoteKeys? {
        // Get the last page that was retrieved, that contained items.
        // From that last page, get the last item
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { movie ->
                // Get the remote keys of the last item retrieved
                tmdbMovieDatabase.remoteKeysDao().remoteKeyMovieId(movie.id)
            }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, DbMovie>): DbRemoteKeys? {
        // Get the first page that was retrieved that contained items.
        // From that first page, get the first item
        return state.pages.firstOrNull { pagingSource ->
            pagingSource.data.isNotEmpty()
        }?.data?.firstOrNull()
            ?.let { movie ->
                // Get the remote keys of the first items retrieved
                tmdbMovieDatabase.remoteKeysDao().remoteKeyMovieId(movie.id)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(
        state: PagingState<Int, DbMovie>
    ): DbRemoteKeys? {
        // The paging library is trying to load data after the anchor position
        // Get the item closest to the anchor position
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { movieId ->
                tmdbMovieDatabase.remoteKeysDao().remoteKeyMovieId(movieId)
            }
        }
    }
}
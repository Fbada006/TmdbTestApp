package com.example.tmdbtestapp.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.tmdbtestapp.data.models.DbMovie

@Dao
interface MovieDao {

    @Query("SELECT * FROM movies")
    fun getAllMovies(): PagingSource<Int, DbMovie>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movies: List<DbMovie>)

    @Query("SELECT * FROM movies WHERE id = :movieId")
    suspend fun getMovieById(movieId: Long?): DbMovie?

    @Query("DELETE FROM movies")
    suspend fun clearMovies()
}
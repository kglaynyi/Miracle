package com.miracle.kglaynyi.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.miracle.kglaynyi.model.Movie;

import java.util.List;

@Dao
public interface MovieDao {
    @Query("SELECT * FROM Movie m WHERE m.title IS NOT NULL AND m.disabled=0 AND m.fileidForDB = (SELECT m2.fileidForDB FROM Movie m2 WHERE m2.title=m.title AND m2.disabled=0 ORDER BY (m2.poster_path IS NOT NULL) DESC, m2.fileidForDB DESC LIMIT 1) ORDER BY m.title COLLATE NOCASE")
    List<Movie> getAll();

    @Query("SELECT * FROM Movie WHERE id=:id and disabled=0")
    Movie byId(int id);

    @Query("SELECT * FROM Movie WHERE id=:id and disabled=0")
    List<Movie> getAllById(int id);

    @Query("SELECT * FROM Movie WHERE id=:id and disabled=0 ORDER BY size DESC limit 1 ")
    Movie byIdLargest(int id);

    @Query("SELECT * FROM Movie WHERE (fileName LIKE '%' || :string || '%' OR title like '%' || :string || '%' OR urlString like '%' || :string || '%' or overview like '%' || :string || '%') and disabled=0")
    List<Movie> getSearchQuery(String string);

    @Query("SELECT * FROM Movie WHERE fileName LIKE :fileName and disabled=0")
    Movie getByFileName(String fileName);

    @Query("SELECT * FROM Movie WHERE poster_path IS NOT NULL and disabled=0 and vote_count>5000 Group by title ORDER BY vote_average DESC Limit 10 ")
    List<Movie> getTopRated();

    @Query("SELECT * from Movie  WHERE backdrop_path IS NOT NULL and disabled=0 Group by title ORDER BY modifiedTime DESC Limit 10")
    List<Movie> getrecentlyadded();

    @Query("SELECT * FROM Movie WHERE poster_path IS NOT NULL and disabled=0 Group by title ORDER BY release_date DESC Limit 10")
    List<Movie> getrecentreleases();

    @Insert
    void insert(Movie... movies);

    @Delete
    void delete(Movie movie);

    @Query("Delete from Movie where index_id = :index_id")
    void deleteAllFromthisIndex(int index_id);

    @Query("SELECT * FROM Movie WHERE Played = 1 and title is not null group by title and disabled=0")
    List<Movie> getPlayed();

    @Query("Update Movie set played = 1 where id =:id and disabled=0")
    void updatePlayed(int id);

    @Query("SELECT * FROM Movie WHERE title is NULL and disabled=0")
    List<Movie> getFilesWithNoTitle();

    @Query("SELECT * FROM Movie WHERE fileName LIKE :fileName and disabled=0")
    List<Movie> getAllByFileName(String fileName);


    @Query("SELECT COUNT (fileName) FROM Movie WHERE index_id =:index_id AND TITLE IS NOT NULL")
    int getNoOfMovies(int index_id);

    @Query("UPDATE Movie set disabled=1 WHERE index_id=:index_id ")
    void disableFromThisIndex(int index_id);

    @Query("Update movie set disabled = 0 where index_id=:index_id")
    void enableFromThisIndex(int index_id);

    @Query("SELECT * FROM Movie WHERE gd_id =:gd_id")
    Movie getByGdId(String gd_id);

    @Query("SELECT * FROM Movie WHERE urlString=:url AND disabled=0 LIMIT 1")
    Movie findByUrl(String url);

    @Query("SELECT * FROM Movie WHERE index_id=:indexId")
    List<Movie> getAllFromIndex(int indexId);

    @Query("DELETE FROM Movie WHERE gd_id =:id")
    void deleteByGdId(String id);

    @Query("UPDATE Movie SET addToList=1 WHERE id=:movieId")
    void updateAddToList(int movieId);

    @Query("SELECT * FROM Movie WHERE addToList=1 and disabled=0")
    List<Movie> getWatchlisted();

    @Query("UPDATE Movie SET addToList=0 WHERE id=:movieId")
    void updateRemoveFromList(int movieId);
}

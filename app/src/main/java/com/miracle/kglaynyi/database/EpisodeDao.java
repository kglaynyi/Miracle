package com.miracle.kglaynyi.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.miracle.kglaynyi.model.TVShowInfo.Episode;

import java.util.List;
import java.util.Date;

@Dao
public interface EpisodeDao {
    @Query("SELECT * FROM Episode ")
    List<Episode> getAll();

    @Query("SELECT * FROM Episode WHERE id LIKE :id and disabled =0")
    Episode find(int id);

    @Query("SELECT * FROM Episode WHERE fileName LIKE :fileName and disabled =0")
    Episode findByFileName(String fileName);

    @Query("SELECT * FROM Episode WHERE season_id=:season_id AND played=0 and disabled =0 order by episode_number limit 1 ")
    Episode getNextEpisodeInSeason(int season_id);

    @Query("SELECT * FROM Episode WHERE show_id=:show_id AND played=0 and disabled =0 order by season_number,episode_number limit 1")
    Episode getNextEpisodeInTVShow(int show_id);

    @Query("SELECT * FROM Episode WHERE id=:id and disabled =0")
    List<Episode> byEpisodeId(int id);

    @Query("SELECT * FROM Episode WHERE id=:id and disabled =0 ORDER BY cast(size as unsigned) desc, (COALESCE(gd_id,'') != '') DESC, idForDB DESC limit 1")
    Episode byEpisodeIdLargest(int id);

    @Query("UPDATE Episode SET played = 1 WHERE id = :episodeId and disabled =0")
    void updatePlayed(int episodeId);

    @Query("SELECT * FROM Episode WHERE show_id=:show_id AND season_id=:season_id and disabled =0 GROUP BY id ORDER BY episode_number ASC")
    List<Episode> getFromThisSeason(int show_id, int season_id);


    @Query("SELECT * FROM Episode WHERE season_id=:season_id and disabled =0")
    List<Episode> getFromSeasonOnly(int season_id);


    @Query("SELECT * FROM Episode WHERE show_id=:show_id and disabled =0")
    List<Episode> getFromThisShow(long show_id);
    @Insert
    void insert(Episode... episodes);

    @Delete
    void delete(Episode episode);

    @Query("Delete from Episode where  index_id = :index_id")
    void deleteAllFromThisIndex(int index_id);


    @Query("select * from Episode where  index_id = :index_id and disabled =0")
    Episode findByLink(int index_id);

    @Query("Delete from Episode where urlString=:link ")
    void deleteByLink(String link);

    @Query("select count( distinct show_id ) from Episode  where index_id = :index_id ")
    int getNoOfShows(int index_id);

    @Query("UPDATE Episode set disabled=1 WHERE  index_id = :index_id")
    void disableFromThisIndex(int index_id);

    @Query("UPDATE Episode set disabled=0 WHERE index_id = :index_id")
    void enableFromThisIndex(int index_id);

    @Query("SELECT * FROM Episode WHERE show_id=:show_id and disabled =0 order by season_number,episode_number limit 1 ")
    Episode getFirstAvailableEpisode(long show_id);


    @Query("SELECT * FROM Episode WHERE gd_id =:id")
    Episode findByGdId(String id);

    @Query("SELECT * FROM Episode WHERE urlString=:url AND disabled=0 LIMIT 1")
    Episode findByUrl(String url);

    @Query("SELECT * FROM Episode WHERE index_id=:indexId")
    List<Episode> getAllFromIndex(int indexId);

    @Query("DELETE FROM Episode WHERE gd_id =:id")
    void deleteByGdId(String id);
    @Query("SELECT * FROM Episode WHERE index_id=:indexId AND fileName=:fileName AND size=:size ORDER BY idForDB DESC LIMIT 1")
    Episode findByIndexFileAndSize(int indexId, String fileName, String size);

    @Query("DELETE FROM Episode WHERE index_id=:indexId AND fileName=:fileName AND size=:size")
    int deleteByIndexFileAndSize(int indexId, String fileName, String size);

    @Query("DELETE FROM Episode WHERE index_id=:indexId AND fileName=:fileName AND size=:size AND COALESCE(gd_id,'') != :keepGdId")
    int deleteDuplicateSources(int indexId, String fileName, String size, String keepGdId);

    @Query("DELETE FROM Episode WHERE index_id=:indexId AND fileName=:fileName AND size=:size AND gd_id=:stableId AND idForDB != (SELECT MAX(idForDB) FROM Episode WHERE index_id=:indexId AND fileName=:fileName AND size=:size AND gd_id=:stableId)")
    int deleteRepeatedStableSource(int indexId, String fileName, String size, String stableId);

    @Query("UPDATE Episode SET urlString=:url, fileName=:fileName, size=:size, mimeType=:mimeType, modifiedTime=:modifiedTime, folder_path=:folderPath WHERE gd_id=:stableId")
    void updateSourceMetadata(String stableId, String url, String fileName, String size, String mimeType, Date modifiedTime, String folderPath);

    @Query("DELETE FROM Episode WHERE index_id=:indexId AND (folder_path=:folderPath OR folder_path LIKE :folderPrefix)")
    int deleteByFolderPrefix(int indexId, String folderPath, String folderPrefix);

    @Query("SELECT * FROM Episode WHERE index_id=:indexId AND (folder_path=:folderPath OR folder_path LIKE :folderPrefix)")
    List<Episode> getByFolderPrefix(int indexId, String folderPath, String folderPrefix);

    @Query("SELECT * FROM Episode WHERE show_id=:showId AND disabled=0 GROUP BY id ORDER BY season_number, episode_number")
    List<Episode> getAvailableEpisodesForShow(long showId);

    @Query("SELECT * FROM Episode WHERE show_id=:showId AND disabled=0 AND (season_number>:seasonNumber OR (season_number=:seasonNumber AND episode_number>:episodeNumber)) GROUP BY id ORDER BY season_number, episode_number LIMIT 1")
    Episode getFollowingEpisode(long showId, int seasonNumber, int episodeNumber);

}

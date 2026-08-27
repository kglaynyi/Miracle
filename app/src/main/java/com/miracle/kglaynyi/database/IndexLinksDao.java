package com.miracle.kglaynyi.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.miracle.kglaynyi.model.IndexLink;

import java.util.List;

@Dao
public interface IndexLinksDao {

    @Query("SELECT * FROM IndexLink")
    List<IndexLink> getAll();

    @Query("SELECT * FROM IndexLink where disabled=0")
    List<IndexLink> getAllEnabled();

    @Query("SELECT * FROM IndexLink WHERE link=:link")
    IndexLink find(String link);

    @Insert
    void insert(IndexLink... indexLinks);

    @Delete
    void delete(IndexLink indexLink);

    @Query("DELETE FROM IndexLink WHERE id=:id")
    void deleteById(int id);

    @Query("Update indexlink set disabled=1 WHERE id=:index_id  ")
    void disableIndex(int index_id);

    @Query("Update indexlink set disabled=0 WHERE id=:index_id  ")
    void enableIndex(int index_id);

    @Query("UPDATE IndexLink SET folderType=:folderType WHERE id=:indexId")
    void updateFolderType(int indexId, String folderType);

}

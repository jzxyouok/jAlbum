package com.backend.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupedFilesDao extends AbstractRecordsStore
{
    private static final Logger logger = LoggerFactory.getLogger(BackupedFilesDao.class);

    private static BackupedFilesDao instance = new BackupedFilesDao();

    private BackupedFilesDao()
    {

    }

    public static BackupedFilesDao getInstance()
    {
        return instance;
    }

    public void checkAndaddOneRecords(String hashStr, String eTag, String objkey)
    {
        if (StringUtils.isBlank(hashStr))
        {
            logger.warn("the input hashstr is blank.");
            return;
        }

        PreparedStatement prep = null;
        ResultSet res = null;
        try
        {
            lock.readLock().lock();
            prep = conn.prepareStatement("select * from backuped where hashstr=?;");
            prep.setString(1, hashStr);
            res = prep.executeQuery();

            if (res.next())
            {
                logger.warn("the file is already backuped: [{}:{}:{}]", hashStr, eTag, objkey);
                closeResource(prep, res);
                lock.readLock().unlock();
                return;
            }
            lock.readLock().unlock();
            closeResource(prep, res);

            lock.writeLock().lock();
            prep = conn.prepareStatement("insert into backuped values(?,?,?);");
            prep.setString(1, hashStr);
            prep.setString(2, eTag);
            prep.setString(3, objkey);
            prep.execute();
            lock.writeLock().unlock();
            closeResource(prep, res);
        }
        catch (SQLException e)
        {
            logger.error("caught: " + hashStr, e);
        }
    }

    public void checkAndCreateTable()
    {
        PreparedStatement prep = null;
        try
        {
            if (checkTableExist("backuped"))
            {
                return;
            }

            prep = conn.prepareStatement(
                    "CREATE TABLE backuped (hashstr STRING PRIMARY KEY, etag STRING (32, 32), objkey STRING);");
            prep.execute();
        }
        catch (Exception e)
        {
            logger.error("caught: ", e);
        }
    }

    public List<String> getAllHashStr()
    {
        List<String> lst = new ArrayList<String>();

        PreparedStatement prep = null;
        ResultSet res = null;
        try
        {
            lock.readLock().lock();
            prep = conn.prepareStatement("select * from backuped;");
            res = prep.executeQuery();

            while (res.next())
            {
                lst.add(res.getString("hashstr"));
            }
        }
        catch (Exception e)
        {
            logger.error("caught: ", e);
        }
        finally
        {
            lock.readLock().unlock();
            closeResource(prep, res);
        }

        return lst;
    }
}

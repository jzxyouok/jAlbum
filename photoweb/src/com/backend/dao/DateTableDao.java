package com.backend.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DateTableDao extends AbstractRecordsStore
{
    private static final Logger logger = LoggerFactory.getLogger(DateTableDao.class);

    private static DateTableDao instance = new DateTableDao();

    private DateTableDao()
    {

    }

    public static DateTableDao getInstance()
    {
        return instance;
    }

    public TreeMap<String, TreeMap<String, TreeMap<String, DateRecords>>> getAllDateRecord()
    {
        TreeMap<String, TreeMap<String, TreeMap<String, DateRecords>>> allrecords = new TreeMap<String, TreeMap<String, TreeMap<String, DateRecords>>>();
        lock.readLock().lock();
        PreparedStatement prep = null;
        ResultSet res = null;
        try
        {
            prep = conn.prepareStatement("select * from daterecords;");
            res = prep.executeQuery();

            while (res.next())
            {
                String date = res.getString("datestr");
                if (StringUtils.isBlank(date) || date.length() != 8)
                {
                    logger.error("one empty datestr record: " + (date == null ? "" : date));
                    continue;
                }

                String day = date.substring(6, 8);
                String month = date.substring(4, 6);
                String year = date.substring(0, 4);

                TreeMap<String, TreeMap<String, DateRecords>> myear = allrecords.get(year);
                if (myear == null)
                {
                    myear = new TreeMap<String, TreeMap<String, DateRecords>>();
                    allrecords.put(year, myear);
                }

                TreeMap<String, DateRecords> mmonth = myear.get(month);
                if (mmonth == null)
                {
                    mmonth = new TreeMap<String, DateRecords>();
                    myear.put(month, mmonth);
                }

                DateRecords dr = new DateRecords();
                dr.setDatestr(date);
                dr.setFirstpic(res.getString("firstpichashstr"));
                dr.setPiccount(res.getLong("piccoount"));
                mmonth.put(day, dr);
            }

        }
        catch (Exception e)
        {
            logger.error("caught: ", e);
        }
        finally
        {
            closeResource(prep, res);
            lock.readLock().unlock();
        }

        return allrecords;
    }

    public DateRecords getOneRecordsByDay(String day)
    {
        if (StringUtils.isBlank(day) || day.length() != 8)
        {
            return null;
        }

        lock.readLock().lock();
        PreparedStatement prep = null;
        ResultSet res = null;
        try
        {
            prep = conn.prepareStatement("select * from daterecords where datestr=?;");
            prep.setString(1, day);
            res = prep.executeQuery();

            if (res.next())
            {
                DateRecords dr = new DateRecords();
                dr.setDatestr(day);
                dr.setFirstpic(res.getString("firstpichashstr"));
                dr.setPiccount(res.getLong("piccoount"));
                return dr;
            }

        }
        catch (Exception e)
        {
            logger.warn("caused by: ", e);
        }
        finally
        {
            closeResource(prep, res);
            lock.readLock().unlock();
        }

        return null;
    }

    public void refreshDate()
    {
        logger.warn("refresh all date record.");

        Map<String, DateRecords> dst = UniqPhotosStore.getInstance().genAllDateRecords();
        if (dst == null || dst.isEmpty())
        {
            logger.warn("there is no pic.");
            return;
        }

        lock.writeLock().lock();
        PreparedStatement prep = null;
        try
        {
            prep = conn.prepareStatement("delete from daterecords");
            prep.execute();
            prep.close();

            prep = conn.prepareStatement("insert into daterecords values(?,?,?);");
            for (Entry<String, DateRecords> dr : dst.entrySet())
            {
                prep.setString(1, dr.getKey());
                prep.setLong(2, dr.getValue().getPiccount());
                prep.setString(3, dr.getValue().getFirstpic());
                prep.addBatch();
            }
            prep.executeBatch();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
            logger.warn("caught: ", e);
        }
        finally
        {
            closeResource(prep, null);
            lock.writeLock().unlock();
        }
    }

}

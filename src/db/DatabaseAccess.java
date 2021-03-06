/*
 * DatabaseAccess.java    version 1.0   date 16/12/2015
 * By rjb 
 */
package db;

import java.io.*;
import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.locks.*;

/**
 * This class provides all required access to the physical database file.
 *
 * @author rjb
 *
 */
class DatabaseAccess {

    /**
     * An instance of RandomAccessFile to read the database file.
     */
    private RandomAccessFile database = null;
    
    /**
     * The database file path.
     */
    private static String dbPath = null;

    /**
     * The database length in bytes.
     */
    private long databaseLength;
    
    /**
     * The database records start 70 bytes into the file, so the initial value
     * for this is hard-coded as 70
     */
    private long locationInFile = 70;
    
    /**
     * A variable to hold an empty string of record length.
     */
    private static String emptyRecordString = null;
    
    /**
     * The variable to hold a record number.
     */
    private int recNo;

    /**
     * Initialises the emptyRecordStirn with a string of the correct length.
     */
    static {
        emptyRecordString = new String(new byte[DBRecord.RECORD_LENGTH]);
    }
    
    /**
     * A lock instance for the record map
     */
    private static ReadWriteLock recordMapLock = new ReentrantReadWriteLock();
    
    /**
     * The record map which the database file is read into. This allows the
     * records to map to recNo keys.
     */
    static Map<Integer, DBRecord> recordMap = new LinkedHashMap<Integer, DBRecord>();
    private Object returnObject;

    /**
     * Constructor
     *
     * @param suppliedDbPath A String containing the path to the database file.
     * @throws FileNotFoundException
     * @throws IOException
     */
    public DatabaseAccess(String suppliedDbPath)
            throws FileNotFoundException, IOException {
        if (database == null) {
            database = new RandomAccessFile(suppliedDbPath, "rw");
            databaseLength = database.length();
            getRecordMap(true);
            dbPath = suppliedDbPath;
        }
    }

    /**
     * Reads the values from the database into the recordMap if createMap is
     * true.
     *
     * @param createMap A boolean indicating whether a new recordMap is needed.
     */
    protected void getRecordMap(boolean createMap) {

        locationInFile = 70;

        if (createMap) {
            recordMapLock.writeLock().lock();
        }
        try {
            DBRecord dbr;
            for (recNo = 1; locationInFile != -1; recNo++) {

                dbr = ArrayToObject(recordReader(recNo));
                dbr.setRecNo(recNo);
                if (dbr.getName().equals("")) {
                } else {
                    recordMap.put(recNo, dbr);

                }
            }
        } catch (RecordNotFoundException rnfe) {

            locationInFile = -1;
        } finally {
            if (createMap) {
                recordMapLock.writeLock().unlock();
            }
        }

    }

    /**
     * This method uses the record number to figure out the location of that
     * record in the database file.
     *
     * @param recNo
     * @return number of bytes into the file that record is located.
     */
    private long recNoToLocationInFile(int recNo) {
        long offset = 70;
        offset += (recNo - 1) * DBRecord.RECORD_LENGTH;

        if (offset > databaseLength) {

            offset = -1;
        }

        return offset;
    }

    /**
     * This method reads the physical file and returns the String Array required
     * by the DB interface.
     *
     * @param recNo The record number for the record to be read.
     * @return Record as String array containing all the fields.
     */
    public String[] recordReader(int recNo) throws RecordNotFoundException {
        final byte[] input = new byte[DBRecord.RECORD_LENGTH];
        locationInFile = recNoToLocationInFile(recNo);
        String[] recordArray = new String[7];

        // Get exlusive access to
        // the file, read the entire set of bytes, then release exclusive lock.
        if (locationInFile != -1) {

            try {
                synchronized (database) {
                    database.seek(locationInFile);
                    database.readFully(input);
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }

        }


        /**
         * inner class to assist in converting from the one big byte[] into
         * multiple String[] - one String per field.
         */
        class RecordFieldReader {

            /**
             * field to track the position within the byte array
             */
            private int offset = 0;

            /**
             * converts the flag at the front of each record to a String and
             * checks if it represents a valid record in the database,
             * otherwise, set the record as deleted.
             *
             * @param length the length to be converted from current offset.
             * @return the converted String
             */
            String readFlag(int length) {
                //if flag is not null, record is deleted, so skip the rest.
                //else move on and read the rest.

                String flag = null;
                try {
                    flag = new String(input, offset, length, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                }
                if (flag.contentEquals("��")) {//NB. flag is NUL NUL
                    offset += length;
                    return "";
                } else {
                    return "Deleted Record";
                }
            }

            /**
             * converts the byte stream from the database of each field's length
             * to a string.
             *
             * @param length the length to be converted from current offset.
             * @return the converted String
             */
            String read(int length) {

                String str = null;

                try {
                    str = new String(input, offset, length, "UTF-8");
                } catch (UnsupportedEncodingException ex) {
                    ex.printStackTrace();
                }
                offset += length;
                return str.trim();
            }
        }

        RecordFieldReader readRecord = new RecordFieldReader();

        String flag = readRecord.readFlag(DBRecord.FLAG_LENGTH);
        String name = readRecord.read(DBRecord.NAME_LENGTH);
        String location = readRecord.read(DBRecord.LOCATION_LENGTH);
        String specialties = readRecord.read(DBRecord.SPECIALTIES_LENGTH);
        String staff = readRecord.read(DBRecord.STAFF_LENGTH);
        String rate = readRecord.read(DBRecord.RATE_LENGTH);
        String owner = readRecord.read(DBRecord.CUSTID_LENGTH);

        //If the record is deleted, set recNo variable of the DBRecord object to "Deleted Record"

        if (flag.equals("Deleted Record")) {
            recordArray[0] = "Deleted Record";//can't be null.

        } else {

            recordArray[0] = String.valueOf(recNo);
            recordArray[1] = name;
            recordArray[2] = location;
            recordArray[3] = specialties;
            recordArray[4] = staff;
            recordArray[5] = rate;
            recordArray[6] = owner;
        }
        return recordArray;
    }

    /**
     * This method converts a String [] of the record fields to an object,
     * returning an empty object for a deleted record.
     *
     * @param recordArray The array object to be converted
     * @return DBRecord object
     */
    public DBRecord ArrayToObject(String[] recordArray) {

        if (recordArray[0].equals("Deleted Record")) {
            returnObject = new DBRecord();//Runs the no arg constructor and returns an empty record. 
        } else {
            returnObject = new DBRecord(recordArray[0], recordArray[1], recordArray[2], recordArray[3], recordArray[4], recordArray[5]);
        }
        return (DBRecord) returnObject;
    }

    /**
     * This method searches the recordMap for the passed in String(s), adding
     * the record number to the results list if they are found. This is then
     * converted to the int [] required by the interface.
     *
     * @param criteria The array object containing search criteria
     * @return int [] containing search results.
     */
    public int[] findRecord(String[] criteria) {

        List<Integer> searchResults = new ArrayList<Integer>();
        List<Integer> antiResults = new ArrayList<Integer>();
        boolean found = false;

        recordMapLock.readLock().lock();
        for (Iterator<DBRecord> it = recordMap.values().iterator(); it.hasNext();) {
            DBRecord dbr = it.next();
            String str = dbr.getName() + dbr.getLocation() + dbr.getSpecialties() + dbr.getRate() + dbr.getStaff() + dbr.getCustID();
            for (String i : criteria) {
                //for each String in the criteria array, do a search.
                i = i.toLowerCase();
                str = str.toLowerCase();
                recNo = dbr.getRecNo();
                found = str.contains(i);
                if (!found) {
                    antiResults.add(recNo);
                }
            }
            if (found && !antiResults.contains(recNo)) {
                searchResults.add(recNo);

            } else {
            }
        }
        recordMapLock.readLock().unlock();

        int[] recNoArray = new int[searchResults.size()];
        for (int i = 0; i < recNoArray.length; i++) {
            recNoArray[i] = searchResults.get(i);
        }
        return recNoArray;
    }

    /**
     * Modifies the fields of a record. The new value for field n appears in
     * data[n]. This method only runs if the client trying to make the change
     * holds the lock in the LockerRoom class.
     *
     * @param recNo The number of the record to be modified
     * @param data array of field values for the record to be updated.
     * @throws RecordNotFoundException Indicates there is no record with that
     * recNo.
     */
    public void update(int recNo, String[] data) throws RecordNotFoundException {

        DBRecord dbr;
        boolean create = false;
        if (data.length == 6) {
            dbr = new DBRecord(data[0], data[1], data[2], data[3], data[4], data[5]);
        } else {
            dbr = new DBRecord(data[0], data[1], data[2], data[3], data[4]);
        }
        dbr.setRecNo(recNo);
        try {
            persistDBRecord(dbr, create);
        } catch (IOException ioe) {
            throw new RecordNotFoundException();
        }
    }

    /**
     * This makes a new record in the database.
     *
     * @param data A String array containing the fields for the new record.
     * @return int The value of the new record number.
     * @throws db.DuplicateKeyException if the record number already exists.
     */
    public int create(String[] data) throws DuplicateKeyException {

        boolean create = true;
        DBRecord dbr;

        if (data.length == 6) {
            dbr = new DBRecord(data[0], data[1], data[2], data[3], data[4], data[5]);
        } else {
            dbr = new DBRecord(data[0], data[1], data[2], data[3], data[4]);
        }
        try {
            persistDBRecord(dbr, create);

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return recNo;

    }

    /**
     * This method persists a record object to the database. If the record does
     * not currently exist in the database as determined by the create param
     * then the record will be created.
     *
     * @param dbr The record to store
     * @param create true for creation, false for modification
     * @return Returns the recNO.
     * @throws IOException Indicates there is a problem accessing the data file
     */
    private int persistDBRecord(DBRecord dbr, boolean create) throws IOException {
        // Perform as many operations as we can outside of the synchronized block 
        String flag = null;
        recordMapLock.writeLock().lock();

        if (create == true) {

            try {
                int i = 1;
                while (recordMap.containsKey(i) == true) {
                    i++;
                }
                recNo = i;
                recordMap.put(recNo, dbr);//updates the map. this is before file is changed.

                locationInFile = recNoToLocationInFile(recNo);
                flag = "";

            } finally {
                //The write lock can be released now, because the recordMap is updated. 
                recordMapLock.writeLock().unlock();
            }

        } else if (create == false) {

            try {

                recNo = dbr.getRecNo();
                recordMap.put(recNo, dbr);//updates the map. this is before file is changed.
                locationInFile = recNoToLocationInFile(dbr.getRecNo());

                flag = "";
            } finally {
                recordMapLock.writeLock().unlock();
            }
        }

        final StringBuilder out = new StringBuilder(emptyRecordString);

        /**
         * assists in converting Strings to a byte[]
         */
        class RecordFieldWriter {

            /**
             * current position in byte[]
             */
            private int currentPosition = 0;

            /**
             * creates a valid flag for the record.
             *
             * @param data the String to be converted into part of the byte[].
             * @param length the maximum size of the String
             */
            void writeFlag(String data, int length) {
                out.replace(currentPosition,
                        currentPosition + data.length(),
                        data);

                currentPosition += length;
            }

            /**
             * converts a String of specified length to byte[]
             *
             * @param data the String to be converted into part of the byte[].
             * @param length the maximum size of the String
             */
            void write(String data, int length) {
                while (data.length() < length) {
                    data = data.concat(" ");
                }

                out.replace(currentPosition,
                        currentPosition + data.length(),
                        data);

                currentPosition += length;

            }
        }
        RecordFieldWriter writeRecord = new RecordFieldWriter();
        writeRecord.writeFlag(flag, DBRecord.FLAG_LENGTH);
        writeRecord.write(dbr.getName(), DBRecord.NAME_LENGTH);
        writeRecord.write(dbr.getLocation(), DBRecord.LOCATION_LENGTH);
        writeRecord.write(dbr.getSpecialties(), DBRecord.SPECIALTIES_LENGTH);
        writeRecord.write(dbr.getStaff(), DBRecord.STAFF_LENGTH);
        writeRecord.write(dbr.getRate(), DBRecord.RATE_LENGTH);
        writeRecord.write(dbr.getCustID(), DBRecord.CUSTID_LENGTH);

        // now that we have everything ready to go, write the file
        //to the database.

        synchronized (database) {
            database.seek(locationInFile);
            database.write(out.toString().getBytes());
        }
        return recNo;

    }

    /**
     * Deletes a record, making the record number and associated storage space
     * available for reuse. This method only runs if the user holds the correct 
     * lock in LockerRoom.
     *
     * @param recNo The number of the record to be modified.
     * @throws RecordNotFoundException Indicates there is no record with that
     * recNo.
     */
    public void delete(int recNo) throws RecordNotFoundException {
        locationInFile = recNoToLocationInFile(recNo);
        recordMapLock.writeLock().lock();

        try {
            synchronized (database) {
                database.seek(locationInFile);
                database.writeShort(0x8000);
            }
            recordMap.remove(recNo);
        } catch (IOException ioe) {
            throw new RecordNotFoundException();
        } finally {
            recordMapLock.writeLock().unlock();
        }

    }
}

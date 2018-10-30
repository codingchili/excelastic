package com.codingchili.Model;

import com.codingchili.logging.ApplicationLogger;
import io.vertx.core.json.JsonObject;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.reactivestreams.*;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * @author Robin Duda
 * <p>
 * Parses xlsx files into json objects.
 */
public class ExcelParser implements FileParser {
    public static final String INDEX = "index";
    private static final String OOXML = ".xlsx";
    private static final String XML97 = ".xls";
    private ApplicationLogger logger = new ApplicationLogger(getClass());
    private String fileName;
    private File file;
    private Workbook workbook;
    private Sheet sheet;
    private int columns;
    private int offset;
    private int rows;

    @Override
    public void setFileData(String localFileName, int offset, String fileName)
            throws ParserException, FileNotFoundException {

        file = new File(localFileName);
        offset -= 1; // convert excel row number to 0-based index.

        if (file.exists()) {
            try {
                this.workbook = getWorkbook(file, fileName);
                this.sheet = workbook.getSheetAt(0);
                this.offset = offset;
                this.fileName = fileName;
            } catch (Exception e) {
                if (e instanceof ParserException) {
                    throw (ParserException) e;
                } else {
                    throw new ParserException(e);
                }
            }
        } else {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
    }

    @Override
    public Set<String> getSupportedFileExtensions() {
        return new HashSet<>(Arrays.asList(OOXML, XML97));
    }

    /**
     * Returns a workbook implementation based on the extension of the filname.
     *
     * @param file     stream representing a workbook
     * @param fileName the filename to determine a specific workbook implementation
     * @return a workbook implentation that supports the given file format
     * @throws ParserException when the file extension is unsupported
     * @throws IOException     when the given data is not a valid workbook
     */
    private Workbook getWorkbook(File file, String fileName) throws ParserException, IOException {
        if (fileName.endsWith(OOXML)) {
            try {
                return new XSSFWorkbook(file);
            } catch (InvalidFormatException e) {
                throw new ParserException(e);
            }
        } else if (fileName.endsWith(XML97)) {
            return new HSSFWorkbook(new FileInputStream(file));
        } else {
            throw new ParserException(
                    String.format("Unrecognized file extension for file %s, expected %s or %s.",
                            fileName, OOXML, XML97));
        }
    }

    @Override
    public void initialize() {
        logger.parsingFile(fileName, offset);

        this.columns = getColumnCount(sheet.getRow(offset));
        this.rows = getItemCount(sheet, offset);

        // parse all rows.
        readRows((json) -> {
            // skip storing the results of the parse.
        }, offset, rows, true);

        logger.parsedFile(rows - 1, fileName);
    }

    @Override
    public void subscribe(Subscriber<? super JsonObject> subscriber) {
        subscriber.onSubscribe(new Subscription() {
            private int index = 0;

            @Override
            public void request(long count) {
                readRows(subscriber::onNext, index, count, false);
                index += count;

                if (index >= rows) {
                    subscriber.onComplete();
                }
            }

            @Override
            public void cancel() {
                // send no more items!
            }
        });
    }

    /**
     * Parses the given portion of the excel file, this saves memory as the whole file
     * does not need to be stored in memory as JSON at once.
     *
     * @param begin    the offset from the starting row.
     * @param count    the number of lines to parse.
     * @param consumer processor of json items.
     */
    public void parseRowRange(int begin, int count, Consumer<JsonObject> consumer) {
        readRows(consumer, begin, begin + count, false);
    }


    @Override
    public void free() {
        try {
            workbook.close();
        } catch (IOException e) {
            logger.onError(e);
        }
    }

    @Override
    public int getNumberOfElements() {
        return rows;
    }

    /**
     * Reads the given range of rows and converts it to json.
     *
     * @param start    the starting element, 0 represents the first row after the row with the column titles.
     * @param count    the number of elements to read - can never read past the max number of rows.
     * @param consumer called with the produced JSON object for each parsed row.
     */
    private void readRows(Consumer<JsonObject> consumer, int start, long count, boolean dryRun) {
        String[] columns = getColumns(sheet.getRow(offset));

        for (int i = start; i < (count + start) && i < rows; i++) {
            consumer.accept(getRow(columns, sheet.getRow(i + offset + 1), dryRun));
        }
    }

    /**
     * retrieves the values of the column titles.
     *
     * @param row that points to the column titles.
     * @return an array of the titles
     */
    private String[] getColumns(Row row) {
        String[] titles = new String[columns];

        for (int i = 0; i < titles.length; i++) {
            titles[i] = row.getCell(i).getStringCellValue();
        }
        return titles;
    }

    /**
     * Returns the number of columns present on the given row.
     *
     * @param row the row to read column count from.
     * @return the number of columns on the given row
     */
    private int getColumnCount(Row row) {
        DataFormatter formatter = new DataFormatter();
        Iterator<Cell> iterator = row.iterator();
        int count = 0;

        while (iterator.hasNext()) {
            Cell cell = iterator.next();
            String value = formatter.formatCellValue(cell);

            if (value.length() > 0) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * counts the number of rows to be imported taking into account the offset
     * of the title columns.
     *
     * @param sheet  the sheet to read items from
     * @param offset the offset of the title columns
     * @return the number of rows minus the column title offset.
     */
    private int getItemCount(Sheet sheet, int offset) {
        int count = 0;
        Row row = sheet.getRow(offset + 1);

        while (row != null) {
            count++;
            row = sheet.getRow(offset + 1 + count);
        }

        return count;
    }

    /**
     * retrieves a row as a json object.
     *
     * @param titles the titles of the row.
     * @param row    the row to read values from.
     * @param dryRun if true no results will be generated and this method returns null.
     * @return a jsonobject that maps titles to the column values.
     */
    private JsonObject getRow(String[] titles, Row row, boolean dryRun) {
        DataFormatter formatter = new DataFormatter();
        JsonObject json = null;
        int index = 0;

        if (!dryRun) {
            json = new JsonObject();
        }

        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            Object value = null;

            if (cell != null) {
                switch (cell.getCellTypeEnum()) {
                    case STRING:
                        value = formatter.formatCellValue(cell);
                        break;
                    case NUMERIC:
                        if (DateUtil.isCellDateFormatted(cell)) {
                            value = cell.getDateCellValue().toInstant().toString();
                        } else {
                            value = cell.getNumericCellValue();
                        }
                        break;
                }
                // avoid indexing null or empty string, fails to index rows
                // when date fields are empty and can lead to mappings being
                // set up incorrectly if leading rows has missing data.
                if (!dryRun && value != null && !(value.toString().length() == 0)) {
                    json.put(titles[index], value);
                }
            }
            index++;
        }
        return json;
    }
}

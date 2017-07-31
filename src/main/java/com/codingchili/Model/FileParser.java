package com.codingchili.Model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;

import static com.codingchili.Controller.Website.MAPPING;

/**
 * @author Robin Duda
 *
 * Parses xlsx files into json objects.
 */
public class FileParser {
    public static final String INDEX = "index";
    public static final String ITEMS = "items";
    private static final String OOXML = ".xlsx";
    private static final String XML97 = ".xls";
    private Logger logger = Logger.getLogger(getClass().getName());
    private JsonArray list = new JsonArray();
    private int columns;
    private int rows;

    /**
     * Parses the contents of an XLSX into JSON.
     *
     * @param bytes  bytes of an XLSX file.
     * @param offset row number containing column titles.
     */
    public FileParser(byte[] bytes, int offset, String fileName) throws ParserException {
        logger.info(String.format("Parsing file '%s' using titles from excel row %d..", fileName, offset));
        offset -= 1; // convert excel row name to index.
        try {
            Workbook workbook = getWorkbook(bytes, fileName);
            Sheet sheet = workbook.getSheetAt(0);

            this.columns = getColumnCount(sheet.getRow(offset));
            this.rows = getItemCount(sheet, offset);

            readRows(sheet, offset);
            logger.info(String.format("Parsed %d rows from file %s.", rows -1, fileName));
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    /**
     * Returns a workbook implementation based on the extension of the filname.
     * @param bytes bytes representing a workbook
     * @param fileName the filename to determine a specific workbook implementation
     * @return a workbook implentation that supports the given file format
     * @throws ParserException when the file extension is unsupported
     * @throws IOException when the given data is not a valid workbook
     */
    private Workbook getWorkbook(byte[] bytes, String fileName) throws ParserException, IOException {
        if (fileName.endsWith(OOXML)) {
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        } else if (fileName.endsWith(XML97)) {
            return new HSSFWorkbook(new ByteArrayInputStream(bytes));
        } else {
            throw new ParserException(
                    String.format("Unrecognized file extension for file %s, expected %s or %s.",
                            fileName, OOXML, XML97));
        }
    }

    public JsonArray getList() {
        return list;
    }

    public int getImportedItems() {
        return rows;
    }

    /**
     * converts the parsed list of items into a json object that includes the index name.
     * may be passed to the ElasticWriter.
     * @param index the name of the index to index to
     * @param mapping the mapping to use for the object
     * @return an importable jsonobject.
     */
    public JsonObject toImportable(String index, String mapping) {
        return new JsonObject().put(ITEMS, list)
                .put(INDEX, index)
                .put(MAPPING, mapping);
    }

    /**
     * reads the rows in the given sheet
     * @param sheet the sheet to read rows from
     * @param columnRow the row to read from
     */
    private void readRows(Sheet sheet, int columnRow) {
        String[] columns = getColumns(sheet.getRow(columnRow));

        for (int i = 0; i < rows; i++) {
            list.add(getRow(columns, sheet.getRow(i + columnRow + 1)));
        }
    }

    /**
     * retrieves the values of the column titles.
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
     * @param sheet the sheet to read items from
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
     * @param titles the titles of the row.
     * @param row the row to read values from.
     * @return a jsonobject that maps titles to the column values.
     */
    private JsonObject getRow(String[] titles, Row row) {
        DataFormatter formatter = new DataFormatter();
        JsonObject json = new JsonObject();
        int index = 0;

        for (Cell cell : row) {
            switch (cell.getCellType()) {
                case Cell.CELL_TYPE_STRING:
                    json.put(titles[index], formatter.formatCellValue(cell));
                    break;
                case Cell.CELL_TYPE_NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        json.put(titles[index], cell.getDateCellValue().toInstant().toString());
                    } else {
                        json.put(titles[index], cell.getNumericCellValue());
                    }
                    break;
            }
            index++;
        }
        return json;
    }
}

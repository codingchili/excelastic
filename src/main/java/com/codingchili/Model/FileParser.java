package com.codingchili.Model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Logger;

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
    private int items;

    /**
     * Parses the contents of an XLSX into JSON.
     *
     * @param bytes  bytes of an XLSX file.
     * @param offset row number containing column titles.
     */
    public FileParser(byte[] bytes, int offset, String fileName) throws ParserException {
        offset -= 1; // convert excel row name to index.
        try {
            Workbook workbook = getWorkbook(bytes, fileName);
            Sheet sheet = workbook.getSheetAt(0);

            this.columns = getColumnCount(sheet.getRow(offset));
            this.items = getItemCount(sheet, offset);

            readRows(sheet, offset);
            logger.info(String.format("Imported %d rows from file %s.", items, fileName));
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

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
        return items;
    }

    public JsonObject toImportable(String index) {
        return new JsonObject().put(ITEMS, list).put(INDEX, index);
    }

    private void readRows(Sheet sheet, int columnRow) {
        String[] columns = getColumns(sheet.getRow(columnRow));

        for (int i = 0; i < items; i++) {
            list.add(getRow(columns, sheet.getRow(i + columnRow + 1)));
        }
    }

    private String[] getColumns(Row row) {
        String[] titles = new String[columns];

        for (int i = 0; i < titles.length; i++) {
            titles[i] = row.getCell(i).getStringCellValue();
        }
        return titles;
    }

    private int getColumnCount(Row row) {
        Iterator<Cell> iterator = row.iterator();
        int count = 0;

        while (iterator.hasNext()) {
            Cell cell = iterator.next();

            if (cell.getStringCellValue() != null) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private int getItemCount(Sheet sheet, int offset) {
        int count = 0;
        Row row = sheet.getRow(offset + 1);

        while (row != null) {
            count++;
            row = sheet.getRow(offset + 1 + count);
        }

        return count;
    }

    private JsonObject getRow(String[] titles, Row row) {
        JsonObject json = new JsonObject();
        int index = 0;

        for (Cell cell : row) {
            switch (cell.getCellType()) {
                case Cell.CELL_TYPE_NUMERIC:
                    json.put(titles[index], cell.getNumericCellValue());
                    break;
                case Cell.CELL_TYPE_STRING:
                    json.put(titles[index], cell.getStringCellValue().split("/")[0]);
                    break;
            }
            index++;
        }
        return json;
    }
}

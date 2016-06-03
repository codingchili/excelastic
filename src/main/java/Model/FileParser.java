package Model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.util.Iterator;

/**
 * @author Robin Duda
 */
public class FileParser {
    private static final int ROW_OFFSET = Configuration.ROW_OFFSET;
    private JsonArray list = new JsonArray();
    private int columns;
    private int rows;

    /**
     * Parses the contents of an XLSX into JSON.
     *
     * @param bytes contains an XLSX file.
     */
    public FileParser(byte[] bytes) throws ParserException {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
            XSSFSheet sheet = workbook.getSheetAt(0);

            this.columns = getColumnCount(sheet.getRow(ROW_OFFSET - 1));
            this.rows = getRowCount(sheet);

            readRows(sheet, ROW_OFFSET);
        } catch (Exception e) {
            throw new ParserException();
        }
    }

    public JsonArray toJsonArray() {
        return list;
    }

    private void readRows(XSSFSheet sheet, int offset) {
        String[] columns = getColumns(sheet.getRow(offset - 1));

        for (int i = 1; i < rows; i++) {
            list.add(getRow(columns, sheet.getRow(offset + i - 1)));
        }
    }

    private String[] getColumns(XSSFRow row) {
        String[] titles = new String[columns];

        for (int i = 0; i < titles.length; i++) {
            titles[i] = row.getCell(i).getStringCellValue();
        }

        return titles;
    }

    private int getColumnCount(XSSFRow row) {
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

    private int getRowCount(XSSFSheet sheet) {
        Iterator<Row> iterator = sheet.iterator();
        int count = 0;

        while (iterator.hasNext()) {
            iterator.next();
            count++;
        }

        return count;
    }

    private JsonObject getRow(String[] titles, XSSFRow row) {
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

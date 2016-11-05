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
    private JsonArray list = new JsonArray();
    private int columns;
    private int items;

    /**
     * Parses the contents of an XLSX into JSON.
     *
     * @param bytes  bytes of an XLSX file.
     * @param offset row number containing column titles.
     */
    public FileParser(byte[] bytes, int offset) throws ParserException {
        offset -= 1; // convert excel row name to index.
        try {
            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
            XSSFSheet sheet = workbook.getSheetAt(0);

            this.columns = getColumnCount(sheet.getRow(offset));
            this.items = getItemCount(sheet, offset);

            readRows(sheet, offset);
        } catch (Exception e) {
            throw new ParserException(e);
        }
    }

    public JsonArray toJsonArray() {
        return list;
    }

    private void readRows(XSSFSheet sheet, int columnRow) {
        String[] columns = getColumns(sheet.getRow(columnRow));

        for (int i = 0; i < items; i++) {
            list.add(getRow(columns, sheet.getRow(i + columnRow + 1)));
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

    private int getItemCount(XSSFSheet sheet, int offset) {
        int count = 0;
        Row row = sheet.getRow(offset + 1);

        while (row != null) {
            count++;
            row = sheet.getRow(offset + 1 + count);
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
